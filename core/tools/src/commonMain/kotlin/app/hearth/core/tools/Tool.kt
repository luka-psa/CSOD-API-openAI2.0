package app.hearth.core.tools

import app.hearth.core.model.Lang
import app.hearth.core.model.today
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialInfo

/**
 * One household action. The UI, the chat orchestrator, the local parser and the MCP
 * server all go through [Tool.run] via [ToolRegistry]; there is no second code path.
 *
 * [argsSerializer] is the single source of truth for the input schema (see [toJsonSchema]).
 */
interface Tool<A, R : ToolResult> {
    val name: String
    val description: String
    /** Deletes, clears, bulk changes, restores. Never run from chat or MCP without confirmation. */
    val destructive: Boolean get() = false
    /** Only reads, never changes anything (MCP clients may call these without asking). */
    val readOnly: Boolean get() = name.startsWith("list_") || name.endsWith("_agenda")
    /** False for tablet-only system tools (connection settings): never offered to an AI over MCP. */
    val exposeOverMcp: Boolean get() = true
    val argsSerializer: KSerializer<A>
    val resultSerializer: KSerializer<R>

    suspend fun run(args: A, ctx: ToolContext): R

    /** The question shown before a destructive tool runs ("Remove milk from the list?"). */
    fun confirmQuestion(args: A, ctx: ToolContext): String = ctx.t("Zeker?", "Are you sure?")
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
    /** "Today" for planning; injectable so tests and the household's timezone agree. */
    val today: LocalDate = today(),
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
