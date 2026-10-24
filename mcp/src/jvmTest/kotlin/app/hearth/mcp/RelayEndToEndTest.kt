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
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole path: the tablet connects to a running relay and shows a pairing code, an "AI app" logs in
 * with it (OAuth + PKCE) and calls a tool, Hearth answers. Runs only when HEARTH_RELAY_URL points at a
 * relay (e.g. Miniflare).
 */
class RelayEndToEndTest {
    @Test
    fun `an AI app links with a pairing code and its MCP call reaches the tablet`() = runBlocking {
        val relay = System.getenv("HEARTH_RELAY_URL") ?: return@runBlocking
        val db = inMemoryHearthDatabase()
        val shopping = ShoppingRepository(db)
        shopping.ensureDefaultShops(Lang.NL)
        val tools = HearthTools(shopping, BacklogRepository(db), BoardRepository(db), StockRepository(db), WeekRepository(db), SettingsRepository(db), app.hearth.core.data.week.MeetingRepository(db))
        val server = McpServer(tools.registry) { ToolContext(ToolSource.MCP, Lang.NL) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = RelayClient(server, scope)
        client.connect(relay.replaceFirst("http", "ws") + "/connect/e2e" + SettingsRepository.secret() + "?key=" + SettingsRepository.secret())
        withTimeout(10_000) { client.status.first { it == RelayStatus.ONLINE } }
        client.requestPairingCode()
        val code = withTimeout(10_000) { client.pairing.first { it != null }!! }

        val http = HttpClient { followRedirects = false }
        val redirect = "https://claude.ai/api/mcp/auth_callback"
        val reg = http.post("$relay/register") {
            header("content-type", "application/json")
            setBody("""{"client_name":"Claude","redirect_uris":["$redirect"]}""")
        }.bodyAsText()
        val clientId = Json.parseToJsonElement(reg).jsonObject["client_id"]!!.jsonPrimitive.content
        val verifier = SettingsRepository.secret()
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val authorize = http.submitForm("$relay/authorize", Parameters.build {
            append("response_type", "code"); append("client_id", clientId); append("redirect_uri", redirect)
            append("state", "x"); append("code_challenge", challenge); append("code_challenge_method", "S256")
            append("pairing_code", code.spaced)
        })
        val authCode = Url(authorize.headers["location"] ?: error("${authorize.status} ${authorize.bodyAsText()}")).parameters["code"]!!
        val tokens = http.submitForm("$relay/token", Parameters.build {
            append("grant_type", "authorization_code"); append("code", authCode); append("client_id", clientId)
            append("redirect_uri", redirect); append("code_verifier", verifier)
        }).bodyAsText()
        val access = Json.parseToJsonElement(tokens).jsonObject["access_token"]!!.jsonPrimitive.content
        withTimeout(10_000) { client.apps.first { it.size == 1 } }
        assertEquals("Claude", client.apps.value.single().name)

        val reply = http.post("$relay/mcp") {
            header("authorization", "Bearer $access")
            setBody("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"add_shopping_item","arguments":{"name":"koffie"}}}""")
        }.bodyAsText()
        assertTrue("Koffie staat op de lijst" in reply, reply)
        assertEquals(listOf("Koffie"), shopping.openItems().map { it.name })

        client.unlink(null)
        withTimeout(10_000) { client.apps.first { it.isEmpty() } }

        http.close()
        scope.cancel()
        db.close()
    }
}
