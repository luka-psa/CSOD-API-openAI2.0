package app.hearth.di

import app.hearth.ai.Orchestrator
import app.hearth.core.data.HearthDatabase
import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.model.Lang
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-wired dependencies (no DI framework, to stay light on low-spec tablets).
 * Each platform opens the database its own way and passes it in.
 *
 * The tablet's own chat runs locally (rules + keyword sorter). The family's AI (ChatGPT or Claude,
 * on their own subscription) reaches the same tools over MCP.
 */
class AppContainer(db: HearthDatabase, val lang: Lang) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val shopping = ShoppingRepository(db)
    val backlog = BacklogRepository(db)
    val board = BoardRepository(db)
    val tools = HearthTools(shopping, backlog, board)
    val orchestrator = Orchestrator(tools.registry)

    fun context(source: ToolSource) = ToolContext(source = source, lang = lang)

    init {
        appScope.launch { shopping.ensureDefaultShops(lang) }
    }
}
