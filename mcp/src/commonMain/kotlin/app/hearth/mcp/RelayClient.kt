package app.hearth.mcp

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How the tablet's link to the relay is doing. */
enum class RelayStatus { OFF, CONNECTING, ONLINE, RETRYING }

@Serializable
private data class RelayEnvelope(val id: String? = null, val body: String? = null, val ping: Boolean? = null, val pong: Boolean? = null)

/**
 * Keeps one outgoing WebSocket to the relay open while the connection is on, answers each forwarded MCP
 * request with [McpServer], and reconnects by itself after Wi-Fi drops or a reboot (backing off gently).
 */
class RelayClient(
    private val server: McpServer,
    private val scope: CoroutineScope,
    private val client: () -> HttpClient = { HttpClient { install(WebSockets) } },
) {
    private val _status = MutableStateFlow(RelayStatus.OFF)
    val status: StateFlow<RelayStatus> = _status.asStateFlow()

    private var job: Job? = null
    private var url: String? = null

    /** Connects to [tabletUrl] (wss://…/connect/<secret>?key=…), or disconnects when null. */
    fun connect(tabletUrl: String?) {
        if (tabletUrl == url && job?.isActive == true) return
        job?.cancel()
        url = tabletUrl
        if (tabletUrl == null) {
            _status.value = RelayStatus.OFF
            return
        }
        job = scope.launch { loop(tabletUrl) }
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
                        // A small heartbeat so routers and the relay keep the line open.
                        val heartbeat = launch {
                            while (isActive) {
                                delay(25_000)
                                send(Frame.Text(JSON.encodeToString(RelayEnvelope.serializer(), RelayEnvelope(ping = true))))
                            }
                        }
                        try {
                            for (frame in incoming) {
                                if (frame !is Frame.Text) continue
                                val envelope = runCatching { JSON.decodeFromString(RelayEnvelope.serializer(), frame.readText()) }.getOrNull() ?: continue
                                val id = envelope.id ?: continue
                                // Each request is answered on its own, so a slow tool doesn't hold up the rest.
                                launch {
                                    val reply = runCatching { server.handle(envelope.body ?: "") }.getOrNull() ?: ""
                                    send(Frame.Text(JSON.encodeToString(RelayEnvelope.serializer(), RelayEnvelope(id = id, body = reply))))
                                }
                            }
                        } finally {
                            heartbeat.cancel()
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
