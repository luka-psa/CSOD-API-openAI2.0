package app.hearth.mcp

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How the tablet's link to the relay is doing. */
enum class RelayStatus { OFF, CONNECTING, ONLINE, RETRYING }

/** An AI app (ChatGPT, Claude, …) that was linked with a pairing code. */
@Serializable
data class LinkedApp(val id: String, val name: String, val since: Long, val lastUsed: Long? = null)

/** The code to type on the Hearth page that ChatGPT or Claude opens. Valid until [expiresAt] (epoch ms). */
data class PairingCode(val code: String, val expiresAt: Long) {
    /** "123 456": easier to read across the room. */
    val spaced: String get() = if (code.length == 6) code.take(3) + " " + code.drop(3) else code
}

@Serializable
private data class RelayEnvelope(
    val id: String? = null,
    val body: String? = null,
    val ping: Boolean? = null,
    val pong: Boolean? = null,
    val type: String? = null,
    val code: String? = null,
    val expiresAt: Long? = null,
    val app: String? = null,
    val apps: List<LinkedApp>? = null,
)

/**
 * Keeps one outgoing WebSocket to the relay open while the connection is on, answers each forwarded MCP
 * request with [McpServer], and reconnects by itself after Wi-Fi drops or a reboot (backing off gently).
 * Also asks the relay for pairing codes and keeps the list of linked AI apps.
 */
class RelayClient(
    private val server: McpServer,
    private val scope: CoroutineScope,
    private val client: () -> HttpClient = { HttpClient { install(WebSockets) } },
) {
    private val _status = MutableStateFlow(RelayStatus.OFF)
    val status: StateFlow<RelayStatus> = _status.asStateFlow()

    private val _apps = MutableStateFlow<List<LinkedApp>>(emptyList())
    val apps: StateFlow<List<LinkedApp>> = _apps.asStateFlow()

    private val _pairing = MutableStateFlow<PairingCode?>(null)
    val pairing: StateFlow<PairingCode?> = _pairing.asStateFlow()

    /** Name of an app the moment it gets linked ("Claude"), for a short "connected" message. */
    private val _linked = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val linked: SharedFlow<String> = _linked.asSharedFlow()

    private val outbox = Channel<RelayEnvelope>(Channel.BUFFERED)
    private var job: Job? = null
    private var url: String? = null

    /** Connects to [tabletUrl] (wss://…/connect/<household>?key=…), or disconnects when null. */
    fun connect(tabletUrl: String?) {
        if (tabletUrl == url && job?.isActive == true) return
        job?.cancel()
        url = tabletUrl
        _pairing.value = null
        if (tabletUrl == null) {
            _status.value = RelayStatus.OFF
            _apps.value = emptyList()
            return
        }
        job = scope.launch { loop(tabletUrl) }
    }

    /** Asks the relay for a fresh pairing code (arrives in [pairing]). */
    fun requestPairingCode() {
        outbox.trySend(RelayEnvelope(type = "pair"))
    }

    fun hidePairingCode() {
        _pairing.value = null
    }

    /** Unlinks one app, or every app when [appId] is null. Their tokens stop working right away. */
    fun unlink(appId: String?) {
        outbox.trySend(RelayEnvelope(type = "unlink", app = appId ?: "all"))
    }

    private suspend fun CoroutineScope.loop(tabletUrl: String) {
        val http = client()
        var backoff = 2_000L
        try {
            while (isActive) {
                _status.value = RelayStatus.CONNECTING
                try {
                    http.webSocket(tabletUrl) {
                        _status.value = RelayStatus.ONLINE
                        backoff = 2_000L
                        suspend fun send(e: RelayEnvelope) = send(Frame.Text(JSON.encodeToString(RelayEnvelope.serializer(), e)))
                        // A small heartbeat so routers and the relay keep the line open.
                        val heartbeat = launch {
                            while (isActive) {
                                delay(25_000)
                                send(RelayEnvelope(ping = true))
                            }
                        }
                        val sender = launch { for (e in outbox) send(e) }
                        try {
                            for (frame in incoming) {
                                if (frame !is Frame.Text) continue
                                val envelope = runCatching { JSON.decodeFromString(RelayEnvelope.serializer(), frame.readText()) }.getOrNull() ?: continue
                                when (envelope.type) {
                                    "apps" -> _apps.value = envelope.apps.orEmpty()
                                    "code" -> if (envelope.code != null) _pairing.value = PairingCode(envelope.code, envelope.expiresAt ?: 0L)
                                    "linked" -> {
                                        _pairing.value = null
                                        envelope.app?.let { _linked.tryEmit(it) }
                                    }
                                    else -> {
                                        val id = envelope.id ?: continue
                                        // Each request is answered on its own, so a slow tool doesn't hold up the rest.
                                        launch {
                                            val reply = runCatching { server.handle(envelope.body ?: "") }.getOrNull() ?: ""
                                            send(RelayEnvelope(id = id, body = reply))
                                        }
                                    }
                                }
                            }
                        } finally {
                            heartbeat.cancel()
                            sender.cancel()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Offline, relay busy, tablet asleep: try again in a moment.
                }
                _status.value = RelayStatus.RETRYING
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        } finally {
            http.close()
        }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}
