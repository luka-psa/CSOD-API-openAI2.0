package app.hearth.ui

import app.hearth.core.tools.system.SetLanguageArgs
import app.hearth.core.tools.backlog.SetAnchorArgs
import app.hearth.core.model.Anchor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hearth.ai.ChatReply
import app.hearth.ai.ChatTurn
import app.hearth.ai.PendingAction
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Lang
import app.hearth.core.model.Shop
import app.hearth.core.model.ShoppingItem
import app.hearth.core.model.nowMillis
import app.hearth.core.model.today
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
import app.hearth.core.tools.board.SetGoalStepArgs
import app.hearth.core.tools.backlog.AddProjectStepArgs
import app.hearth.core.tools.backlog.AddToBacklogArgs
import app.hearth.core.tools.backlog.BacklogRefArgs
import app.hearth.core.tools.backlog.MarkUrgentArgs
import app.hearth.core.tools.backlog.MoveBacklogArgs
import app.hearth.core.tools.backlog.SetRoutineIntervalArgs
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
import app.hearth.ai.route.MeetingWords
import app.hearth.core.tools.week.FinishArgs
import app.hearth.core.model.Topic
import app.hearth.core.model.topic
import app.hearth.core.tools.board.AddIdeaArgs
import app.hearth.core.model.TranscriptLine
import app.hearth.core.model.Ease
import app.hearth.core.model.easeNow
import app.hearth.core.tools.backlog.RateHabitArgs
import app.hearth.core.tools.backlog.SmallStepArgs
import app.hearth.core.model.Meter
import app.hearth.core.model.MeterValue
import app.hearth.core.model.RuleCheck
import app.hearth.core.model.dayOf
import app.hearth.core.model.isRoutine
import app.hearth.core.tools.week.CheckHouseRuleArgs
import app.hearth.core.tools.week.SetWeekMetersTool
import app.hearth.core.tools.week.WeekMeterArgs
import app.hearth.core.tools.week.WeekMetersArgs
import app.hearth.core.model.Win
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.week.AddWinArgs
import app.hearth.core.tools.week.ClearTranscriptArgs
import app.hearth.core.tools.week.MarkSortedArgs
import app.hearth.core.tools.week.RemoveWinArgs
import app.hearth.core.tools.week.TranscriptLineArgs
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    fun saveConnection(enabled: Boolean? = null, relayUrl: String? = null) = tool {
        app.tools.registry.run(app.tools.saveConnection, SaveConnectionArgs(enabled, relayUrl), it)
    }

    val offersExamples = app.offersExamples

    /** One or two examples in every section, to see how Hearth works. */
    fun loadExamples() = tool { say(app.tools.registry.run(app.tools.loadExamples, NoArgs(), it).summary) }

    fun showPairingCode() = app.relay.requestPairingCode()
    fun hidePairingCode() = app.relay.hidePairingCode()

    /** Unlinks an AI app (or all, with null). The screen asked first. */
    fun unlinkApp(id: String?) = app.relay.unlink(id)

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

    fun complete(item: BacklogItem) = tool {
        app.tools.registry.run(app.tools.completeBacklogItem, BacklogRefArgs(itemIds = listOf(item.id)), it)
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
                AddToBacklogArgs(hint.text, section, urgent = hint.urgent, intervalDays = hint.intervalDays, anchor = hint.anchor),
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
    val meters: StateFlow<List<MeterValue>> = app.meeting.meters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ruleChecks: StateFlow<List<RuleCheck>> = app.meeting.ruleChecks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val metersOn: StateFlow<Boolean> = app.settings.value(SetWeekMetersTool.METERS_SETTING).map { it != "false" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val transcript: StateFlow<List<TranscriptLine>> = app.meeting.transcript.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val huddles: StateFlow<List<Huddle>> = app.meeting.huddles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The last meeting's transcript: readable until the next meeting ends. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val lastTranscript: StateFlow<List<TranscriptLine>> = app.week.lastHuddle
        .flatMapLatest { h -> if (h == null) flow { emit(emptyList()) } else app.meeting.transcriptOf(h.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addWin(text: String) {
        if (text.isBlank()) return
        tool { app.tools.registry.run(app.tools.addWin, AddWinArgs(text), it) }
    }

    /** Only after the person confirmed on screen. */
    fun removeWin(win: Win) = tool { app.tools.registry.run(app.tools.removeWin, RemoveWinArgs(win.id), it, confirmed = true) }

    /** Agreed together; null = no agreement, so neutral. */
    fun setMeter(meter: Meter, value: Int?, suggested: Int?) = tool {
        app.tools.registry.run(app.tools.setWeekMeter, WeekMeterArgs(meter, value, suggested), it)
    }

    fun setMetersOn(on: Boolean) = tool { app.tools.registry.run(app.tools.setWeekMeters, WeekMetersArgs(on), it) }

    /** Did a house rule work this week? Then the suggestion for "wat schuurde" follows. */
    fun checkRule(rule: BoardItem, worked: Boolean) = tool {
        app.tools.registry.run(app.tools.checkHouseRule, CheckHouseRuleArgs(ruleId = rule.id, worked = worked), it)
        _agenda.value = app.tools.registry.run(app.tools.weekoverlegAgenda, AgendaArgs(), it)
    }

    /** A habit that asks for attention: half as often… */
    fun lessOften(item: BacklogItem) = tool {
        val days = (item.intervalDays ?: 7) * 2
        say(app.tools.registry.run(app.tools.setRoutineInterval, SetRoutineIntervalArgs(itemIds = listOf(item.id), days = days), it).summary)
        _agenda.value = app.tools.registry.run(app.tools.weekoverlegAgenda, AgendaArgs(), it)
    }

    fun rateHabit(item: BacklogItem, ease: Ease) = tool {
        say(app.tools.registry.run(app.tools.rateHabit, RateHabitArgs(itemIds = listOf(item.id), ease = ease), it).summary)
    }

    fun setSmallStep(item: BacklogItem, step: String) = tool {
        say(app.tools.registry.run(app.tools.setSmallStep, SmallStepArgs(itemIds = listOf(item.id), step = step), it).summary)
        _agenda.value = app.tools.registry.run(app.tools.weekoverlegAgenda, AgendaArgs(), it)
    }

    /** …or onto the Werkbord as an idea: how could this be smaller or easier? */
    fun makeEasier(item: BacklogItem) = tool {
        say(app.tools.registry.run(app.tools.addIdea, AddIdeaArgs(it.t("Makkelijker maken: ${item.text}", "Make it easier: ${item.text}")), it).summary)
    }

    /** What Luister mee heard and hasn't been put in place yet, newest first, with the topic it would go to. */
    val heardLately: StateFlow<List<HeardLine>> = transcript
        .map { lines -> MeetingWords.sentences(lines.filter { !it.sorted }.map { it.text }).takeLast(4).reversed().map(::heardLine) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun heardLine(sentence: String): HeardLine {
        val place = MeetingWords.place(sentence, today())
        val section = KeywordRouter.section(sentence)
        val topic = when (place) {
            Place.PLANNING -> if (TaskWords.extract(sentence).anchor != null) Topic.RHYTHM else section.topic()
            Place.EVENT -> if (section == Section.APPOINTMENT || section == Section.ADMIN) Topic.ADMIN else Topic.FREE_TIME
            Place.FREE_TIME -> Topic.FREE_TIME
            Place.SHOPPING -> Topic.SHOPPING
            Place.MEAL -> Topic.MENU
            Place.HOUSE_RULE -> Topic.RULES
            Place.FRICTION, Place.IDEA, Place.GOAL -> Topic.FRICTION
            Place.WIN, Place.NONE -> null
        }
        return HeardLine(sentence, place, topic)
    }

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
        tool { app.tools.registry.run(app.tools.addTranscriptLine, TranscriptLineArgs(line), it) }
    }

    /** Only after the person confirmed on screen. */
    fun clearTranscript() = tool { app.tools.registry.run(app.tools.clearTranscript, ClearTranscriptArgs(), it, confirmed = true) }

    private val _voorstel = MutableStateFlow<List<VoorstelRow>?>(null)
    /** What Luister mee heard, sorted into places, waiting for one tap. Null when closed. */
    val voorstel: StateFlow<List<VoorstelRow>?> = _voorstel.asStateFlow()
    private var voorstelLines: List<String> = emptyList()

    fun openVoorstel() {
        val lines = transcript.value.filter { !it.sorted }
        voorstelLines = lines.map { it.id }
        _voorstel.value = app.orchestrator.propose(lines.map { it.text }, ctx(ToolSource.UI))
            .map { VoorstelRow(it, checked = it.place != Place.NONE && it.calls.isNotEmpty()) }
    }

    fun closeVoorstel() {
        _voorstel.value = null
    }

    /** The family tapped another place for a sentence. */
    fun moveVoorstel(index: Int, place: Place) = _voorstel.update { rows ->
        rows?.mapIndexed { i, row ->
            if (i != index) row else app.orchestrator.proposal(row.proposal.sentence, place, ctx(ToolSource.UI)).let { p ->
                VoorstelRow(p, checked = p.place != Place.NONE && p.calls.isNotEmpty())
            }
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

    fun resolveFriction(item: BoardItem, outcome: FrictionOutcome, text: String? = null) = tool {
        app.tools.registry.run(app.tools.resolveFriction, ResolveFrictionArgs(itemIds = listOf(item.id), outcome = outcome, text = text), it)
    }

    fun addGoal(text: String, scope: GoalScope, member: Member?) {
        if (text.isBlank()) return
        tool { app.tools.registry.run(app.tools.addGoal, AddGoalArgs(text, scope, member?.id), it) }
    }

    fun setGoalStep(goal: BoardItem, step: String, toWeek: Boolean) {
        if (step.isBlank()) return
        tool { app.tools.registry.run(app.tools.setGoalStep, SetGoalStepArgs(goalId = goal.id, step = step, toWeek = toWeek), it) }
    }

    fun achieveGoal(goal: BoardItem) = tool {
        app.tools.registry.run(app.tools.achieveGoal, BoardRefArgs(itemIds = listOf(goal.id)), it)
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

/** A sentence Luister mee just heard, and the topic tile it lights up (null: just talk, a win, or still to sort). */
data class HeardLine(val text: String, val place: Place, val topic: Topic?)

/** One sentence of the Voorstel and whether it gets added. */
data class VoorstelRow(val proposal: Orchestrator.Proposal, val checked: Boolean)

/** What's switched on with Google, and when it last ran (epoch ms). */
data class GoogleState(
    val backup: Boolean = false,
    val lastBackup: Long? = null,
    val calendar: Boolean = false,
    val lastSync: Long? = null,
)
