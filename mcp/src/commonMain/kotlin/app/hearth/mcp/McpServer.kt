package app.hearth.mcp

import app.hearth.core.model.newId
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolJson
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** A destructive call from the family's AI, waiting for a yes (in the AI chat or on the tablet). */
data class McpPending(val id: String, val call: ToolCall, val question: String)

/**
 * Hearth's MCP server (protocol over JSON-RPC 2.0, stateless, JSON responses). It offers the same tools as
 * the tablet's UI and chat, through the same [ToolRegistry]. Destructive tools never run directly: they
 * become a [McpPending] that the person confirms, either by saying yes in their AI chat (the AI then
 * calls `confirm_pending_action`) or with Ja/Nee on the tablet.
 */
class McpServer(
    private val registry: ToolRegistry,
    private val context: () -> ToolContext,
) {
    private val _pending = MutableStateFlow<List<McpPending>>(emptyList())
    val pending: StateFlow<List<McpPending>> = _pending.asStateFlow()

    /** Last few things the AI did, for the tablet to show ("ChatGPT zette melk op de lijst"). */
    private val _activity = MutableStateFlow<List<String>>(emptyList())
    val activity: StateFlow<List<String>> = _activity.asStateFlow()

    /** Handles one HTTP body (a message or a batch). Returns null when nothing needs an answer. */
    suspend fun handle(body: String): String? {
        val parsed = try {
            ToolJson.parseToJsonElement(body)
        } catch (e: Exception) {
            return error(JsonNull, PARSE_ERROR, "Parse error").toString()
        }
        return when (parsed) {
            is JsonArray -> parsed.mapNotNull { (it as? JsonObject)?.let { m -> handleMessage(m) } }
                .takeIf { it.isNotEmpty() }?.let { JsonArray(it).toString() }
            is JsonObject -> handleMessage(parsed)?.toString()
            else -> error(JsonNull, INVALID_REQUEST, "Invalid request").toString()
        }
    }

    private suspend fun handleMessage(message: JsonObject): JsonObject? {
        val id = message["id"]
        val method = message["method"]?.jsonPrimitive?.contentOrNull
        // Notifications and responses get no answer.
        if (id == null || id is JsonNull) return null
        if (method == null) return error(id, INVALID_REQUEST, "Missing method")
        val params = message["params"] as? JsonObject ?: JsonObject(emptyMap())
        return when (method) {
            "initialize" -> result(id, initialize(params))
            "ping" -> result(id, JsonObject(emptyMap()))
            "tools/list" -> result(id, listTools())
            "tools/call" -> result(id, callTool(params))
            "resources/list" -> result(id, buildJsonObject { putJsonArray("resources") {} })
            "prompts/list" -> result(id, buildJsonObject { putJsonArray("prompts") {} })
            else -> error(id, METHOD_NOT_FOUND, "Method not found: $method")
        }
    }

    private fun initialize(params: JsonObject): JsonObject {
        val asked = params["protocolVersion"]?.jsonPrimitive?.contentOrNull
        return buildJsonObject {
            put("protocolVersion", asked?.takeIf { it in SUPPORTED_VERSIONS } ?: SUPPORTED_VERSIONS.first())
            putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
            putJsonObject("serverInfo") {
                put("name", "hearth")
                put("title", "Hearth")
                put("version", "1.0")
            }
            put("instructions", INSTRUCTIONS)
        }
    }

    private fun listTools(): JsonObject = buildJsonObject {
        putJsonArray("tools") {
            registry.specs.filter { it.exposeOverMcp }.forEach { spec ->
                add(
                    buildJsonObject {
                        put("name", spec.name)
                        put("description", spec.description + if (spec.destructive) " Needs the person's confirmation." else "")
                        put("inputSchema", spec.inputSchema)
                        putJsonObject("annotations") {
                            put("readOnlyHint", spec.readOnly)
                            put("destructiveHint", spec.destructive)
                            put("openWorldHint", false)
                        }
                    },
                )
            }
            add(confirmSpec(CONFIRM, "Run a pending action after the person said yes in this chat. Pass the id from the confirmation message."))
            add(confirmSpec(CANCEL, "Drop a pending action when the person said no."))
        }
    }

    private fun confirmSpec(name: String, description: String) = buildJsonObject {
        put("name", name)
        put("description", description)
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") { putJsonObject("id") { put("type", "string") } }
            putJsonArray("required") { add(JsonPrimitive("id")) }
        }
        putJsonObject("annotations") { put("readOnlyHint", false); put("destructiveHint", name == CONFIRM) }
    }

    private suspend fun callTool(params: JsonObject): JsonObject {
        val name = params["name"]?.jsonPrimitive?.contentOrNull ?: return toolError("Missing tool name.")
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val ctx = context()
        when (name) {
            CONFIRM -> return confirm(args["id"]?.jsonPrimitive?.contentOrNull ?: "")?.let(::outcomeResult)
                ?: toolError(ctx.t("Die bevestiging vind ik niet meer.", "That confirmation is no longer there."))
            CANCEL -> {
                cancel(args["id"]?.jsonPrimitive?.contentOrNull ?: "")
                return text(ctx.t("Oké, niets veranderd.", "Okay, nothing changed."))
            }
        }
        val spec = registry.specs.firstOrNull { it.name == name && it.exposeOverMcp } ?: return toolError("Unknown tool: $name")
        val outcome = registry.execute(ToolCall(spec.name, args), ctx)
        if (outcome is ToolOutcome.NeedsConfirmation) {
            val pending = McpPending(newId(), outcome.call, outcome.summary)
            _pending.update { it + pending }
            return text(
                ctx.t(
                    "Bevestiging nodig: ${outcome.summary} Vraag het aan de persoon. Zegt die ja, roep dan $CONFIRM aan met id ${pending.id}. Het kan ook met Ja/Nee op de tablet.",
                    "Confirmation needed: ${outcome.summary} Ask the person. If they say yes, call $CONFIRM with id ${pending.id}. They can also tap Yes/No on the tablet.",
                ),
            )
        }
        return outcomeResult(outcome)
    }

    /** Runs a pending action (from the AI chat or the tablet's Ja button). */
    suspend fun confirm(id: String): ToolOutcome? {
        val pending = _pending.value.firstOrNull { it.id == id } ?: return null
        _pending.update { list -> list.filterNot { it.id == id } }
        return registry.execute(pending.call, context(), confirmed = true).also { log(it) }
    }

    fun cancel(id: String) = _pending.update { list -> list.filterNot { it.id == id } }

    private fun outcomeResult(outcome: ToolOutcome): JsonObject {
        log(outcome)
        return when (outcome) {
            is ToolOutcome.Done -> buildJsonObject {
                putJsonArray("content") { add(textContent(outcome.summary)) }
                put("structuredContent", outcome.result)
                put("isError", false)
            }
            is ToolOutcome.Failed -> toolError(outcome.summary)
            is ToolOutcome.NeedsConfirmation -> text(outcome.summary)
        }
    }

    private fun log(outcome: ToolOutcome) {
        if (outcome is ToolOutcome.Done && registry.specs.firstOrNull { it.name == outcome.toolName }?.readOnly != true) {
            _activity.update { (it + outcome.summary).takeLast(5) }
        }
    }

    private fun text(message: String) = buildJsonObject {
        putJsonArray("content") { add(textContent(message)) }
        put("isError", false)
    }

    private fun toolError(message: String) = buildJsonObject {
        putJsonArray("content") { add(textContent(message)) }
        put("isError", true)
    }

    private fun textContent(message: String) = buildJsonObject {
        put("type", "text")
        put("text", message)
    }

    private fun result(id: JsonElement, result: JsonObject) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun error(id: JsonElement, code: Int, message: String) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }

    companion object {
        const val CONFIRM = "confirm_pending_action"
        const val CANCEL = "cancel_pending_action"
        val SUPPORTED_VERSIONS = listOf("2025-06-18", "2025-11-25", "2025-03-26", "2024-11-05")
        private const val PARSE_ERROR = -32700
        private const val INVALID_REQUEST = -32600
        private const val METHOD_NOT_FOUND = -32601

        private val INSTRUCTIONS = """
            Hearth is this family's shared household tablet. Use its tools for: the shopping list and stock (per room),
            tasks (routines that come back, admin with deadlines or waiting for a reply, projects with steps), the
            Werkbord (house rules, frictions, goals), meals and the weekly meeting (weekoverleg).
            Reply in the person's language (Flemish Dutch or English) and keep Hearth's tone: warm, short, never
            "overdue" or "you forgot". Show the tool's summary to the person. Destructive actions come back as a
            confirmation question: ask the person, and only call confirm_pending_action after they say yes.
            Dates are yyyy-MM-dd.
        """.trimIndent().replace("\n", " ")
    }
}
