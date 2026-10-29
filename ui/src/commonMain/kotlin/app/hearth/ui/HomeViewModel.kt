package app.hearth.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import app.hearth.ai.route.Spoken
import app.hearth.core.model.Schedule
import app.hearth.core.tools.backlog.SetRoutineScheduleArgs
import app.hearth.core.tools.week.ImportRecipeArgs
import app.hearth.core.tools.backlog.AddRoutineArgs
import app.hearth.core.tools.system.SetLanguageArgs
import app.hearth.core.tools.backlog.SetAnchorArgs
import app.hearth.core.model.Anchor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hearth.ai.ChatReply
import app.hearth.ai.ChatTurn
import app.hearth.ai.PendingAction
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.tickedOn
import app.hearth.core.model.Lang
import app.hearth.core.model.Shop
import app.hearth.core.model.ShoppingItem
import app.hearth.core.model.nowMillis
import app.hearth.ai.ConversationExport
import app.hearth.core.model.today
import app.hearth.core.model.weekdayName
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolSource
import app.hearth.ai.route.TaskWords
import app.hearth.core.model.BoardItem
import app.hearth.core.model.GoalScope
import app.hearth.core.model.Member
import app.hearth.core.model.Section
import app.hearth.core.tools.board.AddGoalArgs
import app.hearth.core.tools.board.AddHouseRuleArgs
import app.hearth.core.tools.board.AddMemberArgs
import app.hearth.core.tools.board.BoardRefArgs
import app.hearth.core.tools.board.FrictionOutcome
import app.hearth.core.tools.board.LogFrictionArgs
import app.hearth.core.tools.board.RemoveMemberArgs
import app.hearth.core.tools.board.ResolveFrictionArgs
import app.hearth.core.tools.backlog.MoveStepArgs
import app.hearth.core.tools.board.MarkFrictionArgs
import app.hearth.core.tools.board.SetGoalStepArgs
import app.hearth.core.tools.backlog.AddProjectStepArgs
import app.hearth.core.tools.backlog.AddToBacklogArgs
import app.hearth.core.tools.backlog.BacklogRefArgs
import app.hearth.core.tools.backlog.MarkUrgentArgs
import app.hearth.core.tools.backlog.MoveBacklogArgs
import app.hearth.core.tools.backlog.SetRoutineIntervalArgs
import app.hearth.core.tools.backlog.SetPairingArgs
import app.hearth.core.tools.backlog.WaitForReplyArgs
import app.hearth.core.tools.backlog.PlanBacklogArgs
import app.hearth.core.tools.backlog.When
import app.hearth.core.data.settings.Connection
import app.hearth.core.tools.system.SaveConnectionArgs
import app.hearth.mcp.LinkedApp
import app.hearth.core.tools.system.BackupFile
import app.hearth.ai.Orchestrator
import app.hearth.ai.route.Place
import app.hearth.ai.route.KeywordRouter
import app.hearth.core.tools.week.FinishArgs
import app.hearth.core.model.Topic
import app.hearth.core.model.FrictionDirection
import app.hearth.core.model.topic
import app.hearth.core.tools.board.AddIdeaArgs
import app.hearth.core.model.TranscriptLine
import app.hearth.core.model.Ease
import app.hearth.core.model.BoardKind
import app.hearth.core.model.HabitReview
import app.hearth.core.model.isHabit
import app.hearth.core.model.HabitTip
import app.hearth.core.model.TipAction
import app.hearth.core.model.celebrateWords
import app.hearth.core.model.frictionsAbout
import app.hearth.core.model.isAbout
import app.hearth.core.tools.week.EditHuddleArgs
import app.hearth.core.model.ruleReview
import app.hearth.core.tools.board.SetRuleHelpArgs
import app.hearth.core.tools.board.EditBoardItemArgs
import app.hearth.core.tools.board.AchieveGoalArgs
import app.hearth.core.model.habitReview
import app.hearth.core.model.easeNow
import app.hearth.core.tools.backlog.RateHabitArgs
import app.hearth.core.tools.backlog.SmallStepArgs
import app.hearth.core.model.WeekRating
import app.hearth.core.model.RuleCheck
import app.hearth.core.model.dayOf
import app.hearth.core.model.isRoutine
import app.hearth.core.tools.week.CheckHouseRuleArgs
import app.hearth.core.tools.week.SetWeekMetersTool
import app.hearth.core.tools.week.RateWeekArgs
import app.hearth.core.tools.week.WeekMetersArgs
import app.hearth.core.model.Win
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.week.AddWinArgs
import app.hearth.core.tools.week.ClearTranscriptArgs
import app.hearth.core.tools.week.MarkSortedArgs
import app.hearth.core.tools.week.FixLineArgs
import app.hearth.core.tools.week.ForgetWordArgs
import app.hearth.core.tools.week.LearnWordsArgs
import app.hearth.core.model.WordFix
import app.hearth.core.model.WordFixes
import app.hearth.ai.meeting.HeardLine
import app.hearth.ai.meeting.Household
import app.hearth.ai.meeting.LineIntake
import app.hearth.ai.meeting.Matcher
import app.hearth.ai.meeting.MeetingIntake
import app.hearth.ai.meeting.MeetingProcessor
import app.hearth.ai.meeting.Processed
import kotlinx.coroutines.Job
import app.hearth.core.tools.week.RemoveWinArgs
import app.hearth.core.tools.week.TranscriptLineArgs
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.tools.system.JoinSyncArgs
import app.hearth.core.tools.system.JoinCodeResult
import app.hearth.core.tools.system.GoogleSettings
import app.hearth.core.tools.system.NoArgs
import app.hearth.core.tools.system.RestoreBackupArgs
import app.hearth.core.tools.system.SetGoogleSyncArgs
import app.hearth.google.GoogleScopes
import app.hearth.mcp.PairingCode
import app.hearth.mcp.McpPending
import app.hearth.mcp.RelayStatus
import app.hearth.core.model.Huddle
import app.hearth.core.model.Meal
import app.hearth.core.model.plusDays
import app.hearth.core.model.Recipe
import app.hearth.core.tools.food.MoveStockItemArgs
import app.hearth.core.tools.food.RemoveStorageSpotArgs
import app.hearth.core.tools.week.AgendaArgs
import app.hearth.core.tools.week.RecipeRefArgs
import app.hearth.core.tools.week.SaveRecipeArgs
import app.hearth.core.tools.week.SetRecipePhotoArgs
import app.hearth.core.tools.week.PlanMealArgs
import app.hearth.core.tools.week.RemoveMealArgs
import app.hearth.core.tools.week.WeekAgenda
import app.hearth.core.model.StockItem
import app.hearth.core.model.StockState
import app.hearth.core.model.StorageSpot
import app.hearth.core.tools.food.AddShoppingItemArgs
import app.hearth.core.tools.food.AddStockItemArgs
import app.hearth.core.tools.food.AddStorageSpotArgs
import app.hearth.core.tools.food.ClearBoughtArgs
import app.hearth.core.tools.food.SetStockStateArgs
import app.hearth.core.tools.food.StockRefArgs
import app.hearth.core.tools.food.MarkBoughtArgs
import app.hearth.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import app.hearth.ai.interpret.Interpreting
import app.hearth.ai.sense.Reading
import app.hearth.ai.sense.SmartListening
import app.hearth.core.tools.system.InterpreterArgs
import app.hearth.core.tools.system.SmartListeningArgs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.ExperimentalTime

data class ChatMessage(val fromUser: Boolean, val text: String, val pending: PendingAction? = null)

data class ShopSection(val shop: Shop, val items: List<ShoppingItem>)

class HomeViewModel(private val app: AppContainer) : ViewModel() {

    /** Dutch (Flemish) by default; English when switched in Instellingen. */
    val lang: StateFlow<Lang> = app.language

    fun setLanguage(language: Lang) = tool { app.tools.registry.run(app.tools.setLanguage, SetLanguageArgs(language), it) }

    // ---- clock ----

    /** Ticks every few minutes so "today" and the greeting stay right on an always-on tablet. */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(nowMillis())
            delay(5 * 60_000L)
        }
    }

    val today: StateFlow<LocalDate> = ticker.map { today() }.stateIn(viewModelScope, SharingStarted.Eagerly, today())

    // ---- data ----

    val backlog: StateFlow<List<BacklogItem>> =
        app.backlog.open.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The colour of each open item: habits as the family rated them, chores from their history, tasks from postponing. */
    @OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
    val ease: StateFlow<Map<String, Ease>> = today
        .flatMapLatest { d ->
            combine(app.backlog.open, app.backlog.routineLogs(d.plusDays(-120).toString())) { items, logs ->
                items.mapNotNull { item -> item.easeNow(logs[item.id].orEmpty(), dayOf(item.createdAt), d)?.let { item.id to it } }.toMap()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The coming seven days of meals, from today. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val meals: StateFlow<List<Meal>> = today
        .flatMapLatest { d -> app.week.meals(d.toString(), d.plusDays(7).toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dishes: StateFlow<List<Meal>> =
        app.week.dishes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recipes: StateFlow<List<Recipe>> =
        app.week.recipes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Bumped when a photo changes, so cards reload it. */
    private val _photoVersion = MutableStateFlow(0)
    val photoVersion: StateFlow<Int> = _photoVersion.asStateFlow()

    /** A recipe's photo, read straight from the repository (reads may skip the tools). */
    suspend fun photo(key: String): ByteArray? = app.week.photo(key)

    val lastHuddle: StateFlow<Huddle?> =
        app.week.lastHuddle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _agenda = MutableStateFlow<WeekAgenda?>(null)
    /** A snapshot for the look back (what got done since the last meeting). */
    val agenda: StateFlow<WeekAgenda?> = _agenda.asStateFlow()

    // ---- connection to the family's AI (MCP) ----

    val connection: StateFlow<Connection> =
        app.settings.connection.stateIn(viewModelScope, SharingStarted.Eagerly, Connection())
    val relayStatus: StateFlow<RelayStatus> = app.relay.status
    val mcpPending: StateFlow<List<McpPending>> = app.mcp.pending
    val mcpActivity: StateFlow<List<String>> = app.mcp.activity

    val linkedApps: StateFlow<List<LinkedApp>> = app.relay.apps
    val pairingCode: StateFlow<PairingCode?> = app.relay.pairing

    fun saveConnection(enabled: Boolean) = tool {
        app.tools.registry.run(app.tools.saveConnection, SaveConnectionArgs(enabled), it)
    }

    val offersExamples = app.offersExamples

    /** One or two examples in every section, to see how Hearth works. */
    fun loadExamples() = tool { say(app.tools.registry.run(app.tools.loadExamples, NoArgs(), it).summary) }

    /** The code to type on the Hearth page; switches the connection on first when it is off. */
    fun showPairingCode() {
        viewModelScope.launch {
            if (!connection.value.enabled) {
                app.tools.registry.run(app.tools.saveConnection, SaveConnectionArgs(true), ctx(ToolSource.UI))
                withTimeoutOrNull(20_000) { app.relay.status.first { it == RelayStatus.ONLINE } } ?: return@launch
            }
            app.relay.requestPairingCode()
        }
    }
    fun hidePairingCode() = app.relay.hidePairingCode()

    /** Unlinks an AI app (or all, with null). The screen asked first. */
    fun unlinkApp(id: String?) = app.relay.unlink(id)

    // ---- Samen op meer toestellen: the household's sync (opt-in) ----

    /** On, when this device is part of a household; when it last synced; and whether a relay exists at all. */
    val sync: StateFlow<SyncState> = combine(
        app.settings.value(SettingsRepository.SYNC_KEYS),
        app.settings.value(SettingsRepository.SYNC_LAST),
        app.settings.connection,
        app.syncBusy,
        app.syncProblem,
    ) { keys, last, connection, busy, problem ->
        SyncState(keys != null, last?.toLongOrNull(), connection.relayUrl != null, busy, problem)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncState())

    private val _joinCode = MutableStateFlow<JoinCodeResult?>(null)
    /** The code another device types, while it's shown. */
    val joinCode: StateFlow<JoinCodeResult?> = _joinCode.asStateFlow()

    fun startSync() = syncAction { _joinCode.value = app.tools.registry.run(app.tools.startSync!!, NoArgs(), it).also { r -> say(r.summary) } }
    fun shareSync() = syncAction { _joinCode.value = app.tools.registry.run(app.tools.shareSync!!, NoArgs(), it) }
    fun hideJoinCode() { _joinCode.value = null }

    /** The dialog asked which way first (take the household over, or add this device's things). */
    fun joinSync(code: String, keepThisDevice: Boolean) = syncAction {
        say(app.tools.registry.run(app.tools.joinSync!!, JoinSyncArgs(code, keepThisDevice), it, confirmed = true).summary)
    }

    fun syncNow() = syncAction { say(app.tools.registry.run(app.tools.syncNow!!, NoArgs(), it).summary) }

    fun stopSync() = syncAction {
        _joinCode.value = null
        say(app.tools.registry.run(app.tools.stopSync!!, NoArgs(), it).summary)
    }

    /** Back on screen (the tablet woke, the phone was opened): catch up with the others. */
    fun syncSoon() = app.syncInBackground()

    private fun syncAction(block: suspend (ToolContext) -> Unit) {
        viewModelScope.launch {
            try {
                app.syncing { block(ctx(ToolSource.UI)) }
            } catch (e: ToolException) {
                say(e.userMessage)
            }
        }
    }

    // ---- Google Drive backup and Google Calendar (opt-in) ----

    val googleAvailable: Boolean get() = app.googleAuth.available

    val google: StateFlow<GoogleState> = combine(
        app.settings.value(GoogleSettings.BACKUP_ON),
        app.settings.value(GoogleSettings.LAST_BACKUP),
        app.settings.value(GoogleSettings.CALENDAR_ON),
        app.settings.value(GoogleSettings.LAST_SYNC),
    ) { backup, lastBackup, calendar, lastSync ->
        GoogleState(backup == "true", lastBackup?.toLongOrNull(), calendar == "true", lastSync?.toLongOrNull())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoogleState())

    private val _googleBusy = MutableStateFlow(false)
    val googleBusy: StateFlow<Boolean> = _googleBusy.asStateFlow()

    private val _backupList = MutableStateFlow<List<BackupFile>?>(null)
    /** Shown when the family picks a backup to put back; null when the picker is closed. */
    val backupList: StateFlow<List<BackupFile>?> = _backupList.asStateFlow()

    /** Sign in for Drive (only the hidden app folder), switch the daily backup on and make the first one. */
    fun connectBackup() = withGoogle {
        if (app.googleAuth.token(GoogleScopes.DRIVE_APPDATA, interactive = true) == null) return@withGoogle say(signInFailed())
        app.tools.registry.run(app.tools.setGoogleSync, SetGoogleSyncArgs(backup = true), it)
        say(app.tools.registry.run(app.tools.backupNow!!, NoArgs(), it).summary)
    }

    fun backupNow() = withGoogle {
        if (app.googleAuth.token(GoogleScopes.DRIVE_APPDATA, interactive = true) == null) return@withGoogle say(signInFailed())
        say(app.tools.registry.run(app.tools.backupNow!!, NoArgs(), it).summary)
    }

    fun openBackups() = withGoogle {
        if (app.googleAuth.token(GoogleScopes.DRIVE_APPDATA, interactive = true) == null) return@withGoogle say(signInFailed())
        _backupList.value = app.tools.registry.run(app.tools.listBackups, NoArgs(), it).backups
    }

    fun closeBackups() {
        _backupList.value = null
    }

    /** The screen asked "replace everything?" first. */
    fun restoreBackup(id: String) = withGoogle {
        _backupList.value = null
        say(app.tools.registry.run(app.tools.restoreBackup!!, RestoreBackupArgs(id), it, confirmed = true).summary)
    }

    /** Sign in for Calendar (only calendars Hearth makes), switch it on and push the plan. */
    fun connectCalendar() = withGoogle {
        if (app.googleAuth.token(GoogleScopes.CALENDAR_APP_CREATED, interactive = true) == null) return@withGoogle say(signInFailed())
        app.tools.registry.run(app.tools.setGoogleSync, SetGoogleSyncArgs(calendar = true), it)
        say(app.tools.registry.run(app.tools.syncCalendar, NoArgs(), it).summary)
    }

    fun syncCalendar() = withGoogle {
        if (app.googleAuth.token(GoogleScopes.CALENDAR_APP_CREATED, interactive = true) == null) return@withGoogle say(signInFailed())
        say(app.tools.registry.run(app.tools.syncCalendar, NoArgs(), it).summary)
    }

    fun setGoogleSync(backup: Boolean? = null, calendar: Boolean? = null) = withGoogle {
        say(app.tools.registry.run(app.tools.setGoogleSync, SetGoogleSyncArgs(backup, calendar), it).summary)
    }

    private fun signInFailed() = ctx(ToolSource.UI).t("Aanmelden bij Google lukte niet. Probeer het gerust nog eens.", "Signing in to Google didn’t work. Feel free to try again.")

    private fun say(text: String) = _messages.update { it + ChatMessage(fromUser = false, text = text) }

    private fun withGoogle(block: suspend (ToolContext) -> Unit) {
        if (_googleBusy.value) return
        viewModelScope.launch {
            _googleBusy.value = true
            try {
                block(ctx(ToolSource.UI))
            } catch (e: ToolException) {
                say(e.userMessage)
            } finally {
                _googleBusy.value = false
            }
        }
    }

    /** Ja / Nee on the tablet for something the family's AI asked to do. */
    fun answerMcp(pending: McpPending, yes: Boolean) {
        viewModelScope.launch {
            if (yes) {
                app.mcp.confirm(pending.id)?.let { outcome -> _messages.update { it + ChatMessage(fromUser = false, text = outcome.summary) } }
            } else {
                app.mcp.cancel(pending.id)
            }
        }
    }

    val stockSpots: StateFlow<List<StorageSpot>> =
        app.stock.spots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stockItems: StateFlow<List<StockItem>> =
        app.stock.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val board: StateFlow<List<BoardItem>> =
        app.board.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val members: StateFlow<List<Member>> =
        app.board.memberList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Project steps, finished ones too, for progress. */
    val steps: StateFlow<List<BacklogItem>> =
        app.backlog.steps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val doneToday: StateFlow<Int> = ticker
        .map { startOfToday() }
        .flatMapLatest { app.backlog.doneSince(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Bought items stay visible (struck through) for a while, so the list never needs clearing. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val shopping: StateFlow<List<ShopSection>> = ticker
        .map { nowMillis() - KEEP_BOUGHT_MILLIS }
        .flatMapLatest { since -> combine(app.shopping.shops, app.shopping.visibleItems(since), ::group) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- chat ----

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // A short "connected" note in the chat the moment an AI app gets linked.
    init {
        viewModelScope.launch {
            app.relay.linked.collect { name ->
                _messages.update { it + ChatMessage(fromUser = false, text = ctx(ToolSource.UI).t("$name is nu gekoppeld met Hearth.", "$name is now linked to Hearth.")) }
            }
        }
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val pending: PendingAction? get() = _messages.value.lastOrNull()?.pending

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (pending != null) {
            when (trimmed.lowercase().trimEnd('.', '!')) {
                in YES -> return answer(trimmed, confirm = true)
                in NO -> return answer(trimmed, confirm = false)
            }
        }
        val history = _messages.value.map { ChatTurn(it.fromUser, it.text) }
        clearPending()
        _messages.update { it + ChatMessage(fromUser = true, text = trimmed) }
        reply { app.orchestrator.handle(trimmed, ctx(ToolSource.CHAT), history) }
    }

    /** The Ja / Nee buttons under a confirmation question. */
    fun answer(label: String, confirm: Boolean) {
        val waiting = pending ?: return
        clearPending()
        _messages.update { it + ChatMessage(fromUser = true, text = label) }
        if (confirm) {
            reply { app.orchestrator.confirm(waiting, ctx(ToolSource.CHAT)) }
        } else {
            val c = ctx(ToolSource.CHAT).copy(lang = waiting.lang)
            _messages.update { it + ChatMessage(fromUser = false, text = c.t("Oké, niets veranderd.", "Okay, nothing changed.")) }
        }
    }

    private fun reply(block: suspend () -> ChatReply) {
        viewModelScope.launch {
            _busy.value = true
            val reply = block()
            _messages.update { it + ChatMessage(fromUser = false, text = reply.text, pending = reply.pending) }
            _busy.value = false
        }
    }

    private fun clearPending() {
        _messages.update { list -> list.map { if (it.pending != null) it.copy(pending = null) else it } }
    }

    // ---- backlog actions (UI path: the same tools the chat and MCP use) ----

    fun plan(item: BacklogItem, whenValue: When, date: LocalDate? = null, finishBy: Boolean = false) = tool {
        app.tools.registry.run(
            app.tools.planBacklogItem,
            PlanBacklogArgs(itemIds = listOf(item.id), `when` = whenValue, date = date?.toString(), finishBy = finishBy),
            it,
        )
    }

    /** A tick: done (a routine: done today), or, when it already is, taken back. */
    fun complete(item: BacklogItem) = tool {
        if (item.done || item.tickedOn(today())) app.tools.registry.run(app.tools.reopenBacklogItem, BacklogRefArgs(itemIds = listOf(item.id)), it)
        else app.tools.registry.run(app.tools.completeBacklogItem, BacklogRefArgs(itemIds = listOf(item.id)), it)
    }

    /** Only after the person confirmed on screen. */
    fun delete(item: BacklogItem) = tool {
        app.tools.registry.run(app.tools.removeBacklogItem, BacklogRefArgs(itemIds = listOf(item.id)), it, confirmed = true)
    }

    fun reopen(item: BacklogItem) = tool {
        app.tools.registry.run(app.tools.reopenBacklogItem, BacklogRefArgs(itemIds = listOf(item.id)), it)
    }

    fun setInterval(item: BacklogItem, days: Int?) = tool {
        app.tools.registry.run(app.tools.setRoutineInterval, SetRoutineIntervalArgs(itemIds = listOf(item.id), days = days), it)
    }

    fun setUrgent(item: BacklogItem, urgent: Boolean) = tool {
        app.tools.registry.run(app.tools.markUrgent, MarkUrgentArgs(itemIds = listOf(item.id), urgent = urgent), it)
    }

    fun waitForReply(item: BacklogItem, followUp: LocalDate?, waiting: Boolean = true) = tool {
        app.tools.registry.run(app.tools.waitForReply, WaitForReplyArgs(itemIds = listOf(item.id), followUp = followUp?.toString(), waiting = waiting), it)
    }

    /** One more step at the end of a routine. */
    /** Fixed days (and maybe a time) for a routine; null: back to roughly every week. */
    fun setSchedule(item: BacklogItem, schedule: Schedule?) = tool {
        app.tools.registry.run(
            app.tools.setRoutineSchedule,
            SetRoutineScheduleArgs(
                itemIds = listOf(item.id), weekdays = schedule?.weekdays.orEmpty(), dayOfMonth = schedule?.monthDay,
                weekOfMonth = schedule?.monthWeek, time = schedule?.time,
            ),
            it,
        )
    }

    fun addRoutineStep(routine: BacklogItem, text: String) {
        if (text.isBlank()) return
        tool { app.tools.registry.run(app.tools.addRoutine, AddRoutineArgs(routine.text, listOf(text), section = routine.section), it) }
    }

    /** A step to another place in its routine (1 = first). */
    fun moveStep(step: BacklogItem, position: Int) = tool {
        app.tools.registry.run(app.tools.moveStep, MoveStepArgs(stepId = step.id, position = position), it)
    }

    /** Hangs a routine on a moment of the day (null: whenever it fits). */
    fun setAnchor(item: BacklogItem, anchor: Anchor?) = tool {
        app.tools.registry.run(app.tools.setAnchor, SetAnchorArgs(itemIds = listOf(item.id), anchor = anchor), it)
    }

    fun move(item: BacklogItem, section: Section) = tool {
        app.tools.registry.run(app.tools.moveBacklogItem, MoveBacklogArgs(itemIds = listOf(item.id), section = section), it)
    }

    /** The add rows on the Tasks screen: same words as the capture bar ("om de 2 weken", "dringend"). */
    fun addTask(text: String, section: Section) {
        if (text.isBlank()) return
        tool {
            val hint = TaskWords.extract(text)
            app.tools.registry.run(
                app.tools.addToBacklog,
                AddToBacklogArgs(
                    hint.text, section, urgent = hint.urgent, intervalDays = hint.intervalDays, anchor = hint.anchor,
                    weekdays = hint.schedule?.weekdays.orEmpty(), dayOfMonth = hint.schedule?.monthDay,
                    weekOfMonth = hint.schedule?.monthWeek, time = hint.schedule?.time,
                ),
                it,
            )
        }
    }

    fun addStep(project: BacklogItem, text: String) {
        if (text.isBlank()) return
        tool { app.tools.registry.run(app.tools.addProjectStep, AddProjectStepArgs(projectId = project.id, project = project.text, text = text), it) }
    }

    // ---- Weekoverleg actions ----

    fun loadAgenda() = tool { _agenda.value = app.tools.registry.run(app.tools.weekoverlegAgenda, AgendaArgs(), it) }

    fun planMeal(date: LocalDate, dish: String, ingredients: String) {
        if (dish.isBlank()) return
        tool { app.tools.registry.run(app.tools.planMeal, PlanMealArgs(dish, date.toString(), listOf(ingredients).filter { i -> i.isNotBlank() }), it) }
    }

    /** Only after the person confirmed on screen. */
    fun removeMeal(meal: Meal) = tool {
        app.tools.registry.run(app.tools.removeMeal, RemoveMealArgs(mealId = meal.id, date = meal.date), it, confirmed = true)
    }

    /** Saves a recipe; [photo] is set when it changed (empty array = removed). */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    /** A recipe from a link; how it went shows in the chat. */
    fun importRecipe(url: String) {
        if (url.isBlank()) return
        tool {
            say(app.tools.registry.run(app.tools.importRecipe, ImportRecipeArgs(url.trim()), it).summary)
            _photoVersion.update { v -> v + 1 }
        }
    }

    fun saveRecipe(name: String, ingredients: List<String>, notes: String?, previousKey: String?, photo: ByteArray?) {
        if (name.isBlank()) return
        tool {
            val saved = app.tools.registry.run(app.tools.saveRecipe, SaveRecipeArgs(name, ingredients, notes, previousKey), it)
            if (photo != null) {
                val base64 = if (photo.isEmpty()) "" else kotlin.io.encoding.Base64.encode(photo)
                app.tools.registry.run(app.tools.setRecipePhoto, SetRecipePhotoArgs(saved.recipe.key, base64), it)
                _photoVersion.update { v -> v + 1 }
            }
            _messages.update { list -> list + ChatMessage(fromUser = false, text = saved.summary) }
        }
    }

    /** Puts a recipe on a day; its ingredients go onto the shopping list. */
    fun planRecipe(date: LocalDate, recipe: Recipe) = tool {
        val planned = app.tools.registry.run(app.tools.planMeal, PlanMealArgs(recipe.name, date.toString()), it)
        _messages.update { list -> list + ChatMessage(fromUser = false, text = planned.summary) }
    }

    /** Only after the person confirmed on screen. */
    fun deleteRecipe(recipe: Recipe) = tool {
        app.tools.registry.run(app.tools.removeRecipe, RecipeRefArgs(recipe.key), it, confirmed = true)
    }

    fun moveStock(item: StockItem, spot: StorageSpot) = tool {
        app.tools.registry.run(app.tools.moveStockItem, MoveStockItemArgs(itemIds = listOf(item.id), spot = spot.name), it)
    }

    /** Only after the person confirmed on screen. */
    fun removeSpot(spot: StorageSpot) = tool {
        app.tools.registry.run(app.tools.removeStorageSpot, RemoveStorageSpotArgs(spot.id), it, confirmed = true)
    }

    // ---- Weekoverleg: what went well, how heavy, Luister mee ----

    val wins: StateFlow<List<Win>> = app.meeting.wins.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val week: StateFlow<WeekRating?> = app.meeting.week.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val ruleChecks: StateFlow<List<RuleCheck>> = app.meeting.ruleChecks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** How each house rule goes (0–5), by rule id: this week's score or the last one. */
    val ruleScores: StateFlow<Map<String, Int>> = app.meeting.ruleScores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    /** Each house rule's score per meeting, oldest first, by rule id. */
    val ruleHistory: StateFlow<Map<String, List<Int>>> = app.meeting.ruleHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Each habit's score in the meeting that's still to come, by habit id. */
    val habitChecks: StateFlow<Map<String, Int>> = app.meeting.habitChecks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The habit coach for every habit (the four laws), by habit id: the same as review_habit gives an AI. */
    val habitReviews: StateFlow<Map<String, HabitReview>> = combine(backlog, steps, board, app.meeting.habitHistory, combine(lang, today) { l, d -> l to d }) { items, allSteps, boardItems, history, (l, day) ->
        val counts = allSteps.groupingBy { it.parentId }.eachCount()
        // Habits and household chores alike: every routine can be made easier along the four laws.
        items.filter { (it.isHabit || it.isRoutine()) && !it.done }.associate { h ->
            val goal = boardItems.firstOrNull { it.kind == BoardKind.GOAL && it.isOpen && it.linkedId == h.id }?.text
            h.id to habitReview(h, history[h.id].orEmpty(), frictionsAbout(h, boardItems, day), counts[h.id] ?: 0, goal, l)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The coach for every open house rule (the four laws), by rule id: the same as review_house_rule gives an AI. */
    val ruleReviews: StateFlow<Map<String, HabitReview>> = combine(board, app.meeting.ruleHistory, lang) { boardItems, history, l ->
        boardItems.filter { it.kind == BoardKind.RULE && it.isOpen }.associate { r ->
            r.id to ruleReview(r, history[r.id].orEmpty(), boardItems.filter { it.isAbout(r.id, r.text) }, l)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Something nice with a habit (make it attractive); empty removes it. */
    fun setPairing(item: BacklogItem, pairing: String) = tool {
        say(app.tools.registry.run(app.tools.setHabitPairing, SetPairingArgs(itemIds = listOf(item.id), pairing = pairing), it).summary)
    }

    /** A coach tip that needs no words: less or more often, the whole habit again, or say it in Wat ging goed. */
    fun applyTip(item: BacklogItem, tip: HabitTip) {
        when (tip.action) {
            TipAction.LESS_OFTEN -> lessOften(item)
            TipAction.MORE_OFTEN -> tool {
                val days = ((item.intervalDays ?: 2) / 2).coerceAtLeast(1)
                say(app.tools.registry.run(app.tools.setRoutineInterval, SetRoutineIntervalArgs(itemIds = listOf(item.id), days = days), it).summary)
            }
            TipAction.WHOLE -> setSmallStep(item, "")
            TipAction.CELEBRATE -> tool { say(app.tools.registry.run(app.tools.addWin, AddWinArgs(celebrateWords(item, it.lang)), it).summary) }
            else -> {}
        }
    }
    val weekOn: StateFlow<Boolean> = app.settings.value(SetWeekMetersTool.METERS_SETTING).map { it != "false" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val transcript: StateFlow<List<TranscriptLine>> = app.meeting.transcript.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val huddles: StateFlow<List<Huddle>> = app.meeting.huddles(52).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The last meeting's transcript: readable until the next meeting ends. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val lastTranscript: StateFlow<List<TranscriptLine>> = app.week.lastHuddle
        .flatMapLatest { h -> if (h == null) flow { emit(emptyList()) } else app.meeting.transcriptOf(h.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A win, under the topic it was named at (null: in general). */
    fun addWin(text: String, topic: Topic? = null) {
        if (text.isBlank()) return
        tool { app.tools.registry.run(app.tools.addWin, AddWinArgs(text, topic = topic), it) }
    }

    /** A past meeting's summary, put right afterwards. */
    fun editHuddle(huddle: Huddle, summary: String) = tool {
        app.tools.registry.run(app.tools.editHuddleSummary, EditHuddleArgs(huddleId = huddle.id, summary = summary), it)
    }

    /** Only after the person confirmed on screen. */
    fun removeWin(win: Win) = tool { app.tools.registry.run(app.tools.removeWin, RemoveWinArgs(win.id), it, confirmed = true) }

    /** How the week went (0–5), agreed together; null = no agreement. */
    fun rateWeek(score: Int?, suggested: Int) = tool {
        app.tools.registry.run(app.tools.rateWeek, RateWeekArgs(score, suggested), it)
    }

    fun setWeekOn(on: Boolean) = tool { app.tools.registry.run(app.tools.setWeekMeters, WeekMetersArgs(on), it) }

    /** How a house rule went this week; rules that ask a lot cloud Hearth's suggestion for the week. */
    fun checkRule(rule: BoardItem, score: Int) = tool {
        app.tools.registry.run(app.tools.checkHouseRule, CheckHouseRuleArgs(ruleId = rule.id, score = score), it)
        _agenda.value = app.tools.registry.run(app.tools.weekoverlegAgenda, AgendaArgs(), it)
    }

    /** A habit that asks for attention: half as often… */
    fun lessOften(item: BacklogItem) = tool {
        val days = (item.intervalDays ?: 7) * 2
        say(app.tools.registry.run(app.tools.setRoutineInterval, SetRoutineIntervalArgs(itemIds = listOf(item.id), days = days), it).summary)
        _agenda.value = app.tools.registry.run(app.tools.weekoverlegAgenda, AgendaArgs(), it)
    }

    fun rateHabit(item: BacklogItem, score: Int) = tool {
        say(app.tools.registry.run(app.tools.rateHabit, RateHabitArgs(itemIds = listOf(item.id), score = score), it).summary)
    }

    fun setSmallStep(item: BacklogItem, step: String) = tool {
        say(app.tools.registry.run(app.tools.setSmallStep, SmallStepArgs(itemIds = listOf(item.id), step = step), it).summary)
        _agenda.value = app.tools.registry.run(app.tools.weekoverlegAgenda, AgendaArgs(), it)
    }

    /** …or onto the Werkbord as an idea: how could this be smaller or easier? */
    fun makeEasier(item: BacklogItem) = tool {
        say(app.tools.registry.run(app.tools.addIdea, AddIdeaArgs(it.t("Makkelijker maken: ${item.text}", "Make it easier: ${item.text}")), it).summary)
    }

    /** The household's own words for Luister mee ("Jaules" is "Jules"), learned from what the family puts right. */
    val learnedWords: StateFlow<List<WordFix>> = app.settings.value(WordFixes.SETTING).map { WordFixes.parse(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** What Luister mee needs to know of the household: names, what exists, its own words. */
    private val household: StateFlow<Household> = combine(
        combine(members, backlog, steps) { m, b, st -> Triple(m, b, st) },
        board,
        combine(shopping, meals, dishes) { sh, me, d -> Triple(sh, me, d) },
        learnedWords,
    ) { (m, b, st), boardItems, (sh, me, d), words ->
        Household(
            people = m.map { it.name },
            backlog = b + st.filter { !it.done },
            board = boardItems,
            shopping = sh.flatMap { it.items }.filter { !it.bought }.map { it.name },
            meals = me,
            dishes = d.map { it.dish },
            words = words,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Household())

    /**
     * What Luister mee heard in this meeting and hasn't been put in place yet, oldest first, each line as Hearth reads it
     * while the meeting goes on: the household's words put right, names and what the household has marked, its topics.
     * No conclusions yet: those come from the full reading after the meeting ([openVoorstel]).
     */
    val heardLines: StateFlow<List<HeardLineView>> = combine(transcript, household) { lines, h ->
        val intake = MeetingIntake(h)
        lines.filter { !it.sorted }.map { HeardLineView(it, intake.read(it.text)) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Understanding conversations: on, ready, and how far its download is. */
    val interpreting: StateFlow<Interpreting.State> = app.interpreting.state
    val interpretingAvailable: Boolean get() = app.interpreting.available

    fun setInterpreter(on: Boolean) = tool { say(app.tools.registry.run(app.tools.setInterpreter, InterpreterArgs(on), it).summary) }

    fun retryInterpreter() {
        viewModelScope.launch { app.interpreting.start() }
    }

    /** Smarter listening: on, ready, and how far its download is. */
    val listening: StateFlow<SmartListening.State> = app.listening.state
    val listeningAvailable: Boolean get() = app.listening.available

    fun setSmartListening(on: Boolean) = tool { say(app.tools.registry.run(app.tools.setSmartListening, SmartListeningArgs(on), it).summary) }

    /** After a failed download or load: try again. */
    fun retrySmartListening() {
        viewModelScope.launch { app.listening.start() }
    }

    /** The model's readings, once smarter listening is ready (null: the rules alone). */
    private fun sense(): ((String) -> Reading?)? = if (app.listening.state.value == SmartListening.State.Ready) app.listening::read else null

    /** The full reading after the meeting: how far it is, readings done of all (null: not reading). */
    private val _processing = MutableStateFlow<Pair<Int, Int>?>(null)
    val processing: StateFlow<Pair<Int, Int>?> = _processing.asStateFlow()
    private val processor = MeetingProcessor(app.orchestrator)
    private var processJob: Job? = null
    /** The last full reading and the lines it read, for the conversation export. */
    private var lastProcessed: Pair<List<String>, Processed>? = null

    /**
     * Reads [lines] as a whole, the way the Voorstel does: the household's own words, topic by topic, the interpreter when
     * it is ready (else the rules), held against what exists. Runs in the background and says how far it is.
     */
    private suspend fun process(lines: List<TranscriptLine>): Processed {
        val c = ctx(ToolSource.UI)
        val h = household.value
        val heard = lines.map { HeardLine(it.id, it.text, it.createdAt) }
        val ready = app.interpreting.state.value == Interpreting.State.Ready
        val interpret: (suspend (List<String>) -> List<app.hearth.ai.interpret.Action>)? =
            if (ready) { texts -> app.interpreting.read(texts, spokenDate(c.today)).orEmpty() } else null
        _processing.value = 0 to 1
        try {
            return withContext(Dispatchers.Default) {
                // The sentence model reads every clause first, so the rules can lean on it.
                if (app.listening.state.value == SmartListening.State.Ready) {
                    val asked = mutableSetOf<String>()
                    app.orchestrator.listen(heard.map { WordFixes.apply(it.text, h.words) }, c, h.people, heard.map { it.at }) { asked += it; null }
                    app.listening.prepare(asked)
                }
                processor.process(heard, h, c, sense(), interpret) { done, total -> _processing.value = done to total }
            }.also { lastProcessed = lines.map { it.id } to it }
        } finally {
            _processing.value = null
        }
    }

    /**
     * The conversation of [lines] with everything Hearth made of it, as JSON, to see how well it understood a real
     * meeting: every line as heard and as read (the household's words, its topics), the rules' thoughts, the topics as
     * they went, the interpreter's raw actions, and the Voorstel as the family left it. [save] gets a file name and the JSON.
     */
    fun exportConversation(lines: List<TranscriptLine>, save: (String, String) -> Unit) {
        if (lines.isEmpty()) return
        viewModelScope.launch {
            val c = ctx(ToolSource.UI)
            val ids = lines.map { it.id }
            val done = lastProcessed?.takeIf { it.first == ids }?.second ?: process(lines)
            val rows = _voorstel.value?.takeIf { voorstelLines == ids }
            val json = withContext(Dispatchers.Default) {
                ConversationExport.json(
                    lines.map { ConversationExport.Line(it.id, it.text, it.createdAt, it.sorted) },
                    done,
                    rows?.map { ConversationExport.Row(it.proposal, it.lines, it.checked, it.changed, it.added) },
                    sense(),
                    mapOf(
                        "lang" to c.lang.name,
                        "today" to c.today.toString(),
                        "people" to members.value.joinToString(", ") { it.name },
                        "learnedWords" to learnedWords.value.size.toString(),
                        "smartListening" to app.listening.state.value.toString(),
                        "interpreter" to app.interpreting.state.value.toString(),
                    ),
                    kotlin.time.Instant.fromEpochMilliseconds(nowMillis()).toString(),
                )
            }
            val day = lines.first().createdAt.let { kotlin.time.Instant.fromEpochMilliseconds(it).toString().take(10) }
            save("hearth-gesprek-$day.json", json)
        }
    }

    /** A line Luister mee misheard, put right in Gesprek; the words that changed are remembered for next time. */
    fun fixLine(line: TranscriptLine, text: String) {
        if (text.isBlank() || text.trim() == line.text) return
        tool { say(app.tools.registry.run(app.tools.fixTranscriptLine, FixLineArgs(line.id, text), it).summary) }
    }

    /** Only after the person confirmed on screen. */
    fun forgetWord(heard: String) = tool {
        app.tools.registry.run(app.tools.forgetWord, ForgetWordArgs(heard), it, confirmed = true)
    }

    /** The household's own words (people, stock, recipes), so the recognizer hears "Mia" and not "mia's". */
    val vocabulary: StateFlow<List<String>> = combine(members, stockItems, recipes, learnedWords) { m, s, r, w ->
        // The words the family put right first: those are the ones the recognizer gets wrong.
        (WordFixes.vocabulary(w).reversed() + m.map { it.name } + s.map { it.name } + r.map { it.name }).map { it.trim() }.filter { it.length >= 2 }.distinct().take(80)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    /** The summary the family reads and agrees on before Klaar records it. */
    private val _agree = MutableStateFlow<List<String>?>(null)
    val agree: StateFlow<List<String>?> = _agree.asStateFlow()

    fun prepareFinish() = tool {
        _agree.value = app.tools.registry.run(app.tools.finishWeekoverleg, FinishArgs(preview = true), it).lines
    }

    fun closeAgree() {
        _agree.value = null
    }

    /** Luister mee heard a stretch of talk. */
    fun heard(line: String) {
        if (line.isBlank()) return
        // Some recognizers give a stretch twice, or again with a few words more: only what is new is kept.
        val last = transcript.value.lastOrNull()?.takeIf { nowMillis() - it.createdAt < 60_000 }
        val new = Spoken.newPart(last?.text, line) ?: return
        tool { app.tools.registry.run(app.tools.addTranscriptLine, TranscriptLineArgs(new), it) }
    }

    /** Only after the person confirmed on screen. */
    fun clearTranscript() = tool { app.tools.registry.run(app.tools.clearTranscript, ClearTranscriptArgs(), it, confirmed = true) }

    private val _voorstel = MutableStateFlow<List<VoorstelRow>?>(null)
    /** What Luister mee heard, sorted into places, waiting for one tap. Null when closed. */
    val voorstel: StateFlow<List<VoorstelRow>?> = _voorstel.asStateFlow()
    private var voorstelLines: List<String> = emptyList()

    /**
     * Zet op zijn plek: the whole conversation is read now (it takes a moment; the sheet shows how far), and every
     * conclusion lands in the Voorstel: new things ticked, changes to what exists ticked, what is there already not.
     */
    fun openVoorstel() {
        val lines = transcript.value.filter { !it.sorted }
        voorstelLines = lines.map { it.id }
        _voorstel.value = emptyList()
        processJob?.cancel()
        processJob = viewModelScope.launch {
            val c = ctx(ToolSource.UI)
            val done = process(lines)
            // A thought that was just talk shows once, as Gewoon gepraat: it can still be put somewhere.
            _voorstel.value = done.found.map { f -> VoorstelRow(f.proposal, checked = f.proposal.calls.isNotEmpty(), lines = f.lines) } +
                done.quiet.map { t -> VoorstelRow(app.orchestrator.proposal(Spoken.clean(t.heard), Place.NONE, c).copy(heard = t.heard), checked = false, lines = t.lines.toList()) }
        }
    }

    fun closeVoorstel() {
        processJob?.cancel()
        _voorstel.value = null
    }

    /** A suggestion after the family changed it: held against what exists again, unless they chose to add it as new. */
    private fun again(p: Orchestrator.Proposal, row: VoorstelRow): VoorstelRow {
        val matched = if (row.asNew) p else Matcher.match(p, household.value)
        return VoorstelRow(matched, checked = matched.place != Place.NONE && matched.calls.isNotEmpty(), changed = true, asNew = row.asNew, lines = row.lines)
    }

    /** The family tapped another place for a sentence. */
    fun moveVoorstel(index: Int, place: Place) = _voorstel.update { rows ->
        rows?.mapIndexed { i, row ->
            if (i != index) row else again(app.orchestrator.proposal(row.proposal.sentence, place, ctx(ToolSource.UI)).copy(heard = row.proposal.heard), row)
        }
    }

    /** A suggestion changed in its sheet: its name, place and day as the family set them. Names typed again teach words. */
    fun editVoorstel(index: Int, change: SuggestionChange) {
        val row = _voorstel.value?.getOrNull(index)?.takeIf { !it.added } ?: return
        _voorstel.update { rows ->
            rows?.mapIndexed { i, r -> if (i != index) r else again(app.orchestrator.edited(r.proposal, change.text, change.place, change.date, change.next, ctx(ToolSource.UI)), r) }
        }
        val learned = WordFixes.learnSpelling(row.proposal.heard, change.text)
        if (learned.isNotEmpty()) tool { c -> learned.forEach { app.tools.registry.run(app.tools.learnWords, LearnWordsArgs(it.heard, it.meant), c) } }
    }

    /** Toch nieuw: it is not about what Hearth found; add it as a new item after all. */
    fun asNewVoorstel(index: Int) = _voorstel.update { rows ->
        rows?.mapIndexed { i, row ->
            val match = row.proposal.match
            if (i != index || match == null) row else VoorstelRow(row.proposal.copy(calls = match.asNew, match = null), checked = match.asNew.isNotEmpty(), changed = true, asNew = true, lines = row.lines)
        }
    }

    fun toggleVoorstel(index: Int) = _voorstel.update { rows ->
        rows?.mapIndexed { i, row -> if (i == index) row.copy(checked = !row.checked && row.proposal.calls.isNotEmpty()) else row }
    }

    /** Adds every ticked line through the same tools as everywhere else, then marks the talk as sorted. */
    fun addVoorstel() {
        val rows = _voorstel.value ?: return
        _voorstel.value = null
        tool {
            var added = 0
            rows.filter { r -> r.checked }.forEach { row ->
                row.proposal.calls.forEach { call -> if (app.tools.registry.execute(call, it) is ToolOutcome.Done) added++ }
            }
            app.tools.registry.run(app.tools.markTranscriptSorted, MarkSortedArgs(voorstelLines), it)
            _messages.update { list ->
                list + ChatMessage(
                    fromUser = false,
                    text = if (added == 0) it.t("Niets toegevoegd; het gesprek blijft bewaard.", "Nothing added; the conversation is kept.")
                    else it.t("$added dingen op hun plek gezet.", "$added things put in their place."),
                )
            }
            loadAgenda()
        }
    }

    fun finishWeekoverleg() = tool {
        _agree.value = null
        val done = app.tools.registry.run(app.tools.finishWeekoverleg, FinishArgs(), it)
        _messages.update { list -> list + ChatMessage(fromUser = false, text = done.summary) }
    }

    // ---- Werkbord actions ----

    fun addMember(name: String) {
        if (name.isBlank()) return
        tool { app.tools.registry.run(app.tools.addMember, AddMemberArgs(name), it) }
    }

    /** Only after the person confirmed on screen. */
    fun removeMember(member: Member) = tool {
        app.tools.registry.run(app.tools.removeMember, RemoveMemberArgs(member.id), it, confirmed = true)
    }

    fun addRule(text: String) {
        if (text.isBlank()) return
        tool { app.tools.registry.run(app.tools.addHouseRule, AddHouseRuleArgs(text), it) }
    }

    /** The Werkbord's working points: a friction, or an idea when it starts with "idee:". */
    fun logFriction(text: String) {
        if (text.isBlank()) return
        tool {
            if (KeywordRouter.section(text) == Section.IDEA) {
                app.tools.registry.run(app.tools.addIdea, AddIdeaArgs(text), it)
            } else {
                app.tools.registry.run(app.tools.logFriction, LogFrictionArgs(text), it)
            }
        }
    }

    /** Written in the family's own words: a house rule, a routine (new or changed), a task, or let go. */
    fun resolveFriction(item: BoardItem, write: FrictionWrite) = tool {
        val args = ResolveFrictionArgs(
            itemIds = listOf(item.id), outcome = write.outcome, text = write.text, section = write.section,
            routine = write.routine?.id, anchor = write.anchor, smallStep = write.smallStep, pairing = write.pairing,
        )
        say(app.tools.registry.run(app.tools.resolveFriction, args, it).summary)
    }

    /** What a friction needs, chosen together in one tap; null puts it back to "te bespreken". */
    fun markFriction(item: BoardItem, direction: FrictionDirection?) = tool {
        app.tools.registry.run(app.tools.markFriction, MarkFrictionArgs(itemIds = listOf(item.id), direction = direction), it)
    }

    fun addGoal(text: String, scope: GoalScope, member: Member?) {
        if (text.isBlank()) return
        tool { app.tools.registry.run(app.tools.addGoal, AddGoalArgs(text, scope, member?.id), it) }
    }

    /** A goal's place in the week: a rhythm, free time on a day, or one small step. */
    fun setGoalStep(goal: BoardItem, plan: GoalPlan) {
        if (plan.text.isBlank()) return
        tool {
            val args = SetGoalStepArgs(
                goalId = goal.id, step = plan.text, toWeek = plan.toWeek, how = plan.how,
                anchor = plan.anchor, intervalDays = plan.intervalDays, date = plan.date?.toString(),
            )
            say(app.tools.registry.run(app.tools.setGoalStep, args, it).summary)
        }
    }

    fun achieveGoal(goal: BoardItem) = tool {
        app.tools.registry.run(app.tools.achieveGoal, AchieveGoalArgs(itemIds = listOf(goal.id)), it)
    }

    /** A goal ticked by mistake: open again. */
    fun reopenGoal(goal: BoardItem) = tool {
        app.tools.registry.run(app.tools.achieveGoal, AchieveGoalArgs(itemIds = listOf(goal.id), reached = false), it)
    }

    /** Other words for a house rule, friction, idea or goal. */
    fun renameBoardItem(item: BoardItem, text: String) {
        if (text.isBlank() || text == item.text) return
        tool { app.tools.registry.run(app.tools.editBoardItem, EditBoardItemArgs(itemId = item.id, newText = text), it) }
    }

    /** A house rule helped along the four laws: its moment, something nice, a small version. */
    fun setRuleHelp(rule: BoardItem, help: RuleHelp) = tool {
        app.tools.registry.run(
            app.tools.setRuleHelp,
            SetRuleHelpArgs(itemId = rule.id, anchor = help.anchor, noMoment = help.noMoment, pairing = help.pairing, smallStep = help.smallStep),
            it,
        )
    }

    /** Only after the person confirmed on screen. */
    fun deleteBoardItem(item: BoardItem) = tool {
        app.tools.registry.run(app.tools.removeBoardItem, BoardRefArgs(itemIds = listOf(item.id)), it, confirmed = true)
    }

    // ---- shopping actions ----

    fun addShopping(name: String) {
        if (name.isBlank()) return
        tool { app.tools.registry.run(app.tools.addShoppingItem, AddShoppingItemArgs(name = name), it) }
    }

    /** Only after the person confirmed on screen. */
    fun removeShopping(item: ShoppingItem) = tool {
        app.tools.registry.run(app.tools.removeShoppingItem, MarkBoughtArgs(itemIds = listOf(item.id)), it, confirmed = true)
    }

    /** Only after the person confirmed on screen. */
    fun clearBought() = tool { app.tools.registry.run(app.tools.clearBoughtItems, ClearBoughtArgs(), it, confirmed = true) }

    /** One tap: out (onto the list) or back in the house (off the list). */
    fun toggleStock(item: StockItem) = tool {
        val state = if (item.state == StockState.IN_STOCK) StockState.OUT else StockState.IN_STOCK
        app.tools.registry.run(app.tools.setStockState, SetStockStateArgs(itemIds = listOf(item.id), state = state), it)
    }

    fun addStock(name: String, spot: StorageSpot) {
        if (name.isBlank()) return
        tool { app.tools.registry.run(app.tools.addStockItem, AddStockItemArgs(name, spot.name), it) }
    }

    fun addSpot(name: String) {
        if (name.isBlank()) return
        tool { app.tools.registry.run(app.tools.addStorageSpot, AddStorageSpotArgs(name), it) }
    }

    /** Only after the person confirmed on screen. */
    fun removeStock(item: StockItem) = tool {
        app.tools.registry.run(app.tools.removeStockItem, StockRefArgs(itemIds = listOf(item.id)), it, confirmed = true)
    }

    fun toggleBought(item: ShoppingItem) = tool {
        val args = MarkBoughtArgs(itemIds = listOf(item.id))
        if (item.bought) app.tools.registry.run(app.tools.unmarkBought, args, it) else app.tools.registry.run(app.tools.markBought, args, it)
    }

    // ---- helpers ----

    private fun ctx(source: ToolSource): ToolContext = app.context(source).copy(today = today.value)

    private fun tool(block: suspend (ToolContext) -> Unit) {
        viewModelScope.launch {
            try {
                block(ctx(ToolSource.UI))
            } catch (e: ToolException) {
                _messages.update { it + ChatMessage(fromUser = false, text = e.userMessage) }
            }
        }
    }

    private fun group(shops: List<Shop>, items: List<ShoppingItem>): List<ShopSection> {
        val byShop = items.groupBy { it.shopId }
        return shops.mapNotNull { shop -> byShop[shop.id]?.let { ShopSection(shop, it) } }
    }

    @OptIn(ExperimentalTime::class)
    private fun startOfToday(): Long = today().atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    companion object {
        private val YES = setOf("ja", "jawel", "yes", "yep", "ok", "oké", "okay", "doe maar", "go")
        private val NO = setOf("nee", "neen", "no", "nope", "stop", "laat maar", "cancel")
        private const val KEEP_BOUGHT_MILLIS = 6 * 60 * 60_000L

    }
}

/** One line Luister mee heard, and how Hearth reads it while the meeting goes on. */
data class HeardLineView(val line: TranscriptLine, val intake: LineIntake)

/** One suggestion of the Voorstel and whether it gets added ([added]: it is in place already). */
data class VoorstelRow(
    val proposal: Orchestrator.Proposal,
    val checked: Boolean,
    val added: Boolean = false,
    /** The family changed it (its name, place or day). */
    val changed: Boolean = false,
    /** Toch nieuw: added as a new item, not held against what exists. */
    val asNew: Boolean = false,
    /** The lines of the conversation it rests on (indexes), for the export. */
    val lines: List<Int> = emptyList(),
)

/** What's switched on with Google, and when it last ran (epoch ms). */
/** The household's sync as the Instellingen card shows it. */
data class SyncState(
    val on: Boolean = false,
    val lastSync: Long? = null,
    val available: Boolean = false,
    val busy: Boolean = false,
    val problem: String? = null,
)

data class GoogleState(
    val backup: Boolean = false,
    val lastBackup: Long? = null,
    val calendar: Boolean = false,
    val lastSync: Long? = null,
)

/** The day as the interpreter learned it: "dinsdag 27 oktober". */
private fun spokenDate(day: kotlinx.datetime.LocalDate): String =
    "${weekdayName(day.dayOfWeek, Lang.NL)} ${day.day} ${listOf("januari", "februari", "maart", "april", "mei", "juni", "juli", "augustus", "september", "oktober", "november", "december")[day.month.ordinal]}"
