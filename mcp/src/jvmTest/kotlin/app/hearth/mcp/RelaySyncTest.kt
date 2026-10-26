package app.hearth.mcp

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.backup.BackupRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.sync.SyncRepository
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolSource
import app.hearth.core.tools.backlog.AddToBacklogArgs
import app.hearth.core.tools.food.AddShoppingItemArgs
import app.hearth.core.tools.system.JoinSyncArgs
import app.hearth.core.tools.system.NoArgs
import app.hearth.core.tools.system.SyncCodeUnknown
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The real sync client and its sealing, against a relay that behaves like relay/ (and shows what it sees). */
class RelaySyncTest {
    /** What the relay keeps: versions, sealed boxes and offers. Everything it was sent is in [seen]. */
    private class FakeRelay {
        var version = 0L
        var data: String? = null
        var token: String? = null
        val offers = mutableMapOf<String, String>()
        val seen = StringBuilder()

        fun engine() = MockEngine { req -> handle(req) }

        private suspend fun io.ktor.client.engine.mock.MockRequestHandleScope.handle(req: HttpRequestData): io.ktor.client.request.HttpResponseData {
            val body = (req.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString() ?: (req.body as? io.ktor.http.content.TextContent)?.text ?: ""
            seen.append(body).append(req.headers["Authorization"])
            val json = headersOf("content-type", "application/json")
            val path = req.url.encodedPath
            return when {
                path == "/sync/offer" -> {
                    val o = Json.parseToJsonElement(body).jsonObject
                    offers[o["lookup"]!!.jsonPrimitive.content] = o["box"]!!.jsonPrimitive.content
                    respond("""{"expiresAt":600000}""", headers = json)
                }
                path == "/sync/claim" -> {
                    val box = offers.remove(Json.parseToJsonElement(body).jsonObject["lookup"]!!.jsonPrimitive.content)
                    if (box == null) respond("Unknown code", HttpStatusCode.NotFound) else respond("""{"box":"$box"}""", headers = json)
                }
                path.startsWith("/sync/") -> {
                    val auth = req.headers["Authorization"]!!
                    if (token != null && token != auth) return respond("Not this household", HttpStatusCode.Forbidden)
                    if (req.method == HttpMethod.Get) {
                        val since = req.url.parameters["since"]?.toLong()
                        respond(if (since == version || data == null) """{"version":$version}""" else """{"version":$version,"data":"$data"}""", headers = json)
                    } else {
                        val push = Json.parseToJsonElement(body).jsonObject
                        if (push["base"]!!.jsonPrimitive.long != version) return respond("""{"version":$version}""", HttpStatusCode.Conflict, json)
                        token = auth
                        version++
                        data = push["data"]!!.jsonPrimitive.content
                        respond("""{"version":$version}""", headers = json)
                    }
                }
                else -> respond("Not found", HttpStatusCode.NotFound)
            }
        }
    }

    private val relay = FakeRelay()
    private val ctx = ToolContext(ToolSource.UI, Lang.NL)

    private inner class Device {
        val db = inMemoryHearthDatabase()
        val shopping = ShoppingRepository(db)
        val backlog = BacklogRepository(db)
        val cloud = RelaySync({ "https://relay.example" }, HttpClient(relay.engine()))
        val tools = HearthTools(
            shopping, backlog, BoardRepository(db), StockRepository(db), WeekRepository(db), SettingsRepository(db), MeetingRepository(db),
            BackupRepository(db), sync = SyncRepository(db, BackupRepository(db)), syncCloud = cloud,
        )
    }

    @Test
    fun `the relay only ever sees sealed boxes, and a code opens the household once`() = runTest {
        val tablet = Device()
        val phone = Device()
        tablet.shopping.ensureDefaultShops(Lang.NL)
        tablet.tools.registry.run(tablet.tools.addShoppingItem, AddShoppingItemArgs("speculoos"), ctx)
        tablet.tools.registry.run(tablet.tools.addToBacklog, AddToBacklogArgs("Verzekering bellen", Section.ADMIN), ctx)

        val code = tablet.tools.registry.run(tablet.tools.startSync!!, NoArgs(), ctx)
        assertTrue(Regex("^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$").matches(code.shown), code.shown)
        phone.tools.registry.run(phone.tools.joinSync!!, JoinSyncArgs(code.shown.lowercase()), ctx, confirmed = true)
        assertEquals(listOf("Speculoos"), phone.shopping.openItems().map { it.name })

        phone.tools.registry.run(phone.tools.addShoppingItem, AddShoppingItemArgs("choco"), ctx)
        phone.tools.registry.run(phone.tools.syncNow!!, NoArgs(), ctx)
        tablet.tools.registry.run(tablet.tools.syncNow!!, NoArgs(), ctx)
        assertEquals(listOf("Choco", "Speculoos"), tablet.shopping.openItems().map { it.name }.sorted())

        // Nothing readable went over the wire: not the items, not the key, not the code.
        val keys = tablet.tools.syncEngine!!.keys()!!
        for (secret in listOf("speculoos", "Speculoos", "choco", "Verzekering", keys.key, code.code)) {
            assertFalse(secret in relay.seen, "the relay saw $secret")
        }
        // The code is spent.
        assertFailsWith<SyncCodeUnknown> { phone.cloud.claim(code.code) }
        assertFailsWith<SyncCodeUnknown> { phone.cloud.claim("NOPE") }
        tablet.db.close()
        phone.db.close()
    }

    @Test
    fun `a box sealed for one household doesn't open with another key or in another place`() = runBlocking {
        val key = SyncCrypto.random(32)
        val box = SyncCrypto.seal(key, "melk".encodeToByteArray(), "space-a".encodeToByteArray())
        assertEquals("melk", SyncCrypto.open(key, box, "space-a".encodeToByteArray()).decodeToString())
        assertFailsWith<Exception> { SyncCrypto.open(SyncCrypto.random(32), box, "space-a".encodeToByteArray()) }
        assertFailsWith<Exception> { SyncCrypto.open(key, box, "space-b".encodeToByteArray()) }
        // The same code always gives the same lookup; another code another one.
        assertTrue(SyncCrypto.derive("ABCD2345", "salt", 1000).contentEquals(SyncCrypto.derive("ABCD2345", "salt", 1000)))
        assertFalse(SyncCrypto.derive("ABCD2345", "salt", 1000).contentEquals(SyncCrypto.derive("ABCD2346", "salt", 1000)))
    }

    @Test
    fun `two devices against a real relay`() = runBlocking {
        // Runs when HEARTH_E2E_RELAY_URL points at a relay (e.g. Miniflare), like the MCP end-to-end test.
        val url = System.getenv("HEARTH_E2E_RELAY_URL") ?: return@runBlocking
        fun device() = inMemoryHearthDatabase().let { db ->
            Triple(db, ShoppingRepository(db), HearthTools(
                ShoppingRepository(db), BacklogRepository(db), BoardRepository(db), StockRepository(db), WeekRepository(db), SettingsRepository(db),
                MeetingRepository(db), BackupRepository(db), sync = SyncRepository(db, BackupRepository(db)), syncCloud = RelaySync({ url }),
            ))
        }
        val (db1, shop1, tablet) = device()
        val (db2, shop2, phone) = device()
        shop1.ensureDefaultShops(Lang.NL)
        tablet.registry.run(tablet.addShoppingItem, AddShoppingItemArgs("koffie"), ctx)
        val code = tablet.registry.run(tablet.startSync!!, NoArgs(), ctx)
        phone.registry.run(phone.joinSync!!, JoinSyncArgs(code.shown), ctx, confirmed = true)
        phone.registry.run(phone.addShoppingItem, AddShoppingItemArgs("thee"), ctx)
        phone.registry.run(phone.syncNow!!, NoArgs(), ctx)
        tablet.registry.run(tablet.syncNow!!, NoArgs(), ctx)
        assertEquals(listOf("Koffie", "Thee"), shop1.openItems().map { it.name }.sorted())
        assertEquals(listOf("Koffie", "Thee"), shop2.openItems().map { it.name }.sorted())
        db1.close()
        db2.close()
    }
}

private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.content
