package app.hearth.ai

import app.hearth.ai.llm.LlmException
import app.hearth.ai.llm.LlmMessage
import app.hearth.ai.llm.LlmProvider
import app.hearth.ai.llm.LlmToolResult
import app.hearth.ai.llm.ModelRole
import app.hearth.ai.route.Intent
import app.hearth.ai.route.KeywordRouter
import app.hearth.ai.route.Prompts
import app.hearth.ai.route.RouteDecision
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolJson
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolRegistry
import app.hearth.core.tools.food.AddShoppingItemTool
import app.hearth.core.tools.food.ListShoppingItemsTool
import app.hearth.core.tools.food.MarkBoughtTool
import app.hearth.core.tools.food.RemoveShoppingItemTool
import app.hearth.core.tools.food.UnmarkBoughtTool
import app.hearth.core.tools.inbox.ArchiveInboxItemTool
import app.hearth.core.tools.inbox.CaptureArgs
import app.hearth.core.tools.inbox.CaptureToInboxTool
import app.hearth.core.tools.inbox.ListInboxTool
import app.hearth.core.tools.toJsonSchema

/** A destructive action waiting for a yes in the chat. */
data class PendingAction(val call: ToolCall, val question: String, val lang: Lang)

data class ChatReply(val text: String, val outcomes: List<ToolOutcome>, val pending: PendingAction? = null)

/** A previous line in the chat, for a little conversational context. */
data class ChatTurn(val fromUser: Boolean, val text: String)

/**
 * Routes a chat message to tools in up to three tiers:
 * 1. Local rules ([LocalIntentParser]): instant and offline, for clear everyday commands.
 * 2. Router model: sorts the message into a [Section] (fixed instruction, small model).
 * 3. Section helper model: acts in that section with only its tools and writes the reply.
 * Without a provider, or when it fails, the [KeywordRouter] sorts the message into the inbox, so
 * nothing anyone says is ever lost.
 */
class Orchestrator(
    private val registry: ToolRegistry,
    private val provider: () -> LlmProvider? = { null },
    private val parser: LocalIntentParser = LocalIntentParser(),
) {
    suspend fun handle(text: String, ctx: ToolContext, history: List<ChatTurn> = emptyList()): ChatReply {
        val llm = provider()
        // Tier 1: clear commands never wait for the network.
        parser.parse(text, allowBare = llm == null)?.let { parsed ->
            val local = parsed.lang?.let { ctx.copy(lang = it) } ?: ctx
            if (llm != null || !looksLikeOtherSection(parsed, text)) return runCalls(parsed.calls, local)
        }
        if (llm == null) return captureOffline(text, ctx, note = null)

        val decision = try {
            route(llm, text)
        } catch (e: LlmException) {
            return captureOffline(text, ctx, note = problemNote(e, ctx))
        }
        val routed = ctx.copy(lang = decision.lang)
        return try {
            when {
                decision.intent == Intent.CHAT -> ChatReply(decision.reply.ifBlank { routed.t("Graag gedaan!", "You're welcome!") }, emptyList())
                decision.section in SHOPPING_SECTIONS -> helper(llm, Section.SHOPPING, text, routed, history)
                decision.intent == Intent.ASK -> helper(llm, null, text, routed, history)
                decision.intent == Intent.DONE -> helper(llm, decision.section, text, routed, history)
                // Sections that aren't built yet: keep it, sorted, in the inbox.
                else -> runCalls(listOf(captureCall(decision.text.ifBlank { text }, decision.section)), routed)
            }
        } catch (e: LlmException) {
            captureOffline(text, routed, note = problemNote(e, routed), section = decision.section)
        }
    }

    /** Runs a destructive action the person confirmed in the chat. */
    suspend fun confirm(pending: PendingAction, ctx: ToolContext): ChatReply {
        val outcome = registry.execute(pending.call, ctx.copy(lang = pending.lang), confirmed = true)
        return ChatReply(outcome.summary, listOf(outcome))
    }

    // ---- stages ----

    private suspend fun route(llm: LlmProvider, text: String): RouteDecision {
        val json = llm.structured(ModelRole.ROUTER, Prompts.ROUTER, text, "route", ROUTE_SCHEMA)
        return try {
            ToolJson.decodeFromJsonElement(RouteDecision.serializer(), json)
        } catch (e: Exception) {
            throw LlmException(LlmException.Reason.BAD_RESPONSE, "Could not read the router's answer.")
        }
    }

    private suspend fun helper(
        llm: LlmProvider,
        section: Section?,
        text: String,
        ctx: ToolContext,
        history: List<ChatTurn>,
    ): ChatReply {
        val toolNames = toolsFor(section)
        val tools = registry.specs.filter { it.name in toolNames }
        val messages = mutableListOf<LlmMessage>()
        history.takeLast(HISTORY_TURNS).dropWhile { !it.fromUser }.forEach {
            messages += if (it.fromUser) LlmMessage.User(it.text) else LlmMessage.Assistant(it.text, emptyList())
        }
        messages += LlmMessage.User(text)
        val outcomes = mutableListOf<ToolOutcome>()

        repeat(MAX_STEPS) {
            val reply = llm.chat(ModelRole.HELPER, Prompts.helper(section, ctx.lang), messages, tools)
            if (reply.refused) return captureOffline(text, ctx, note = null, section = section ?: Section.OTHER)
            if (reply.toolCalls.isEmpty()) {
                val summary = outcomes.joinToString("\n") { it.summary }
                return ChatReply(reply.text?.takeIf { it.isNotBlank() } ?: summary.ifBlank { ctx.t("Oké.", "Okay.") }, outcomes)
            }
            messages += reply.asMessage()
            val results = mutableListOf<LlmToolResult>()
            for (call in reply.toolCalls) {
                val outcome = if (call.name in toolNames) {
                    registry.execute(ToolCall(call.name, call.arguments), ctx)
                } else {
                    ToolOutcome.Failed(call.name, "Unknown tool")
                }
                if (outcome is ToolOutcome.NeedsConfirmation) {
                    return ChatReply(outcome.summary, outcomes, PendingAction(outcome.call, outcome.summary, ctx.lang))
                }
                outcomes += outcome
                results += LlmToolResult(
                    callId = call.id,
                    content = when (outcome) {
                        is ToolOutcome.Done -> outcome.result.toString()
                        else -> outcome.summary
                    },
                    isError = outcome is ToolOutcome.Failed,
                )
            }
            messages += LlmMessage.ToolResults(results)
        }
        return ChatReply(outcomes.joinToString("\n") { it.summary }, outcomes)
    }

    // ---- helpers ----

    private suspend fun runCalls(calls: List<ToolCall>, ctx: ToolContext): ChatReply {
        val outcomes = mutableListOf<ToolOutcome>()
        for (call in calls) {
            val outcome = registry.execute(call, ctx)
            if (outcome is ToolOutcome.NeedsConfirmation) {
                return ChatReply(outcome.summary, outcomes, PendingAction(outcome.call, outcome.summary, ctx.lang))
            }
            outcomes += outcome
        }
        return ChatReply(outcomes.joinToString("\n") { it.summary }, outcomes)
    }

    private suspend fun captureOffline(text: String, ctx: ToolContext, note: String?, section: Section? = null): ChatReply {
        val lang = KeywordRouter.language(text, ctx.lang)
        val local = ctx.copy(lang = lang)
        if (section == null && KeywordRouter.isQuestion(text)) {
            val list = registry.execute(ToolCall(ListInboxTool.NAME, kotlinx.serialization.json.JsonObject(emptyMap())), local)
            val shopping = registry.execute(ToolCall(ListShoppingItemsTool.NAME, kotlinx.serialization.json.JsonObject(emptyMap())), local)
            return ChatReply(listOfNotNull(note, shopping.summary, list.summary).joinToString("\n"), listOf(shopping, list))
        }
        val reply = runCalls(listOf(captureCall(text, section ?: KeywordRouter.section(text))), local)
        return if (note == null) reply else reply.copy(text = note + "\n" + reply.text)
    }

    /** A bare word the rules would put on the shopping list, but that clearly belongs elsewhere ("tandarts"). */
    private fun looksLikeOtherSection(parsed: LocalIntentParser.Parsed, text: String): Boolean =
        parsed.lang == null && parsed.calls.all { it.name == AddShoppingItemTool.NAME } &&
            KeywordRouter.section(text).let { it != Section.OTHER && it != Section.SHOPPING && it != Section.PROJECT }

    private fun captureCall(text: String, section: Section) =
        ToolCall.of(CaptureToInboxTool.NAME, CaptureArgs.serializer(), CaptureArgs(text.trim(), section))

    private fun problemNote(e: LlmException, ctx: ToolContext): String = when (e.reason) {
        LlmException.Reason.AUTH -> ctx.t(
            "De AI-sleutel werkt niet (zie Instellingen). Ik heb het veilig bewaard:",
            "The AI key isn't working (see Settings). I kept it safe:",
        )
        else -> ctx.t("Even geen verbinding met de AI. Ik heb het veilig bewaard:", "Can't reach the AI right now. I kept it safe:")
    }

    private fun toolsFor(section: Section?): Set<String> = when (section) {
        Section.SHOPPING, Section.STOCK -> setOf(
            AddShoppingItemTool.NAME, ListShoppingItemsTool.NAME, MarkBoughtTool.NAME, UnmarkBoughtTool.NAME,
            RemoveShoppingItemTool.NAME,
        )
        null -> setOf(ListShoppingItemsTool.NAME, ListInboxTool.NAME)
        else -> setOf(ListInboxTool.NAME, ArchiveInboxItemTool.NAME, CaptureToInboxTool.NAME)
    }

    private companion object {
        val SHOPPING_SECTIONS = setOf(Section.SHOPPING, Section.STOCK)
        val ROUTE_SCHEMA = RouteDecision.serializer().descriptor.toJsonSchema()
        const val MAX_STEPS = 5
        const val HISTORY_TURNS = 6
    }
}
