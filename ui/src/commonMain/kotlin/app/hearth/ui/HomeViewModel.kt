package app.hearth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hearth.ai.ChatTurn
import app.hearth.ai.PendingAction
import app.hearth.ai.llm.ProviderConfig
import app.hearth.core.model.InboxItem
import app.hearth.core.model.Lang
import app.hearth.core.model.Shop
import app.hearth.core.model.ShoppingItem
import app.hearth.core.model.nowMillis
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolSource
import app.hearth.core.tools.food.AddShoppingItemArgs
import app.hearth.core.tools.food.MarkBoughtArgs
import app.hearth.core.tools.inbox.ArchiveInboxArgs
import app.hearth.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String, val pending: PendingAction? = null)

data class ShopSection(val shop: Shop, val items: List<ShoppingItem>)

class HomeViewModel(private val app: AppContainer) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Bought items stay visible (struck through) for a while, so the list never needs clearing. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val shopping: StateFlow<List<ShopSection>> =
        flow {
            while (true) {
                emit(nowMillis() - KEEP_BOUGHT_MILLIS)
                delay(REFRESH_MILLIS)
            }
        }
            .flatMapLatest { since -> combine(app.shopping.shops, app.shopping.visibleItems(since), ::group) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val speechLanguage: String = if (app.lang == Lang.NL) "nl-BE" else "en-US"

    val inbox: StateFlow<List<InboxItem>> =
        app.inbox.open.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ai: StateFlow<ProviderConfig?> = app.ai

    private val pending: PendingAction? get() = _messages.value.lastOrNull()?.pending

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val waiting = pending
        if (waiting != null) {
            when (trimmed.lowercase().trimEnd('.', '!')) {
                in YES -> return answer(trimmed, confirm = true)
                in NO -> return answer(trimmed, confirm = false)
            }
        }
        val history = _messages.value.map { ChatTurn(it.fromUser, it.text) }
        clearPending()
        _messages.update { it + ChatMessage(fromUser = true, text = trimmed) }
        reply { app.orchestrator.handle(trimmed, app.context(ToolSource.CHAT), history) }
    }

    /** The yes/no buttons under a confirmation question. */
    fun answer(label: String, confirm: Boolean) {
        val waiting = pending ?: return
        clearPending()
        _messages.update { it + ChatMessage(fromUser = true, text = label) }
        if (confirm) {
            reply { app.orchestrator.confirm(waiting, app.context(ToolSource.CHAT)) }
        } else {
            val ctx = app.context(ToolSource.CHAT).copy(lang = waiting.lang)
            _messages.update { it + ChatMessage(fromUser = false, text = ctx.t("Oké, niets veranderd.", "Okay, nothing changed.")) }
        }
    }

    private fun reply(block: suspend () -> app.hearth.ai.ChatReply) {
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

    fun archiveInbox(item: InboxItem) {
        viewModelScope.launch {
            runTool { app.tools.registry.run(app.tools.archiveInboxItem, ArchiveInboxArgs(itemIds = listOf(item.id)), it) }
        }
    }

    fun saveAi(config: ProviderConfig?) = app.saveAi(config)

    suspend fun testAi(config: ProviderConfig): String? = app.testAi(config)

    /** UI path: the same tool the chat uses, called with typed args. */
    fun addFromCard(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runTool { app.tools.registry.run(app.tools.addShoppingItem, AddShoppingItemArgs(name = name), it) }
        }
    }

    fun toggleBought(item: ShoppingItem) {
        viewModelScope.launch {
            val args = MarkBoughtArgs(itemIds = listOf(item.id))
            runTool {
                if (item.bought) app.tools.registry.run(app.tools.unmarkBought, args, it)
                else app.tools.registry.run(app.tools.markBought, args, it)
            }
        }
    }

    private suspend fun runTool(block: suspend (ToolContext) -> Unit) {
        try {
            block(app.context(ToolSource.UI))
        } catch (e: ToolException) {
            _messages.update { it + ChatMessage(fromUser = false, text = e.userMessage) }
        }
    }

    private fun group(shops: List<Shop>, items: List<ShoppingItem>): List<ShopSection> {
        val byShop = items.groupBy { it.shopId }
        return shops.mapNotNull { shop -> byShop[shop.id]?.let { ShopSection(shop, it) } }
    }

    companion object {
        private val YES = setOf("ja", "jawel", "yes", "yep", "ok", "oké", "okay", "doe maar", "go")
        private val NO = setOf("nee", "neen", "no", "nope", "stop", "laat maar", "cancel")
        private const val KEEP_BOUGHT_MILLIS = 6 * 60 * 60_000L
        private const val REFRESH_MILLIS = 15 * 60_000L
    }
}
