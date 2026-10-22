package app.hearth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hearth.core.model.Lang
import app.hearth.core.model.Shop
import app.hearth.core.model.ShoppingItem
import app.hearth.core.model.nowMillis
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolSource
import app.hearth.core.tools.food.AddShoppingItemArgs
import app.hearth.core.tools.food.MarkBoughtArgs
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

data class ChatMessage(val fromUser: Boolean, val text: String)

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

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _messages.update { it + ChatMessage(fromUser = true, text = trimmed) }
        viewModelScope.launch {
            _busy.value = true
            val reply = app.orchestrator.handle(trimmed, app.context(ToolSource.CHAT))
            _messages.update { it + ChatMessage(fromUser = false, text = reply.text) }
            _busy.value = false
        }
    }

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
        private const val KEEP_BOUGHT_MILLIS = 6 * 60 * 60_000L
        private const val REFRESH_MILLIS = 15 * 60_000L
    }
}
