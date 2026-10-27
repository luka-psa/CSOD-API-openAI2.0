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
import app.hearth.ai.route.MealWords
import app.hearth.ai.route.RecipeWords
import app.hearth.core.tools.week.SaveRecipeArgs
import app.hearth.core.tools.week.SaveRecipeTool
import app.hearth.ai.route.StockWords
import app.hearth.ai.route.RoutineWords
import app.hearth.core.tools.week.ImportRecipeArgs
import app.hearth.core.tools.week.ImportRecipeTool
import app.hearth.ai.route.ListKind
import app.hearth.ai.route.ListHint
import app.hearth.ai.route.ListWords
import app.hearth.core.tools.backlog.AddRoutineTool
import app.hearth.core.tools.backlog.AddRoutineArgs
import app.hearth.core.tools.week.PlanMealArgs
import app.hearth.core.tools.week.PlanMealTool
import app.hearth.ai.route.TaskWords
import app.hearth.core.tools.food.AddShoppingItemArgs
import app.hearth.core.tools.food.SetStockStateArgs
import app.hearth.core.tools.food.SetStockStateTool
import app.hearth.ai.route.TimeWords
import app.hearth.core.model.BoardKind
import app.hearth.core.model.TaskKind
import app.hearth.core.model.boardKind
import app.hearth.ai.route.AreaWords
import app.hearth.ai.route.MeetingWords
import app.hearth.ai.route.Spoken
import app.hearth.core.tools.system.SyncNowTool
import app.hearth.core.model.Topic
import app.hearth.core.model.topic
import app.hearth.ai.route.Place
import app.hearth.ai.route.Stage
import app.hearth.core.model.plusDays
import app.hearth.core.tools.board.AddIdeaArgs
import app.hearth.core.tools.board.AddIdeaTool
import app.hearth.core.tools.week.AddWinArgs
import app.hearth.core.tools.week.AddWinTool
import kotlinx.datetime.LocalDate
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
        // A link to a recipe: its name, ingredients, steps and photo become one of our recipes.
        LINK.find(text)?.takeIf { text.replace(it.value, "").replace(RECIPE_WORD, "").isBlank() }?.let { link ->
            val local = ctx.copy(lang = KeywordRouter.language(text, ctx.lang))
            return runCalls(listOf(ToolCall.of(ImportRecipeTool.NAME, ImportRecipeArgs.serializer(), ImportRecipeArgs(link.value))), local)
        }
        // "synchroniseer", "sync now": the household's other devices, now.
        if (SYNC_WORDS.matches(text.trim()) && registry.contains(SyncNowTool.NAME)) {
            return runCalls(listOf(ToolCall(SyncNowTool.NAME, kotlinx.serialization.json.JsonObject(emptyMap()))), ctx.copy(lang = KeywordRouter.language(text, ctx.lang)))
        }
        // A pasted list: a heading says where it goes; without one every line is sorted on its own.
        ListWords.extract(text)?.let { list -> return pastedList(list, ctx, history) }
        // "recept: pannenkoeken met bloem, melk en eieren": a recipe for the week menu.
        if (RECIPE_PREFIX.containsMatchIn(text)) {
            val hint = RecipeWords.parse(text)
            if (hint.name != null) {
                val local = ctx.copy(lang = KeywordRouter.language(text, ctx.lang))
                return runCalls(listOf(ToolCall.of(SaveRecipeTool.NAME, SaveRecipeArgs.serializer(), SaveRecipeArgs(hint.name, hint.ingredients, hint.notes))), local)
            }
        }
        // "dat ging goed: we aten elke avond samen": a win for the weekoverleg.
        WIN_PREFIX.matchEntire(text.trim())?.let { m ->
            val local = ctx.copy(lang = KeywordRouter.language(text, ctx.lang))
            return runCalls(listOf(ToolCall.of(AddWinTool.NAME, AddWinArgs.serializer(), AddWinArgs(m.groupValues[1]))), local)
        }
        // "avondroutine: boodschappen, Mia ophalen, koken, pyjama": a routine with its steps, in order.
        RoutineWords.extract(text)?.let { hint ->
            val local = ctx.copy(lang = KeywordRouter.language(text, ctx.lang))
            return runCalls(listOf(ToolCall.of(AddRoutineTool.NAME, AddRoutineArgs.serializer(), AddRoutineArgs(hint.name, hint.steps, hint.anchor))), local)
        }
        // "voeg aan de huisregels toe dat …": the person named the place; only the rest becomes the item.
        AreaWords.extract(text)?.let { hint ->
            val local = ctx.copy(lang = KeywordRouter.language(text, ctx.lang))
            return runCalls(listOf(captureCall(hint.text, hint.section, local)), local)
        }
        // "we eten vrijdag spaghetti": the meal plan (its ingredients go onto the list).
        MealWords.extract(text, ctx.today)?.let { hint ->
            val local = ctx.copy(lang = KeywordRouter.language(text, ctx.lang))
            return runCalls(listOf(ToolCall.of(PlanMealTool.NAME, PlanMealArgs.serializer(), PlanMealArgs(hint.dish, hint.date.toString()))), local)
        }
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

    private suspend fun pastedList(list: ListHint, ctx: ToolContext, history: List<ChatTurn>): ChatReply {
        val local = ctx.copy(lang = KeywordRouter.language(list.items.joinToString(" ") + " " + (list.heading ?: ""), ctx.lang))
        val heading = list.heading
        return when (ListWords.kind(heading)) {
            ListKind.SHOPPING -> runCalls(list.items.map { ToolCall.of(AddShoppingItemTool.NAME, AddShoppingItemArgs.serializer(), AddShoppingItemArgs(it)) }, local)
            ListKind.RECIPE -> runCalls(listOf(ToolCall.of(SaveRecipeTool.NAME, SaveRecipeArgs.serializer(), SaveRecipeArgs(ListWords.recipeName(heading!!), list.items))), local)
            // "Paklijst babysit" with its lines: a checklist, ticked when it's needed (a moment in the name makes it a daily routine).
            ListKind.CHECKLIST -> {
                val anchor = RoutineWords.momentOf(heading!!)
                runCalls(listOf(ToolCall.of(AddRoutineTool.NAME, AddRoutineArgs.serializer(), AddRoutineArgs(heading.replaceFirstChar { it.uppercase() }, list.items, anchor))), local)
            }
            ListKind.NONE -> {
                val replies = list.items.map { handle(it, ctx, history) }
                ChatReply(replies.joinToString("\n") { it.text }, replies.flatMap { it.outcomes }, replies.firstNotNullOfOrNull { it.pending })
            }
        }
    }

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
            BoardKind.IDEA -> return ToolCall.of(AddIdeaTool.NAME, AddIdeaArgs.serializer(), AddIdeaArgs(text))
            null -> {}
        }
        return backlogCall(text, section, ctx, fromTalk = false).first
    }

    /**
     * A backlog entry (never the Werkbord). A time word plans it wherever it stands ("morgen", "tegen vrijdag",
     * "op 12/12", "verlof van 3 tot 7 november"), a rhythm ("om de 2 weken") makes it a routine, "dringend" flags it.
     */
    private fun backlogCall(text: String, section: Section, ctx: ToolContext, fromTalk: Boolean): Pair<ToolCall, AddToBacklogArgs> {
        val task = TaskWords.extract(text)
        val hint = TimeWords.extractAnywhere(task.text, ctx.today)
        val said = hint?.text ?: task.text
        var chosen = if (section == Section.OTHER) KeywordRouter.section(said) else section
        val body = KeywordRouter.withoutPrefix(said).let { if (task.anchor != null) it.replaceFirstChar { c -> c.uppercase() } else it }
        if (chosen.boardKind != null || chosen == Section.SHOPPING || chosen == Section.STOCK) chosen = Section.OTHER
        // A moment of the day makes it part of a rhythm ("'s avonds de tassen klaarzetten").
        if (task.anchor != null && chosen.kind != TaskKind.ROUTINE) chosen = Section.RHYTHM
        if (task.intervalDays != null && chosen.kind != TaskKind.ROUTINE) chosen = Section.CHORE
        if (task.urgent && chosen == Section.OTHER) chosen = Section.ADMIN
        val args = AddToBacklogArgs(
            // Talk reads as an item: "moet ik afspreken met Anthony" -> "Afspreken met Anthony".
            if (fromTalk) MeetingWords.gist(body) else body, chosen,
            hint?.whenValue ?: When.UNPLANNED, hint?.date?.toString(), hint?.finishBy ?: false,
            urgent = task.urgent, intervalDays = task.intervalDays, anchor = task.anchor,
            weekdays = task.schedule?.weekdays.orEmpty(), dayOfMonth = task.schedule?.monthDay,
            weekOfMonth = task.schedule?.monthWeek, time = task.schedule?.time,
        )
        return ToolCall.of(AddToBacklogTool.NAME, AddToBacklogArgs.serializer(), args) to args
    }

    // ---------- Luister mee: the weekoverleg's conversation, sorted ----------

    /** One thing concluded from the conversation and where it would go. [calls] run when the family says yes. */
    data class Proposal(
        /** The words it rests on (cleaned); fix them and Hearth reads it again. */
        val sentence: String,
        val place: Place,
        /** What the new item will say. */
        val text: String,
        /** The day it gets, if any. */
        val date: LocalDate?,
        val calls: List<ToolCall>,
        /** Everything said about it, as heard. */
        val heard: String = sentence,
        /** "Deze week": no day yet, it goes to Volgende. */
        val next: Boolean = false,
        /** The topic it lands in (null: a win, or just talk). */
        val topic: Topic? = null,
    )

    /** A thought of the conversation and what Hearth concludes from it (nothing, when it was just talk). */
    data class Talk(val heard: String, val lines: IntRange, val proposals: List<Proposal>, val stage: Stage = Stage.TALK)

    /** The conversation as thoughts, each with its conclusions. [people] are the household's names. */
    fun listen(
        lines: List<String>,
        ctx: ToolContext,
        people: Collection<String> = emptyList(),
        times: List<Long>? = null,
        /** The sentence model's readings, when smarter listening is on. */
        sense: ((String) -> app.hearth.ai.sense.Reading?)? = null,
    ): List<Talk> =
        MeetingWords.thoughts(lines, ctx.today, people, times, sense).map { t ->
            Talk(t.heard, t.lines, t.conclusions.map { c -> proposal(c.sentence, c.place, ctx).copy(heard = c.heard) }, t.stage)
        }

    /** Every conclusion of [lines]; a thought that was just talk comes back once, as [Place.NONE]. */
    fun propose(lines: List<String>, ctx: ToolContext, people: Collection<String> = emptyList(), times: List<Long>? = null): List<Proposal> =
        listen(lines, ctx, people, times).flatMap { t ->
            t.proposals.ifEmpty { listOf(proposal(Spoken.clean(t.heard), Place.NONE, ctx).copy(heard = t.heard)) }
        }

    /**
     * What the interpreter read (one action) as a suggestion: the same tools as everywhere else, the day and time read
     * from its words like any sentence. [heard] is the line it came from. Questions ("wat staat er morgen?") are not
     * suggestions; null for those.
     */
    fun proposalOf(action: app.hearth.ai.interpret.Action, heard: String, ctx: ToolContext): Proposal? {
        val local = ctx.copy(lang = KeywordRouter.language(heard, ctx.lang))
        val a = action.args
        fun text(vararg extra: String?) = (listOf(a["text"]) + extra).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
        fun backlog(place: Place, section: Section, words: String): Proposal {
            val (call, args) = backlogCall(words, section, local, fromTalk = false)
            val date = when (args.`when`) {
                When.TODAY -> local.today
                When.TOMORROW -> local.today.plusDays(1)
                When.DAY -> args.date?.let { LocalDate.parse(it) }
                else -> null
            }
            return Proposal(heard, place, args.text, date, listOf(call), heard = heard, next = args.`when` == When.NEXT, topic = args.section.topic())
        }
        fun board(place: Place, text: String, call: ToolCall, topic: Topic?) = Proposal(heard, place, text, null, listOf(call), heard = heard, topic = topic)
        return when (action.name) {
            "add_task" -> backlog(Place.PLANNING, Section.OTHER, text(a["day"]))
            "add_appointment" -> backlog(Place.EVENT, Section.APPOINTMENT, text(a["day"], a["time"]))
            "add_free_time" -> backlog(Place.FREE_TIME, Section.PROTECTED_TIME, text(a["day"]))
            "add_routine" -> backlog(Place.RHYTHM, Section.RHYTHM, text(a["when"]))
            "add_shopping" -> {
                val calls = shoppingCalls(a["items"].orEmpty())
                if (calls.isEmpty()) null
                else Proposal(heard, Place.SHOPPING, calls.joinToString(", ") { ToolJson.decodeFromJsonElement(AddShoppingItemArgs.serializer(), it.arguments).name.replaceFirstChar { c -> c.uppercase() } }, null, calls, heard = heard, topic = Topic.SHOPPING)
            }
            "plan_meal" -> {
                val dish = a["dish"]?.takeIf { it.isNotBlank() } ?: return null
                // A day on its own ("vrijdag") reads like the end of a sentence.
                val date = a["day"]?.let { TimeWords.extractAnywhere("eten ${it}", local.today) }?.let { h ->
                    when (h.whenValue) {
                        When.TODAY -> local.today
                        When.TOMORROW -> local.today.plusDays(1)
                        else -> h.date
                    }
                } ?: local.today
                Proposal(heard, Place.MEAL, dish, date, listOf(ToolCall.of(PlanMealTool.NAME, PlanMealArgs.serializer(), PlanMealArgs(dish, date.toString()))), heard = heard, topic = Topic.MENU)
            }
            "add_house_rule" -> text().ifBlank { null }?.let { board(Place.HOUSE_RULE, it, ToolCall.of(AddHouseRuleTool.NAME, AddHouseRuleArgs.serializer(), AddHouseRuleArgs(it)), Topic.RULES) }
            "log_friction" -> text().ifBlank { null }?.let { board(Place.FRICTION, it, ToolCall.of(LogFrictionTool.NAME, LogFrictionArgs.serializer(), LogFrictionArgs(it)), Topic.FRICTION) }
            "add_idea" -> text().ifBlank { null }?.let { board(Place.IDEA, it, ToolCall.of(AddIdeaTool.NAME, AddIdeaArgs.serializer(), AddIdeaArgs(it)), Topic.FRICTION) }
            "add_goal" -> text().ifBlank { null }?.let { board(Place.GOAL, it, ToolCall.of(AddGoalTool.NAME, AddGoalArgs.serializer(), AddGoalArgs(it, KeywordRouter.goalScope(heard))), Topic.GOALS) }
            "add_win" -> text().ifBlank { null }?.let { board(Place.WIN, it, ToolCall.of(AddWinTool.NAME, AddWinArgs.serializer(), AddWinArgs(it)), null) }
            else -> null
        }
    }

    /** The same words put somewhere else (the family tapped another place), or read again after a fix. */
    fun proposal(sentence: String, place: Place, ctx: ToolContext): Proposal {
        val local = ctx.copy(lang = KeywordRouter.language(sentence, ctx.lang))
        val area = AreaWords.extract(sentence)
        val body = area?.text ?: sentence.trim().trimEnd('.', '!').replaceFirstChar { it.uppercase() }
        fun backlog(section: Section): Proposal {
            val (call, args) = backlogCall(body, section, local, fromTalk = true)
            val date = when (args.`when`) {
                When.TODAY -> local.today
                When.TOMORROW -> local.today.plusDays(1)
                When.DAY -> args.date?.let { LocalDate.parse(it) }
                else -> null
            }
            return Proposal(sentence, place, args.text, date, listOf(call), next = args.`when` == When.NEXT, topic = args.section.topic())
        }
        fun board(text: String, call: ToolCall, topic: Topic?) = Proposal(sentence, place, text, null, listOf(call), topic = topic)
        return when (place) {
            Place.PLANNING -> backlog(area?.section?.takeIf { it.boardKind == null } ?: Section.OTHER)
            Place.EVENT -> backlog(Section.APPOINTMENT)
            Place.FREE_TIME -> backlog(Section.PROTECTED_TIME)
            Place.RHYTHM -> backlog(Section.RHYTHM)
            Place.SHOPPING -> {
                val calls = shoppingCalls(body)
                Proposal(sentence, place, calls.joinToString(", ") { ToolJson.decodeFromJsonElement(AddShoppingItemArgs.serializer(), it.arguments).name.replaceFirstChar { c -> c.uppercase() } }, null, calls, topic = Topic.SHOPPING)
            }
            Place.MEAL -> {
                val meal = MealWords.extract(body, local.today) ?: MealWords.extract(sentence, local.today)
                if (meal == null) Proposal(sentence, place, body, null, emptyList(), topic = Topic.MENU)
                else Proposal(sentence, place, meal.dish, meal.date, listOf(ToolCall.of(PlanMealTool.NAME, PlanMealArgs.serializer(), PlanMealArgs(meal.dish, meal.date.toString()))), topic = Topic.MENU)
            }
            Place.HOUSE_RULE -> MeetingWords.ruleText(body).let { board(it, ToolCall.of(AddHouseRuleTool.NAME, AddHouseRuleArgs.serializer(), AddHouseRuleArgs(it)), Topic.RULES) }
            Place.FRICTION -> board(body, ToolCall.of(LogFrictionTool.NAME, LogFrictionArgs.serializer(), LogFrictionArgs(body)), Topic.FRICTION)
            Place.IDEA -> board(body, ToolCall.of(AddIdeaTool.NAME, AddIdeaArgs.serializer(), AddIdeaArgs(body)), Topic.FRICTION)
            Place.GOAL -> board(body, ToolCall.of(AddGoalTool.NAME, AddGoalArgs.serializer(), AddGoalArgs(body, KeywordRouter.goalScope(body))), Topic.GOALS)
            Place.WIN -> board(body, ToolCall.of(AddWinTool.NAME, AddWinArgs.serializer(), AddWinArgs(body)), null)
            Place.NONE -> Proposal(sentence, place, body, null, emptyList())
        }
    }

    /**
     * A conclusion as the family set it in its sheet: their own words for it, the place and the day they chose. The
     * words are kept as typed (a rhythm still reads its days from them, "zwemles elke woensdag"); the chosen day wins
     * over any day word in them.
     */
    fun edited(base: Proposal, text: String, place: Place, date: LocalDate?, next: Boolean, ctx: ToolContext): Proposal {
        val words = text.trim().trimEnd('.', '!').ifEmpty { base.text }.replaceFirstChar { it.uppercase() }
        val whenValue = when {
            date != null -> When.DAY
            next -> When.NEXT
            else -> When.UNPLANNED
        }
        fun backlog(section: Section): Proposal {
            val task = TaskWords.extract(words)
            val chosen = when {
                section != Section.OTHER -> section
                else -> KeywordRouter.section(task.text).takeIf { it.boardKind == null && it != Section.SHOPPING && it != Section.STOCK } ?: Section.OTHER
            }
            val args = AddToBacklogArgs(
                task.text.replaceFirstChar { it.uppercase() }, chosen, whenValue, date?.toString(), urgent = task.urgent,
                intervalDays = task.intervalDays, anchor = task.anchor, weekdays = task.schedule?.weekdays.orEmpty(),
                dayOfMonth = task.schedule?.monthDay, weekOfMonth = task.schedule?.monthWeek, time = task.schedule?.time,
            )
            val call = ToolCall.of(AddToBacklogTool.NAME, AddToBacklogArgs.serializer(), args)
            return Proposal(base.sentence, place, args.text, date, listOf(call), base.heard, next = next && date == null, topic = chosen.topic())
        }
        fun board(call: ToolCall, topic: Topic?) = Proposal(base.sentence, place, words, null, listOf(call), base.heard, topic = topic)
        return when (place) {
            Place.PLANNING -> backlog(Section.OTHER)
            Place.EVENT -> backlog(Section.APPOINTMENT)
            Place.FREE_TIME -> backlog(Section.PROTECTED_TIME)
            Place.RHYTHM -> backlog(Section.RHYTHM)
            Place.SHOPPING -> shoppingCalls(words).let { calls ->
                Proposal(base.sentence, place, words, null, calls, base.heard, topic = Topic.SHOPPING)
            }
            Place.MEAL -> {
                val day = date ?: ctx.today
                Proposal(base.sentence, place, words, day, listOf(ToolCall.of(PlanMealTool.NAME, PlanMealArgs.serializer(), PlanMealArgs(words, day.toString()))), base.heard, topic = Topic.MENU)
            }
            Place.HOUSE_RULE -> board(ToolCall.of(AddHouseRuleTool.NAME, AddHouseRuleArgs.serializer(), AddHouseRuleArgs(words)), Topic.RULES)
            Place.FRICTION -> board(ToolCall.of(LogFrictionTool.NAME, LogFrictionArgs.serializer(), LogFrictionArgs(words)), Topic.FRICTION)
            Place.IDEA -> board(ToolCall.of(AddIdeaTool.NAME, AddIdeaArgs.serializer(), AddIdeaArgs(words)), Topic.FRICTION)
            Place.GOAL -> board(ToolCall.of(AddGoalTool.NAME, AddGoalArgs.serializer(), AddGoalArgs(words, KeywordRouter.goalScope(words))), Topic.GOALS)
            Place.WIN -> board(ToolCall.of(AddWinTool.NAME, AddWinArgs.serializer(), AddWinArgs(words)), null)
            Place.NONE -> Proposal(base.sentence, place, words, null, emptyList(), base.heard)
        }
    }

    /** "we moeten nog melk en eieren halen" -> melk, eieren. */
    private fun shoppingCalls(text: String): List<ToolCall> {
        StockWords.extract(text)?.let { return listOf(ToolCall.of(AddShoppingItemTool.NAME, AddShoppingItemArgs.serializer(), AddShoppingItemArgs(it.name))) }
        parser.parse(text, allowBare = false)?.calls?.filter { it.name == AddShoppingItemTool.NAME }?.takeIf { it.isNotEmpty() }?.let { return it }
        val items = text.replace(SHOPPING_TALK, " ").replace(Regex("\\s+"), " ").trim()
        if (items.isEmpty()) return emptyList()
        return parser.parse(items, allowBare = true)?.calls?.filter { it.name == AddShoppingItemTool.NAME }?.takeIf { it.isNotEmpty() }
            ?: listOf(ToolCall.of(AddShoppingItemTool.NAME, AddShoppingItemArgs.serializer(), AddShoppingItemArgs(items)))
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
        val RECIPE_PREFIX = Regex("^\\s*(?:recept|recipe)\\s*:", RegexOption.IGNORE_CASE)
        val SHOPPING_SECTIONS = setOf(Section.SHOPPING, Section.STOCK)
        val DONE_WORDS = Regex(
            "^(?:de |het |the )?(.+?)\\s+(?:is|zijn|was|are)\\s+(?:gedaan|klaar|geregeld|geboekt|gebeld|done|booked|sorted|finished)[.!]?$",
            RegexOption.IGNORE_CASE,
        )
        val ROUTE_SCHEMA = RouteDecision.serializer().descriptor.toJsonSchema()
        val LINK = Regex("https?://\\S+")
        val RECIPE_WORD = Regex("(?i)\\b(?:recept|recipe)\\b[:\\s]*")
        val WIN_PREFIX = Regex("^(?:dat ging goed|wat ging goed|ging goed|went well|that went well)\\s*[:,\\-]\\s*(.+)$", RegexOption.IGNORE_CASE)
        val SYNC_WORDS = Regex("^(?:nu |nog eens )?(?:synchroniseer|synchroniseren|synchroniseer nu|sync|sync now|synchronise|synchronize|synchronise now|synchronize now)[.!]?$", RegexOption.IGNORE_CASE)
        val SHOPPING_TALK = Regex(
            "\\b(?:we|moeten|moet|nog|wat|even|ook|kopen|halen|gaan|in de winkel|boodschappen|need to|we need|buy|pick up|get some|groceries)\\b",
            RegexOption.IGNORE_CASE,
        )
        const val MAX_STEPS = 5
        const val HISTORY_TURNS = 6
    }
}
