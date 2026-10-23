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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What ChatGPT or Claude sees and can do through Hearth's MCP server. */
class McpServerTest {
    private val db = inMemoryHearthDatabase()
    private val shopping = ShoppingRepository(db)
    private val tools = HearthTools(shopping, BacklogRepository(db), BoardRepository(db), StockRepository(db), WeekRepository(db), SettingsRepository(db))
    private val server = McpServer(tools.registry) { ToolContext(ToolSource.MCP, Lang.NL) }

    @BeforeTest
    fun setUp() = runTest { shopping.ensureDefaultShops(Lang.NL) }

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun call(id: Int, method: String, params: String = "{}"): JsonObject {
        val reply = server.handle("""{"jsonrpc":"2.0","id":$id,"method":"$method","params":$params}""")!!
        return Json.parseToJsonElement(reply).jsonObject
    }

    private suspend fun tool(name: String, args: String): JsonObject =
        call(3, "tools/call", """{"name":"$name","arguments":$args}""")["result"]!!.jsonObject

    private fun JsonObject.text() = this["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content

    @Test
    fun `initialize agrees on a protocol version and introduces Hearth`() = runTest {
        val result = call(1, "initialize", """{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}""")["result"]!!.jsonObject
        assertEquals("2025-06-18", result["protocolVersion"]!!.jsonPrimitive.content)
        assertEquals("hearth", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue("tools" in result["capabilities"]!!.jsonObject)
        val unknown = call(2, "initialize", """{"protocolVersion":"1999-01-01"}""")["result"]!!.jsonObject
        assertEquals(McpServer.SUPPORTED_VERSIONS.first(), unknown["protocolVersion"]!!.jsonPrimitive.content)
        assertNull(server.handle("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
    }

    @Test
    fun `tools list has the household tools with schemas, not the tablet-only ones`() = runTest {
        val list = call(1, "tools/list")["result"]!!.jsonObject["tools"]!!.jsonArray.map { it.jsonObject }
        val names = list.map { it["name"]!!.jsonPrimitive.content }
        assertTrue("add_shopping_item" in names)
        assertTrue("plan_meal" in names)
        assertTrue(McpServer.CONFIRM in names)
        assertTrue("save_connection" !in names)
        val add = list.first { it["name"]!!.jsonPrimitive.content == "add_shopping_item" }
        assertEquals("object", add["inputSchema"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val listing = list.first { it["name"]!!.jsonPrimitive.content == "list_shopping_items" }
        assertTrue(listing["annotations"]!!.jsonObject["readOnlyHint"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `calls run the same tools as the tablet`() = runTest {
        assertEquals("Melk staat op de lijst · Supermarkt", tool("add_shopping_item", """{"name":"melk"}""").text())
        assertEquals(listOf("Melk"), shopping.openItems().map { it.name })
        val failed = tool("mark_bought", """{"names":["kaas"]}""")
        assertTrue(failed["isError"]!!.jsonPrimitive.boolean)
        assertEquals(listOf("Melk staat op de lijst · Supermarkt"), server.activity.value)
    }

    @Test
    fun `destructive calls wait for a yes, from the AI chat or the tablet`() = runTest {
        tool("add_shopping_item", """{"name":"melk"}""")
        tool("add_shopping_item", """{"name":"kaas"}""")
        val ask = tool("remove_shopping_item", """{"names":["melk"]}""").text()
        assertTrue(ask.startsWith("Bevestiging nodig: melk van de lijst verwijderen?"), ask)
        assertEquals(2, shopping.openItems().size)

        // Yes in the AI chat.
        val id = server.pending.value.single().id
        assertEquals("Verwijderd: Melk", tool(McpServer.CONFIRM, """{"id":"$id"}""").text())
        assertEquals(listOf("Kaas"), shopping.openItems().map { it.name })

        // No on the tablet.
        tool("remove_shopping_item", """{"names":["kaas"]}""")
        server.cancel(server.pending.value.single().id)
        assertTrue(server.pending.value.isEmpty())
        assertEquals(listOf("Kaas"), shopping.openItems().map { it.name })
    }

    @Test
    fun `batches, pings and unknown methods`() = runTest {
        val batch = server.handle("""[{"jsonrpc":"2.0","id":1,"method":"ping"},{"jsonrpc":"2.0","method":"notifications/x"},{"jsonrpc":"2.0","id":2,"method":"nope"}]""")!!
        val replies = Json.parseToJsonElement(batch).jsonArray.map { it.jsonObject }
        assertEquals(2, replies.size)
        assertEquals(-32601, replies[1]["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
        assertEquals(-32700, Json.parseToJsonElement(server.handle("not json")!!).jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt())
    }
}
