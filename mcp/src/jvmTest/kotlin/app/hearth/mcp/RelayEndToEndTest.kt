package app.hearth.mcp

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.Lang
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolSource
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole path: an MCP client posts to a running relay, the relay forwards to this "tablet" over a
 * WebSocket, Hearth's tools answer. Runs only when HEARTH_RELAY_URL points at a relay (e.g. Miniflare).
 */
class RelayEndToEndTest {
    @Test
    fun `an MCP call goes through the relay to the tablet and back`() = runBlocking {
        val relay = System.getenv("HEARTH_RELAY_URL") ?: return@runBlocking
        val db = inMemoryHearthDatabase()
        val shopping = ShoppingRepository(db)
        shopping.ensureDefaultShops(Lang.NL)
        val tools = HearthTools(shopping, BacklogRepository(db), BoardRepository(db), StockRepository(db), WeekRepository(db), SettingsRepository(db))
        val server = McpServer(tools.registry) { ToolContext(ToolSource.MCP, Lang.NL) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secret = "e2e" + "a".repeat(40)
        val client = RelayClient(server, scope)
        client.connect(relay.replaceFirst("http", "ws") + "/connect/$secret?key=" + "b".repeat(40))
        withTimeout(10_000) { client.status.first { it == RelayStatus.ONLINE } }
        delay(200)

        val http = HttpClient()
        val reply = http.post("$relay/mcp/$secret") {
            setBody("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"add_shopping_item","arguments":{"name":"koffie"}}}""")
        }.bodyAsText()
        assertTrue("Koffie staat op de lijst" in reply, reply)
        assertEquals(listOf("Koffie"), shopping.openItems().map { it.name })

        http.close()
        scope.cancel()
        db.close()
    }
}
