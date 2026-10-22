package app.hearth.ai.llm

import app.hearth.core.tools.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Claude via the Messages API (raw HTTP: there is no official Anthropic SDK for Kotlin/Wasm).
 *
 * - API version 2026-10-21.
 * - Router: structured JSON output (`output_config.format`). Forced tool choice is not used: it is
 *   rejected by current models.
 * - Helper: tools with `tool_choice: auto`; the assistant `content` is replayed unchanged so thinking
 *   blocks stay valid across the tool loop.
 * - Effort is set explicitly to `low`: Hearth's tasks are short and the household waits for the reply.
 * - Models with server-side refusal fallback get `fallbacks: "default"` (not available for Haiku).
 */
class AnthropicProvider(
    override val config: ProviderConfig,
    private val transport: HttpTransport,
) : LlmProvider {

    private fun model(role: ModelRole) = if (role == ModelRole.ROUTER) config.routerModel else config.helperModel

    override suspend fun structured(role: ModelRole, system: String, user: String, schemaName: String, schema: JsonObject): JsonObject {
        val model = model(role)
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 4_000)
            put("system", system)
            putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", user) } }
            putJsonObject("output_config") {
                put("effort", "low")
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("schema", schema)
                }
            }
            fallbacks(model)
        }
        val response = post(body, model)
        if (response.str("stop_reason") == "refusal") throw LlmException(LlmException.Reason.REFUSED, "Declined by the model.")
        return parseJsonObjectText(textOf(response.arr("content")) ?: throw LlmException(LlmException.Reason.BAD_RESPONSE, "Empty answer."))
    }

    override suspend fun chat(role: ModelRole, system: String, messages: List<LlmMessage>, tools: List<ToolSpec>): LlmReply {
        val model = model(role)
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 16_000)
            put("system", system)
            putJsonArray("messages") { messages.forEach { add(toWire(it)) } }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { t ->
                        addJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", t.inputSchema)
                        }
                    }
                }
                putJsonObject("tool_choice") { put("type", "auto") }
            }
            putJsonObject("output_config") { put("effort", "low") }
            fallbacks(model)
        }
        val response = post(body, model)
        val content = response.arr("content") ?: JsonArray(emptyList())
        if (response.str("stop_reason") == "refusal") {
            return LlmReply(text = null, toolCalls = emptyList(), raw = content, refused = true)
        }
        val calls = content.mapNotNull { it.jsonObjectOrNull() }.filter { it.str("type") == "tool_use" }.map {
            LlmToolCall(it.str("id") ?: "", it.str("name") ?: "", it.obj("input") ?: JsonObject(emptyMap()))
        }
        return LlmReply(text = textOf(content), toolCalls = calls, raw = content)
    }

    private fun toWire(m: LlmMessage): JsonObject = when (m) {
        is LlmMessage.User -> buildJsonObject { put("role", "user"); put("content", m.text) }
        is LlmMessage.Assistant -> buildJsonObject {
            put("role", "assistant")
            // Replay Claude's own content blocks (thinking, text, tool_use) exactly as received.
            put("content", m.raw ?: JsonArray(listOf(buildJsonObject { put("type", "text"); put("text", m.text ?: "") })))
        }
        is LlmMessage.ToolResults -> buildJsonObject {
            put("role", "user")
            putJsonArray("content") {
                m.results.forEach { r ->
                    addJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", r.callId)
                        put("content", r.content)
                        if (r.isError) put("is_error", true)
                    }
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.fallbacks(model: String) {
        if (supportsFallback(model)) put("fallbacks", "default")
    }

    private suspend fun post(body: JsonObject, model: String): JsonObject {
        val headers = buildMap {
            put("x-api-key", config.apiKey)
            put("anthropic-version", API_VERSION)
            // Hearth also runs as a web app that calls the API straight from the browser with the user's own key.
            put("anthropic-dangerous-direct-browser-access", "true")
            if (supportsFallback(model)) put("anthropic-beta", FALLBACK_BETA)
        }
        val text = transport.send("https://api.anthropic.com/v1/messages", headers, body.toString())
        return runCatching { LlmJson.parseToJsonElement(text) as JsonObject }.getOrNull()
            ?: throw LlmException(LlmException.Reason.BAD_RESPONSE, "Unexpected answer from Claude.")
    }

    private fun textOf(content: JsonArray?): String? =
        content?.mapNotNull { it.jsonObjectOrNull() }
            ?.filter { it.str("type") == "text" }
            ?.mapNotNull { it.str("text") }
            ?.joinToString("")
            ?.ifBlank { null }

    companion object {
        const val API_VERSION = "2026-10-21"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /** Haiku has no server-side fallback; the parameter is only sent where it does something. */
        fun supportsFallback(model: String) = model.startsWith("claude-") && "haiku" !in model
    }
}
