package app.hearth.ui

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
import app.hearth.core.model.Section
import app.hearth.core.tools.backlog.AddProjectStepArgs
import app.hearth.core.tools.backlog.AddToBacklogArgs
import app.hearth.core.tools.backlog.BacklogRefArgs
import app.hearth.core.tools.backlog.MarkUrgentArgs
import app.hearth.core.tools.backlog.MoveBacklogArgs
import app.hearth.core.tools.backlog.SetRoutineIntervalArgs
import app.hearth.core.tools.backlog.WaitForReplyArgs
import app.hearth.core.tools.backlog.PlanBacklogArgs
import app.hearth.core.tools.backlog.When
import app.hearth.core.tools.food.AddShoppingItemArgs
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

    val lang: Lang = app.lang
    val speechLanguage: String = if (lang == Lang.NL) "nl-BE" else "en-US"

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
                AddToBacklogArgs(hint.text, section, urgent = hint.urgent, intervalDays = hint.intervalDays),
                it,
            )
        }
    }

    fun addStep(project: BacklogItem, text: String) {
        if (text.isBlank()) return
        tool { app.tools.registry.run(app.tools.addProjectStep, AddProjectStepArgs(projectId = project.id, project = project.text, text = text), it) }
    }

    // ---- shopping actions ----

    fun addShopping(name: String) {
        if (name.isBlank()) return
        tool { app.tools.registry.run(app.tools.addShoppingItem, AddShoppingItemArgs(name = name), it) }
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
