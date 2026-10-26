package app.hearth.mcp

import app.hearth.core.tools.system.JoinCode
import app.hearth.core.tools.system.SyncCloud
import app.hearth.core.tools.system.SyncCodeUnknown
import app.hearth.core.tools.system.SyncKeys
import app.hearth.core.tools.system.SyncNoRelay
import app.hearth.core.tools.system.SyncPulled
import app.hearth.core.tools.system.SyncPushed
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/**
 * The household's sync remote on the shared relay. Everything is sealed here, on the device, before it leaves:
 * the relay keeps a locked box, its version and a hash of the token, never the key. A join code never reaches
 * the relay either; it only sees a lookup and a box made from the code the slow way (PBKDF2).
 */
class RelaySync(
    /** The relay's https address, or null when this build has none. */
    private val relayUrl: suspend () -> String?,
    private val client: HttpClient = HttpClient(),
) : SyncCloud {
    override suspend fun available(): Boolean = relayUrl() != null

    override fun newKeys(): SyncKeys = SyncKeys(urlSafe(SyncCrypto.random(32)), urlSafe(SyncCrypto.random(32)), Base64.encode(SyncCrypto.random(32)))

    override suspend fun pull(keys: SyncKeys, since: Long?): SyncPulled {
        val res = client.get("${base()}/sync/${keys.space}" + (since?.let { "?since=$it" } ?: "")) { header("Authorization", "Bearer ${keys.token}") }
        val body = JSON.decodeFromString(Remote.serializer(), ok(res).bodyAsText())
        val snapshot = body.data?.let { SyncCrypto.open(Base64.decode(keys.key), Base64.decode(it), keys.space.encodeToByteArray()).decodeToString() }
        return SyncPulled(body.version, snapshot)
    }

    override suspend fun push(keys: SyncKeys, base: Long, snapshot: String): SyncPushed {
        val sealed = Base64.encode(SyncCrypto.seal(Base64.decode(keys.key), snapshot.encodeToByteArray(), keys.space.encodeToByteArray()))
        val res = client.post("${base()}/sync/${keys.space}") {
            header("Authorization", "Bearer ${keys.token}")
            contentType(ContentType.Application.Json)
            setBody(JSON.encodeToString(Push.serializer(), Push(base, sealed)))
        }
        if (res.status == HttpStatusCode.Conflict) return SyncPushed(JSON.decodeFromString(Remote.serializer(), res.bodyAsText()).version, accepted = false)
        return SyncPushed(JSON.decodeFromString(Remote.serializer(), ok(res).bodyAsText()).version, accepted = true)
    }

    override suspend fun offer(keys: SyncKeys): JoinCode {
        val code = CharArray(CODE_LENGTH) { ALPHABET[SyncCrypto.random(1)[0].toInt() and 31] }.concatToString()
        val box = Base64.encode(SyncCrypto.seal(boxKey(code), JSON.encodeToString(SyncKeys.serializer(), keys).encodeToByteArray(), JOIN_AAD))
        val res = client.post("${base()}/sync/offer") {
            contentType(ContentType.Application.Json)
            setBody(JSON.encodeToString(Offer.serializer(), Offer(lookup(code), box)))
        }
        return JoinCode(code, JSON.decodeFromString(Offered.serializer(), ok(res).bodyAsText()).expiresAt)
    }

    override suspend fun claim(code: String): SyncKeys {
        val clean = code.uppercase().filter { it in ALPHABET }
        if (clean.length != CODE_LENGTH) throw SyncCodeUnknown()
        val res = client.post("${base()}/sync/claim") {
            contentType(ContentType.Application.Json)
            setBody(JSON.encodeToString(Claim.serializer(), Claim(lookup(clean))))
        }
        if (res.status == HttpStatusCode.NotFound || res.status == HttpStatusCode.TooManyRequests) throw SyncCodeUnknown()
        val box = JSON.decodeFromString(Claimed.serializer(), ok(res).bodyAsText()).box
        val plain = runCatching { SyncCrypto.open(boxKey(clean), Base64.decode(box), JOIN_AAD) }.getOrElse { throw SyncCodeUnknown() }
        return JSON.decodeFromString(SyncKeys.serializer(), plain.decodeToString())
    }

    private suspend fun base(): String = relayUrl()?.trimEnd('/') ?: throw SyncNoRelay()

    private suspend fun lookup(code: String): String = urlSafe(SyncCrypto.derive(code, "hearth-join-lookup", ITERATIONS))
    private suspend fun boxKey(code: String): ByteArray = SyncCrypto.derive(code, "hearth-join-box", ITERATIONS)

    private fun ok(res: HttpResponse): HttpResponse = if (res.status.isSuccess()) res else throw IllegalStateException("Relay said ${res.status}")

    @Serializable private class Remote(val version: Long, val data: String? = null)
    @Serializable private class Push(val base: Long, val data: String)
    @Serializable private class Offer(val lookup: String, val box: String)
    @Serializable private class Offered(val expiresAt: Long)
    @Serializable private class Claim(val lookup: String)
    @Serializable private class Claimed(val box: String)

    companion object {
        /** No 0/O, no 1/I: easy to read off a tablet across the room. 32 letters, 8 of them: 40 bits. */
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val CODE_LENGTH = 8
        private const val ITERATIONS = 100_000
        private val JOIN_AAD = "hearth-join".encodeToByteArray()
        private val JSON = Json { ignoreUnknownKeys = true }

        private fun urlSafe(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes).trimEnd('=')
    }
}
