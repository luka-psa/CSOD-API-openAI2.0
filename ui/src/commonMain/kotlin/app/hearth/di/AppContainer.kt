package app.hearth.di

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
    db: HearthDatabase,
    val googleAuth: GoogleAuth = NoGoogleAuth,
    /** The web preview starts with examples, so a first visit shows how it works. */
    examplesOnFirstRun: Boolean = false,
    /** False in the browser: other sites are read through the relay (CORS). */
    directPages: Boolean = true,
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
    val tools = HearthTools(shopping, backlog, board, stock, week, settings, meeting, backups, drive, calendar, pages)
    val orchestrator = Orchestrator(tools.registry)

    /** The family's ChatGPT or Claude reaches the same tools here, through the relay. */
    val mcp = McpServer(tools.registry) { context(ToolSource.MCP) }
    val relay = RelayClient(mcp, appScope)

    private companion object {
        const val CHECK_EVERY_MS = 60 * 60_000L
        const val BACKUP_EVERY_MS = 20 * 60 * 60_000L
        const val CALENDAR_DELAY_MS = 20_000L
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
        // Connected only while it's switched on; the client reconnects by itself.
        appScope.launch { settings.connection.collect { c -> relay.connect(if (c.enabled) c.tabletUrl else null) } }
        if (googleAuth.available) keepGoogleUpToDate()
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
