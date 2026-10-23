package app.hearth.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The one-time install on the family's Cloudflare account, against a scripted Cloudflare API. */
class RelayInstallerTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `finds the account, uploads the worker with its Durable Object, enables workers dev`() = runTest {
        val seen = mutableListOf<String>()
        var uploadBody = ""
        var uploads = 0
        val engine = MockEngine { req ->
            seen += "${req.method.value} ${req.url.encodedPath}"
            assertEquals("Bearer tok", req.headers[HttpHeaders.Authorization])
            when {
                req.url.encodedPath == "/client/v4/accounts" -> respond("""{"success":true,"result":[{"id":"acc1"}]}""", HttpStatusCode.OK, json)
                req.method == HttpMethod.Put && req.url.encodedPath.endsWith("/scripts/hearth-relay") -> {
                    uploads++
                    uploadBody = String(req.body.toByteArray())
                    if (uploads == 1) respond("""{"success":false,"errors":[{"code":10079,"message":"migration tag precondition failed"}]}""", HttpStatusCode.BadRequest, json)
                    else respond("""{"success":true,"result":{}}""", HttpStatusCode.OK, json)
                }
                req.url.encodedPath.endsWith("/workers/subdomain") -> respond("""{"success":true,"result":{"subdomain":"gezin"}}""", HttpStatusCode.OK, json)
                req.url.encodedPath.endsWith("/hearth-relay/subdomain") -> respond("""{"success":true,"result":{"enabled":true}}""", HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
        val url = RelayInstaller { HttpClient(engine) }.install("tok")
        assertEquals("https://hearth-relay.gezin.workers.dev", url)
        assertEquals(
            listOf(
                "GET /client/v4/accounts",
                "PUT /client/v4/accounts/acc1/workers/scripts/hearth-relay",
                "PUT /client/v4/accounts/acc1/workers/scripts/hearth-relay",
                "GET /client/v4/accounts/acc1/workers/subdomain",
                "POST /client/v4/accounts/acc1/workers/scripts/hearth-relay/subdomain",
            ),
            seen,
        )
        // The retry drops the migration (already applied); the script itself is the relay.
        assertTrue("durable_object_namespace" in uploadBody)
        assertTrue("new_sqlite_classes" !in uploadBody)
        assertTrue("export class Household" in uploadBody)
        assertTrue("application/javascript+module" in uploadBody)
    }

    @Test
    fun `a bad token says why`() = runTest {
        val engine = MockEngine { respond("""{"success":false,"errors":[{"code":10000,"message":"Authentication error"}]}""", HttpStatusCode.Forbidden, json) }
        val e = assertFailsWith<InstallException> { RelayInstaller { HttpClient(engine) }.install("bad", "acc1") }
        assertEquals("Authentication error", e.message)
    }
}
