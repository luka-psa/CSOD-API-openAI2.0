package app.hearth.di

import app.hearth.ai.Orchestrator
import app.hearth.core.data.HearthDatabase
import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.tools.food.shopIdFor
import app.hearth.core.model.Lang
import app.hearth.core.tools.HearthTools
import app.hearth.mcp.McpServer
import app.hearth.mcp.RelayClient
import app.hearth.mcp.RelayInstaller
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
    val stock = StockRepository(db)
    val week = WeekRepository(db)
    val settings = SettingsRepository(db)
    val tools = HearthTools(shopping, backlog, board, stock, week, settings)
    val orchestrator = Orchestrator(tools.registry)

    /** The family's ChatGPT or Claude reaches the same tools here, through the relay. */
    val mcp = McpServer(tools.registry) { context(ToolSource.MCP) }
    val relay = RelayClient(mcp, appScope)
    val installer = RelayInstaller()

    fun context(source: ToolSource) = ToolContext(source = source, lang = lang)

    init {
        appScope.launch {
            shopping.ensureDefaultShops(lang)
            stock.ensureDefaults(lang) { shopping.shopIdFor(it) }
        }
        // Connected only while it's switched on; the client reconnects by itself.
        appScope.launch { settings.connection.collect { c -> relay.connect(if (c.enabled) c.tabletUrl else null) } }
    }
}
