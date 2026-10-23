package app.hearth.mcp

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

/** Something the person can fix (wrong token, missing permission); the message is shown as-is. */
class InstallException(message: String) : Exception(message)

/**
 * Installs (or updates) the relay on the family's own free Cloudflare account, once, with an API token
 * made from the "Edit Cloudflare Workers" template. Returns the relay's address, e.g.
 * https://hearth-relay.<account>.workers.dev. The token is used for these calls only and not stored.
 */
class RelayInstaller(private val client: () -> HttpClient = { HttpClient() }) {

    suspend fun install(token: String, accountId: String? = null, scriptName: String = "hearth-relay"): String {
        val http = client()
        try {
            val auth: io.ktor.client.request.HttpRequestBuilder.() -> Unit = { header(HttpHeaders.Authorization, "Bearer ${token.trim()}") }
            val account = accountId?.trim()?.ifEmpty { null } ?: findAccount(http, auth)

            // 1. Upload the Worker with its Durable Object. The migration only applies the first time.
            var upload = upload(http, auth, account, scriptName, withMigration = true)
            if (!upload.ok && upload.message.contains("migration", ignoreCase = true)) {
                upload = upload(http, auth, account, scriptName, withMigration = false)
            }
            if (!upload.ok) throw InstallException(upload.message)

            // 2. The account's workers.dev name (made once if the account has none yet).
            val subdomain = accountSubdomain(http, auth, account)

            // 3. Serve the Worker on workers.dev.
            val enable = http.post("$API/accounts/$account/workers/scripts/$scriptName/subdomain") {
                auth()
                contentType(ContentType.Application.Json)
                setBody("""{"enabled":true}""")
            }.parsed()
            if (!enable.ok) throw InstallException(enable.message)

            return "https://$scriptName.$subdomain.workers.dev"
        } finally {
            http.close()
        }
    }

    private suspend fun findAccount(http: HttpClient, auth: io.ktor.client.request.HttpRequestBuilder.() -> Unit): String {
        val res = http.get("$API/accounts") { auth() }.parsed()
        if (!res.ok) throw InstallException(res.message)
        val accounts = res.json["result"]?.jsonArray.orEmpty()
        return when (accounts.size) {
            0 -> throw InstallException("This token can't see any Cloudflare account. Use the “Edit Cloudflare Workers” template.")
            1 -> accounts.first().jsonObject["id"]!!.jsonPrimitive.content
            else -> throw InstallException("This token sees several accounts. Fill in the Account ID too.")
        }
    }

    private suspend fun upload(
        http: HttpClient,
        auth: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
        account: String,
        scriptName: String,
        withMigration: Boolean,
    ): Parsed {
        val metadata = buildJsonObject {
            put("main_module", "worker.js")
            put("compatibility_date", COMPATIBILITY_DATE)
            putJsonArray("bindings") {
                add(
                    buildJsonObject {
                        put("type", "durable_object_namespace")
                        put("name", "HOUSEHOLD")
                        put("class_name", "Household")
                    },
                )
            }
            if (withMigration) {
                putJsonObject("migrations") {
                    put("new_tag", "v1")
                    put("new_sqlite_classes", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("Household")) })
                }
            }
        }
        return http.put("$API/accounts/$account/workers/scripts/$scriptName") {
            auth()
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "metadata",
                            metadata.toString(),
                            Headers.build { append(HttpHeaders.ContentType, "application/json") },
                        )
                        append(
                            "worker.js",
                            RELAY_SCRIPT,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/javascript+module")
                                append(HttpHeaders.ContentDisposition, "filename=\"worker.js\"")
                            },
                        )
                    },
                ),
            )
        }.parsed()
    }

    private suspend fun accountSubdomain(http: HttpClient, auth: io.ktor.client.request.HttpRequestBuilder.() -> Unit, account: String): String {
        val existing = http.get("$API/accounts/$account/workers/subdomain") { auth() }.parsed()
        existing.json["result"]?.let { it as? JsonObject }?.get("subdomain")?.jsonPrimitive?.contentOrNull?.let { return it }
        val name = "hearth-" + (1..6).map { "abcdefghijkmnpqrstuvwxyz23456789"[Random.nextInt(32)] }.joinToString("")
        val made = http.put("$API/accounts/$account/workers/subdomain") {
            auth()
            contentType(ContentType.Application.Json)
            setBody("""{"subdomain":"$name"}""")
        }.parsed()
        if (!made.ok) throw InstallException(made.message)
        return made.json["result"]?.jsonObject?.get("subdomain")?.jsonPrimitive?.contentOrNull ?: name
    }

    private class Parsed(val ok: Boolean, val json: JsonObject, val message: String)

    private suspend fun HttpResponse.parsed(): Parsed {
        val json = runCatching { Json.parseToJsonElement(bodyAsText()).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        val ok = status.value in 200..299 && (json["success"]?.jsonPrimitive?.booleanOrNull != false)
        val message = json["errors"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            ?: "Cloudflare answered ${status.value}"
        return Parsed(ok, json, message)
    }

    companion object {
        const val API = "https://api.cloudflare.com/client/v4"
        const val COMPATIBILITY_DATE = "2025-09-01"
    }
}
