package app.hearth.core.tools

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

val ToolJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

/** A request to run a tool by name, as produced by the parser, an LLM or an MCP client. */
@Serializable
data class ToolCall(val name: String, val arguments: JsonObject) {
    companion object {
        fun <A> of(name: String, serializer: KSerializer<A>, args: A) =
            ToolCall(name, ToolJson.encodeToJsonElement(serializer, args).jsonObject)
    }
}

/** What an LLM or MCP client sees for one tool. */
data class ToolSpec(
    val name: String,
    val description: String,
    val destructive: Boolean,
    val inputSchema: JsonObject,
)

sealed interface ToolOutcome {
    val toolName: String
    val summary: String

    data class Done(override val toolName: String, override val summary: String, val result: JsonObject) : ToolOutcome
    data class NeedsConfirmation(override val toolName: String, override val summary: String, val call: ToolCall) : ToolOutcome
    data class Failed(override val toolName: String, override val summary: String) : ToolOutcome
}

class ToolRegistry(tools: List<Tool<*, *>>) {
    private val byName: Map<String, Tool<*, *>> = tools.associateBy { it.name }.also {
        require(it.size == tools.size) { "Duplicate tool names" }
    }

    val specs: List<ToolSpec> = tools.map {
        ToolSpec(it.name, it.description, it.destructive, it.argsSerializer.descriptor.toJsonSchema())
    }

    operator fun contains(name: String) = name in byName

    /** Typed entry point for the UI. Destructive tools must be confirmed on screen first. */
    suspend fun <A, R : ToolResult> run(tool: Tool<A, R>, args: A, ctx: ToolContext, confirmed: Boolean = false): R {
        check(byName[tool.name] === tool) { "Tool ${tool.name} is not registered" }
        check(!tool.destructive || confirmed) { "${tool.name} is destructive and needs confirmation" }
        return tool.run(args, ctx)
    }

    /** Untyped entry point for chat, parser and MCP. */
    suspend fun execute(call: ToolCall, ctx: ToolContext, confirmed: Boolean = false): ToolOutcome {
        val tool = byName[call.name]
            ?: return ToolOutcome.Failed(call.name, ctx.t("Dat kan ik (nog) niet.", "I can't do that (yet)."))
        return executeTyped(tool, call, ctx, confirmed)
    }

    private suspend fun <A, R : ToolResult> executeTyped(
        tool: Tool<A, R>,
        call: ToolCall,
        ctx: ToolContext,
        confirmed: Boolean,
    ): ToolOutcome {
        val args = try {
            ToolJson.decodeFromJsonElement(tool.argsSerializer, call.arguments)
        } catch (e: SerializationException) {
            return ToolOutcome.Failed(tool.name, ctx.t("Ik begreep dat niet helemaal.", "I didn't quite get that."))
        } catch (e: IllegalArgumentException) {
            return ToolOutcome.Failed(tool.name, ctx.t("Ik begreep dat niet helemaal.", "I didn't quite get that."))
        }
        if (tool.destructive && !confirmed) {
            return ToolOutcome.NeedsConfirmation(tool.name, ctx.t("Zeker?", "Are you sure?"), call)
        }
        return try {
            val result = tool.run(args, ctx)
            ToolOutcome.Done(tool.name, result.summary, ToolJson.encodeToJsonElement(tool.resultSerializer, result).jsonObject)
        } catch (e: ToolException) {
            ToolOutcome.Failed(tool.name, e.userMessage)
        }
    }
}
