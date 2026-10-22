package app.hearth.core.tools

import app.hearth.core.model.Lang
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialInfo

/**
 * One household action. The UI, the chat orchestrator, the local parser and (later) the MCP
 * server all go through [Tool.run] via [ToolRegistry]; there is no second code path.
 *
 * [argsSerializer] is the single source of truth for the input schema (see [toJsonSchema]).
 */
interface Tool<A, R : ToolResult> {
    val name: String
    val description: String
    /** Deletes, clears, bulk changes, restores. Never run from chat or MCP without confirmation. */
    val destructive: Boolean get() = false
    val argsSerializer: KSerializer<A>
    val resultSerializer: KSerializer<R>

    suspend fun run(args: A, ctx: ToolContext): R
}

/** Every result carries a short, friendly line the chat can show verbatim. */
interface ToolResult {
    val summary: String
}

enum class ToolSource { UI, CHAT, MCP }

data class ToolContext(
    val source: ToolSource,
    val lang: Lang,
    val actorMemberId: String? = null,
) {
    fun t(nl: String, en: String): String = if (lang == Lang.NL) nl else en
}

/** A problem the person can fix ("I couldn't find X on the list"). The message is shown as-is. */
class ToolException(val userMessage: String) : Exception(userMessage)

/** Human description of an argument or tool; ends up in the generated JSON Schema. */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class Describe(val text: String)
