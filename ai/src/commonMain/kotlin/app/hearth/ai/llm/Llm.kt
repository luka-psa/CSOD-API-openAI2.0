package app.hearth.ai.llm

import app.hearth.core.tools.ToolSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The cloud providers Hearth can use. Users bring their own API key. */
enum class ProviderKind(val label: String) {
    OPENAI("ChatGPT (OpenAI)"),
    ANTHROPIC("Claude (Anthropic)"),
    XAI("Grok (xAI)"),
}

/**
 * Two models per provider, matching the two stages of the orchestrator:
 * a small, cheap [routerModel] that only sorts a message into a section, and a [helperModel]
 * that does the work in that section with its tools and writes the reply.
 */
@Serializable
data class ProviderConfig(
    val kind: ProviderKind,
    val apiKey: String,
    val routerModel: String = defaultRouterModel(kind),
    val helperModel: String = defaultHelperModel(kind),
) {
    companion object {
        fun defaultRouterModel(kind: ProviderKind) = when (kind) {
            ProviderKind.OPENAI -> "gpt-5.4-nano"
            ProviderKind.ANTHROPIC -> "claude-haiku-5-5"
            ProviderKind.XAI -> "grok-4.3"
        }

        fun defaultHelperModel(kind: ProviderKind) = when (kind) {
            ProviderKind.OPENAI -> "gpt-5.4-mini"
            ProviderKind.ANTHROPIC -> "claude-opus-5-5"
            ProviderKind.XAI -> "grok-4.3"
        }
    }
}

enum class ModelRole { ROUTER, HELPER }

/** Provider-neutral conversation used by the helper's tool loop. */
sealed interface LlmMessage {
    data class User(val text: String) : LlmMessage

    /**
     * A reply from the model. [raw] is the provider's own message content, replayed unchanged on the
     * next request (Claude needs its thinking and tool_use blocks back exactly as sent).
     */
    data class Assistant(val text: String?, val toolCalls: List<LlmToolCall>, val raw: JsonElement? = null) : LlmMessage

    data class ToolResults(val results: List<LlmToolResult>) : LlmMessage
}

data class LlmToolCall(val id: String, val name: String, val arguments: JsonObject)

data class LlmToolResult(val callId: String, val content: String, val isError: Boolean = false)

/** One model turn: text, tool calls, or both. [refused] means the provider's safety system declined. */
data class LlmReply(val text: String?, val toolCalls: List<LlmToolCall>, val raw: JsonElement?, val refused: Boolean = false) {
    fun asMessage() = LlmMessage.Assistant(text, toolCalls, raw)
}

class LlmException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { AUTH, RATE_LIMIT, NETWORK, REFUSED, BAD_REQUEST, BAD_RESPONSE }
}

interface LlmProvider {
    val config: ProviderConfig

    /** One request whose answer must be a JSON object matching [schema] (used by the router). */
    suspend fun structured(role: ModelRole, system: String, user: String, schemaName: String, schema: JsonObject): JsonObject

    /** One step of a tool-calling conversation (used by the section helpers). */
    suspend fun chat(role: ModelRole, system: String, messages: List<LlmMessage>, tools: List<ToolSpec>): LlmReply
}

fun createProvider(config: ProviderConfig, transport: HttpTransport = defaultTransport()): LlmProvider = when (config.kind) {
    ProviderKind.ANTHROPIC -> AnthropicProvider(config, transport)
    ProviderKind.OPENAI -> OpenAiCompatibleProvider(config, transport, OpenAiCompatibleProvider.Flavor.OPENAI)
    ProviderKind.XAI -> OpenAiCompatibleProvider(config, transport, OpenAiCompatibleProvider.Flavor.XAI)
}
