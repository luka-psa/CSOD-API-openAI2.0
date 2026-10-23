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
import app.hearth.ai.route.StockWords
import app.hearth.ai.route.TaskWords
import app.hearth.core.tools.food.AddShoppingItemArgs
import app.hearth.core.tools.food.SetStockStateArgs
import app.hearth.core.tools.food.SetStockStateTool
import app.hearth.ai.route.TimeWords
import app.hearth.core.model.BoardKind
import app.hearth.core.model.TaskKind
import app.hearth.core.model.boardKind
import app.hearth.core.tools.board.AddGoalArgs
import app.hearth.core.tools.board.AddGoalTool
import app.hearth.core.tools.board.AddHouseRuleArgs
import app.hearth.core.tools.board.AddHouseRuleTool
import app.hearth.core.tools.board.LogFrictionArgs
import app.hearth.core.tools.board.LogFrictionTool
import app.hearth.core.model.kind
import app.hearth.core.tools.backlog.AddToBacklogArgs
import app.hearth.core.tools.backlog.AddToBacklogTool
import app.hearth.core.tools.backlog.BacklogRefArgs
import app.hearth.core.tools.backlog.CompleteBacklogItemTool
import app.hearth.core.tools.backlog.ListBacklogTool
import app.hearth.core.tools.backlog.PlanBacklogItemTool
import app.hearth.core.tools.backlog.When
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
 * Without a provider, or when it fails, the [KeywordRouter] puts the message on the
 * backlog with a section label, so nothing anyone says is ever lost.
 */
class Orchestrator(
    private val registry: ToolRegistry,
    private val provider: () -> LlmProvider? = { null },
    private val parser: LocalIntentParser = LocalIntentParser(),
) {
    suspend fun handle(text: String, ctx: ToolContext, history: List<ChatTurn> = emptyList()): ChatReply {
        // "de koffie is op": stock if we keep it, otherwise straight onto the shopping list.
        StockWords.extract(text)?.let { hint ->
            val local = ctx.copy(lang = KeywordRouter.language(text, ctx.lang))
            val stock = registry.execute(
                ToolCall.of(SetStockStateTool.NAME, SetStockStateArgs.serializer(), SetStockStateArgs(names = listOf(hint.name), state = hint.state)),
                local,
            )
            if (stock is ToolOutcome.Done) return ChatReply(stock.summary, listOf(stock))
            return runCalls(listOf(ToolCall.of(AddShoppingItemTool.NAME, AddShoppingItemArgs.serializer(), AddShoppingItemArgs(hint.name))), local)
        }
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
                // Sections that aren't built yet: keep it, sorted, on the backlog.
                else -> runCalls(listOf(captureCall(decision.text.ifBlank { text }, decision.section, routed)), routed)
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
            val list = registry.execute(ToolCall(ListBacklogTool.NAME, kotlinx.serialization.json.JsonObject(emptyMap())), local)
            val shopping = registry.execute(ToolCall(ListShoppingItemsTool.NAME, kotlinx.serialization.json.JsonObject(emptyMap())), local)
            return ChatReply(listOfNotNull(note, shopping.summary, list.summary).joinToString("\n"), listOf(shopping, list))
        }
        if (section == null) {
            DONE_WORDS.matchEntire(text.trim())?.let { m ->
                val call = ToolCall.of(CompleteBacklogItemTool.NAME, BacklogRefArgs.serializer(), BacklogRefArgs(texts = listOf(m.groupValues[1])))
                val outcome = registry.execute(call, local)
                if (outcome is ToolOutcome.Done) return ChatReply(outcome.summary, listOf(outcome))
            }
        }
        val reply = runCalls(listOf(captureCall(text, section ?: KeywordRouter.section(text), local)), local)
        return if (note == null) reply else reply.copy(text = note + "\n" + reply.text)
    }

    /**
     * A bare phrase the rules would put on the shopping list, but that clearly belongs elsewhere: "tandarts",
     * "IKEA kast", "keuken uitkuisen". A single project word ("lampen") stays something to buy.
     */
    private fun looksLikeOtherSection(parsed: LocalIntentParser.Parsed, text: String): Boolean {
        if (parsed.lang != null || !parsed.calls.all { it.name == AddShoppingItemTool.NAME }) return false
        val section = KeywordRouter.section(text)
        val words = text.trim().split(Regex("\\s+")).size
        return (section != Section.OTHER && section != Section.SHOPPING && (section != Section.PROJECT || words >= 2)) ||
            TaskWords.extract(text).let { it.intervalDays != null || it.urgent }
    }

    /**
     * A backlog entry. A trailing time word ("morgen", "tegen vrijdag") plans it right away; a rhythm
     * ("om de 2 weken") makes it a routine; "dringend" flags it.
     */
    private fun captureCall(text: String, section: Section, ctx: ToolContext): ToolCall {
        val chosenSection = if (section == Section.OTHER) KeywordRouter.section(text) else section
        when (chosenSection.boardKind) {
            BoardKind.RULE -> return ToolCall.of(AddHouseRuleTool.NAME, AddHouseRuleArgs.serializer(), AddHouseRuleArgs(text))
            BoardKind.FRICTION -> return ToolCall.of(LogFrictionTool.NAME, LogFrictionArgs.serializer(), LogFrictionArgs(text))
            BoardKind.GOAL -> return ToolCall.of(AddGoalTool.NAME, AddGoalArgs.serializer(), AddGoalArgs(text, KeywordRouter.goalScope(text)))
            null -> {}
        }
        val task = TaskWords.extract(text)
        val hint = TimeWords.extract(task.text, ctx.today)
        val body = hint?.text ?: task.text
        var chosen = if (section == Section.OTHER) KeywordRouter.section(body) else section
        if (task.intervalDays != null && chosen.kind != TaskKind.ROUTINE) chosen = Section.CHORE
        if (task.urgent && chosen == Section.OTHER) chosen = Section.ADMIN
        val args = AddToBacklogArgs(
            body, chosen,
            hint?.whenValue ?: When.UNPLANNED, hint?.date?.toString(), hint?.finishBy ?: false,
            urgent = task.urgent, intervalDays = task.intervalDays,
        )
        return ToolCall.of(AddToBacklogTool.NAME, AddToBacklogArgs.serializer(), args)
    }

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
        null -> setOf(ListShoppingItemsTool.NAME, ListBacklogTool.NAME)
        else -> setOf(ListBacklogTool.NAME, CompleteBacklogItemTool.NAME, PlanBacklogItemTool.NAME, AddToBacklogTool.NAME)
    }

    private companion object {
        val SHOPPING_SECTIONS = setOf(Section.SHOPPING, Section.STOCK)
        val DONE_WORDS = Regex(
            "^(?:de |het |the )?(.+?)\\s+(?:is|zijn|was|are)\\s+(?:gedaan|klaar|geregeld|geboekt|gebeld|done|booked|sorted|finished)[.!]?$",
            RegexOption.IGNORE_CASE,
        )
        val ROUTE_SCHEMA = RouteDecision.serializer().descriptor.toJsonSchema()
        const val MAX_STEPS = 5
        const val HISTORY_TURNS = 6
    }
}
