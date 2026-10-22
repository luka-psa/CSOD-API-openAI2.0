package app.hearth.ai.llm

import app.hearth.core.tools.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** OpenAI Chat Completions, and xAI (Grok), which speaks the same API. */
class OpenAiCompatibleProvider(
    override val config: ProviderConfig,
    private val transport: HttpTransport,
    private val flavor: Flavor,
) : LlmProvider {

    enum class Flavor(val baseUrl: String) {
        OPENAI("https://api.openai.com/v1"),
        XAI("https://api.x.ai/v1"),
    }

    private fun model(role: ModelRole) = if (role == ModelRole.ROUTER) config.routerModel else config.helperModel

    override suspend fun structured(role: ModelRole, system: String, user: String, schemaName: String, schema: JsonObject): JsonObject {
        val body = buildJsonObject {
            put("model", model(role))
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                addJsonObject { put("role", "user"); put("content", user) }
            }
            putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", schemaName)
                    put("strict", true)
                    put("schema", schema)
                }
            }
            limits(role, maxTokens = 2_000)
        }
        val message = post(body)
        message.str("refusal")?.let { throw LlmException(LlmException.Reason.REFUSED, it) }
        return parseJsonObjectText(message.str("content") ?: throw LlmException(LlmException.Reason.BAD_RESPONSE, "Empty answer."))
    }

    override suspend fun chat(role: ModelRole, system: String, messages: List<LlmMessage>, tools: List<ToolSpec>): LlmReply {
        val body = buildJsonObject {
            put("model", model(role))
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                messages.forEach { m -> toWire(m).forEach { add(it) } }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { t ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", t.inputSchema)
                            }
                        }
                    }
                }
                put("tool_choice", "auto")
            }
            limits(role, maxTokens = 4_000)
        }
        val message = post(body)
        val refusal = message.str("refusal")
        val calls = message.arr("tool_calls").orEmpty().mapNotNull { it.jsonObjectOrNull() }.map { call ->
            val fn = call.obj("function") ?: JsonObject(emptyMap())
            val args = fn.str("arguments")?.let { runCatching { parseJsonObjectText(it) }.getOrNull() } ?: JsonObject(emptyMap())
            LlmToolCall(call.str("id") ?: "", fn.str("name") ?: "", args)
        }
        return LlmReply(text = refusal ?: message.str("content"), toolCalls = calls, raw = null, refused = refusal != null)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.limits(role: ModelRole, maxTokens: Int) {
        when (flavor) {
            Flavor.OPENAI -> {
                put("max_completion_tokens", maxTokens)
                // Hearth's tasks are small: sorting a sentence, picking a tool.
                put("reasoning_effort", "low")
            }
            Flavor.XAI -> put("max_tokens", maxTokens)
        }
    }

    private fun toWire(m: LlmMessage): List<JsonObject> = when (m) {
        is LlmMessage.User -> listOf(buildJsonObject { put("role", "user"); put("content", m.text) })
        is LlmMessage.Assistant -> listOf(
            buildJsonObject {
                put("role", "assistant")
                put("content", m.text?.let(::JsonPrimitive) ?: JsonNull)
                if (m.toolCalls.isNotEmpty()) {
                    putJsonArray("tool_calls") {
                        m.toolCalls.forEach { c ->
                            addJsonObject {
                                put("id", c.id)
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", c.name)
                                    put("arguments", c.arguments.toString())
                                }
                            }
                        }
                    }
                }
            },
        )
        is LlmMessage.ToolResults -> m.results.map { r ->
            buildJsonObject {
                put("role", "tool")
                put("tool_call_id", r.callId)
                put("content", if (r.isError) "Error: ${r.content}" else r.content)
            }
        }
    }

    private suspend fun post(body: JsonObject): JsonObject {
        val text = transport.send(
            "${flavor.baseUrl}/chat/completions",
            mapOf("Authorization" to "Bearer ${config.apiKey}"),
            body.toString(),
        )
        val root = runCatching { LlmJson.parseToJsonElement(text) as JsonObject }.getOrNull()
        return root?.arr("choices")?.firstOrNull()?.jsonObjectOrNull()?.obj("message")
            ?: throw LlmException(LlmException.Reason.BAD_RESPONSE, "Unexpected answer from ${flavor.name}.")
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: buildJsonArray { }
