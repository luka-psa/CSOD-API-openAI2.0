package app.hearth.di

import app.hearth.ai.interpret.Interpreting
import app.hearth.ai.interpret.InterpreterRuntime
import app.hearth.ai.interpret.NoInterpreterRuntime
import app.hearth.ai.sense.NoSenseRuntime
import app.hearth.ai.sense.SenseRuntime
import app.hearth.ai.sense.SmartListening

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import app.hearth.core.tools.system.AppSettings
import app.hearth.ai.Orchestrator
import app.hearth.core.data.HearthDatabase
import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.tools.food.shopIdFor
import app.hearth.core.model.Lang
import app.hearth.core.tools.HearthTools
import app.hearth.mcp.McpServer
import app.hearth.mcp.RelayClient
import app.hearth.mcp.RelaySync
import app.hearth.core.data.sync.SyncRepository
import app.hearth.core.tools.ToolException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.hearth.mcp.DEFAULT_RELAY_URL
import app.hearth.core.data.backup.BackupRepository
import app.hearth.core.model.nowMillis
import app.hearth.core.tools.system.GoogleSettings
import app.hearth.core.tools.system.NoArgs
import app.hearth.core.tools.system.LoadExamplesTool
import app.hearth.google.DriveBackupStore
import app.hearth.google.GoogleAuth
import app.hearth.google.GoogleCalendarApi
import app.hearth.google.NoGoogleAuth
import io.ktor.client.HttpClient
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Hand-wired dependencies (no DI framework, to stay light on low-spec tablets).
 * Each platform opens the database its own way and passes it in.
 *
 * The tablet's own chat runs locally (rules + keyword sorter). The family's AI (ChatGPT or Claude,
 * on their own subscription) reaches the same tools over MCP.
 */
class AppContainer(
    private val db: HearthDatabase,
    val googleAuth: GoogleAuth = NoGoogleAuth,
    /** The web preview starts with examples, so a first visit shows how it works. */
    examplesOnFirstRun: Boolean = false,
    /** False in the browser: other sites are read through the relay (CORS). */
    directPages: Boolean = true,
    /** Where this device keeps and runs the sentence model for smarter listening (none: the rules alone). */
    senseRuntime: SenseRuntime = NoSenseRuntime,
    /** Where this device keeps and runs the interpreter that reads a whole weekoverleg (none: not on this device). */
    interpreterRuntime: InterpreterRuntime = NoInterpreterRuntime,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The app's language: Dutch (Flemish) unless the family switched to English in Instellingen. */
    private val _language = MutableStateFlow(Lang.NL)
    val language: StateFlow<Lang> = _language.asStateFlow()
    val lang: Lang get() = _language.value

    val shopping = ShoppingRepository(db)
    val backlog = BacklogRepository(db)
    val board = BoardRepository(db)
    val stock = StockRepository(db)
    val week = WeekRepository(db)
    val settings = SettingsRepository(db, DEFAULT_RELAY_URL)
    val backups = BackupRepository(db)

    /** The family's own Google Drive and Calendar: off until someone switches them on. */
    private val googleHttp = HttpClient()
    val drive = DriveBackupStore(googleHttp, googleAuth)
    val calendar = GoogleCalendarApi(googleHttp, googleAuth)
    val meeting = MeetingRepository(db)
    private val pages = WebPages(HttpClient(), directPages) { settings.connection.first().relayUrl ?: DEFAULT_RELAY_URL.ifBlank { null } }
    /** The household on more devices: merged here, met on the relay as a sealed box. */
    val sync = SyncRepository(db, backups)
    private val syncCloud = RelaySync({ settings.connection.first().relayUrl })
    val tools = HearthTools(shopping, backlog, board, stock, week, settings, meeting, backups, drive, calendar, pages, sync, syncCloud)
    val orchestrator = Orchestrator(tools.registry)
    /** Smarter listening: the sentence model next to the rules, once switched on in Instellingen. */
    val listening = SmartListening(senseRuntime)
    /** The interpreter: reads the conversation as a whole and names what was decided, once switched on. */
    val interpreting = Interpreting(interpreterRuntime)

    /** The family's ChatGPT or Claude reaches the same tools here, through the relay. */
    val mcp = McpServer(tools.registry) { context(ToolSource.MCP) }
    val relay = RelayClient(mcp, appScope)

    private companion object {
        const val CHECK_EVERY_MS = 60 * 60_000L
        const val BACKUP_EVERY_MS = 20 * 60 * 60_000L
        const val CALENDAR_DELAY_MS = 20_000L
        /** A little after a change, so a burst of taps goes out as one. */
        const val SYNC_AFTER_CHANGE_MS = 15_000L
        /** A light check for what the others did, while Hearth is open. */
        const val SYNC_EVERY_MS = 5 * 60_000L
        val SYNCED_TABLES = arrayOf(
            "shop", "shopping_item", "backlog_item", "member", "board_item", "storage_spot", "stock_item", "meal", "dish", "huddle",
            "setting", "recipe_photo", "reflection", "transcript_line", "routine_log",
        )
    }

    fun context(source: ToolSource) = ToolContext(source = source, lang = lang)

    /**
     * Done once the defaults (and, on a first web visit, the examples) are in. The web waits for it before
     * showing the screen: its database driver can stall when a screen query is cancelled mid-write.
     */
    val ready = appScope.async {
        settings.currentValue(AppSettings.LANGUAGE)?.let { _language.value = Lang.fromTag(it) }
        shopping.ensureDefaultShops(lang)
        stock.ensureDefaults(lang) { shopping.shopIdFor(it) }
        if (examplesOnFirstRun && settings.currentValue(LoadExamplesTool.LOADED) == null && backlog.openItems().isEmpty()) {
            runCatching { tools.registry.run(tools.loadExamples, NoArgs(), context(ToolSource.UI)) }
        }
    }

    /** The tablet offers examples in Koppelingen; the web preview already starts with them. */
    val offersExamples = !examplesOnFirstRun

    init {
        appScope.launch { settings.value(AppSettings.LANGUAGE).collect { it?.let { tag -> _language.value = Lang.fromTag(tag) } } }
        // On: download once and load (in the background, the app goes on); off again: the space back.
        appScope.launch {
            settings.value(AppSettings.SMART_LISTENING).collectLatest { on ->
                when {
                    on == "true" -> listening.start()
                    listening.state.value != SmartListening.State.Off -> listening.stop()
                }
            }
        }
        appScope.launch {
            settings.value(AppSettings.INTERPRETER).collectLatest { on ->
                when {
                    on == "true" -> interpreting.start()
                    interpreting.state.value != Interpreting.State.Off -> interpreting.stop()
                }
            }
        }
        // Connected only while it's switched on; the client reconnects by itself.
        appScope.launch { settings.connection.collect { c -> relay.connect(if (c.enabled) c.tabletUrl else null) } }
        if (googleAuth.available) keepGoogleUpToDate()
        keepInSync()
    }

    private val syncLock = Mutex()
    private val _syncBusy = MutableStateFlow(false)
    /** True while this device is pulling, merging or pushing. */
    val syncBusy: StateFlow<Boolean> = _syncBusy.asStateFlow()
    private val _syncProblem = MutableStateFlow<String?>(null)
    /** Why the last sync didn't go through (offline, an older Hearth); null once it does. */
    val syncProblem: StateFlow<String?> = _syncProblem.asStateFlow()

    /** One sync at a time, whoever asks (the button, a change, the clock). */
    suspend fun <T> syncing(block: suspend () -> T): T = syncLock.withLock {
        _syncBusy.value = true
        try {
            block().also { _syncProblem.value = null }
        } catch (e: ToolException) {
            _syncProblem.value = e.userMessage
            throw e
        } finally {
            _syncBusy.value = false
        }
    }

    /** Syncs now if this device is in a household; quietly, a failure waits for the next round. */
    fun syncInBackground(onlyIfChanged: Boolean = false) {
        appScope.launch { syncQuietly(onlyIfChanged) }
    }

    private suspend fun syncQuietly(onlyIfChanged: Boolean) {
        val engine = tools.syncEngine ?: return
        val keys = engine.keys() ?: return
        if (onlyIfChanged && !engine.changed(keys)) return
        runCatching { syncing { tools.registry.run(tools.syncNow!!, NoArgs(), context(ToolSource.UI)) } }
    }

    /**
     * Like git, on its own: a little after something changes here, and every few minutes for what the others did,
     * while Hearth is open (the tablet always is; a phone catches up when it's opened). Only when it's switched on.
     */
    @OptIn(FlowPreview::class)
    private fun keepInSync() {
        appScope.launch {
            ready.await()
            launch {
                db.invalidationTracker.createFlow(*SYNCED_TABLES, emitInitialState = false)
                    .debounce(SYNC_AFTER_CHANGE_MS)
                    .collect { syncQuietly(onlyIfChanged = true) }
            }
            while (true) {
                syncQuietly(onlyIfChanged = false)
                delay(SYNC_EVERY_MS)
            }
        }
    }

    /**
     * Quiet background care, only for what is switched on: one backup a day, and the calendar follows
     * the plan a little after it changes. Failures (offline, signed out) just wait for the next round.
     */
    @OptIn(FlowPreview::class)
    private fun keepGoogleUpToDate() {
        val ctx = context(ToolSource.UI)
        appScope.launch {
            while (true) {
                val on = settings.currentValue(GoogleSettings.BACKUP_ON) == "true"
                val last = settings.currentValue(GoogleSettings.LAST_BACKUP)?.toLongOrNull() ?: 0L
                if (on && nowMillis() - last > BACKUP_EVERY_MS) runCatching { tools.registry.run(tools.backupNow!!, NoArgs(), ctx) }
                delay(CHECK_EVERY_MS)
            }
        }
        appScope.launch {
            combine(settings.value(GoogleSettings.CALENDAR_ON), backlog.open) { on, items -> if (on == "true") items else null }
                .filterNotNull()
                .debounce(CALENDAR_DELAY_MS)
                .collectLatest { runCatching { tools.registry.run(tools.syncCalendar, NoArgs(), ctx) } }
        }
    }
}
