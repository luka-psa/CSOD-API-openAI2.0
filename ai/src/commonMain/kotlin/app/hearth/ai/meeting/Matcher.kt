package app.hearth.ai.meeting

import app.hearth.ai.Orchestrator
import app.hearth.ai.interpret.MeetingReader
import app.hearth.ai.route.Place
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.Plan
import app.hearth.core.model.isRoutine
import app.hearth.core.model.normalizeName
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolJson
import app.hearth.core.tools.backlog.AddToBacklogArgs
import app.hearth.core.tools.backlog.AddToBacklogTool
import app.hearth.core.tools.backlog.PlanBacklogArgs
import app.hearth.core.tools.backlog.PlanBacklogItemTool
import app.hearth.core.tools.backlog.SetAnchorArgs
import app.hearth.core.tools.backlog.SetAnchorTool
import app.hearth.core.tools.backlog.SetRoutineScheduleArgs
import app.hearth.core.tools.backlog.SetRoutineScheduleTool
import app.hearth.core.tools.backlog.When
import app.hearth.core.tools.food.AddShoppingItemArgs
import app.hearth.core.tools.food.AddShoppingItemTool

/**
 * A conclusion next to what the household already has. Talk is mostly about what exists ("de vaatwasser", "onze
 * avondroutine", "de huisregel over schoenen"): then the conclusion changes that item (a new day, another moment of the
 * day, fixed days) instead of adding it twice, or, when it brings nothing new, says it is there already. The family can
 * still add it as new (Toch nieuw).
 */
object Matcher {
    fun match(p: Orchestrator.Proposal, h: Household): Orchestrator.Proposal {
        if (p.calls.isEmpty() || p.match != null) return p
        val words = contentWords(p.text)
        return when (p.place) {
            Place.PLANNING, Place.EVENT, Place.FREE_TIME, Place.RHYTHM -> {
                val item = best(words, h.backlog.filter { !it.done && !it.isStep }) { it.text } ?: return p
                backlogChange(p, item)
            }
            Place.HOUSE_RULE -> same(p, best(words, h.board.filter { it.isOpen && it.kind == BoardKind.RULE }) { it.text }?.let { it.id to it.text })
            Place.FRICTION, Place.IDEA ->
                same(p, best(words, h.board.filter { it.isOpen && (it.kind == BoardKind.FRICTION || it.kind == BoardKind.IDEA) }) { it.text }?.let { it.id to it.text })
            Place.GOAL -> same(p, best(words, h.board.filter { it.isOpen && it.kind == BoardKind.GOAL }) { it.text }?.let { it.id to it.text })
            Place.SHOPPING -> shopping(p, h)
            Place.MEAL -> {
                val day = p.date?.toString()
                val meal = h.meals.firstOrNull { it.date == day && similar(contentWords(it.dish), words) } ?: return p
                same(p, meal.id to meal.dish)
            }
            Place.WIN, Place.NONE -> p
        }
    }

    /** A task gets the new day; a routine its moment or its days; with nothing new it is there already. */
    private fun backlogChange(p: Orchestrator.Proposal, item: BacklogItem): Orchestrator.Proposal {
        val add = p.calls.firstOrNull { it.name == AddToBacklogTool.NAME }?.let { ToolJson.decodeFromJsonElement(AddToBacklogArgs.serializer(), it.arguments) }
        val ids = listOf(item.id)
        val calls = mutableListOf<ToolCall>()
        if (item.isRoutine()) {
            add?.anchor?.takeIf { it != item.anchor }?.let { calls += ToolCall.of(SetAnchorTool.NAME, SetAnchorArgs.serializer(), SetAnchorArgs(ids, anchor = it)) }
            add?.weekdays?.takeIf { it.isNotEmpty() && it.toSet() != item.schedule?.weekdays?.toSet() }?.let {
                calls += ToolCall.of(SetRoutineScheduleTool.NAME, SetRoutineScheduleArgs.serializer(), SetRoutineScheduleArgs(ids, weekdays = it, time = add.time))
            }
        } else {
            val day = p.date?.toString()
            when {
                day != null && day != item.date -> calls += ToolCall.of(PlanBacklogItemTool.NAME, PlanBacklogArgs.serializer(), PlanBacklogArgs(ids, `when` = When.DAY, date = day))
                day == null && p.next && item.plan != Plan.NEXT && item.plan != Plan.DAY ->
                    calls += ToolCall.of(PlanBacklogItemTool.NAME, PlanBacklogArgs.serializer(), PlanBacklogArgs(ids, `when` = When.NEXT))
            }
        }
        return p.copy(calls = calls, match = Orchestrator.Existing(item.id, item.text, update = calls.isNotEmpty(), asNew = p.calls))
    }

    /** What is on the list already isn't added again; all of it there: nothing to add. */
    private fun shopping(p: Orchestrator.Proposal, h: Household): Orchestrator.Proposal {
        fun onList(call: ToolCall): String? {
            if (call.name != AddShoppingItemTool.NAME) return null
            val name = normalizeName(ToolJson.decodeFromJsonElement(AddShoppingItemArgs.serializer(), call.arguments).name)
            // "appel" and "appels" are one thing; "melk" and "melkpoeder" are two.
            return h.shopping.firstOrNull { normalizeName(it).let { l -> l == name || (kotlin.math.abs(l.length - name.length) <= 2 && MeetingReader.sameWord(l, name)) } }
        }
        val there = p.calls.mapNotNull(::onList)
        if (there.isEmpty()) return p
        val rest = p.calls.filter { onList(it) == null }
        return if (rest.isEmpty()) p.copy(calls = emptyList(), match = Orchestrator.Existing("shopping", there.joinToString(", "), update = false, asNew = p.calls))
        else p.copy(calls = rest)
    }

    private fun same(p: Orchestrator.Proposal, found: Pair<String, String>?): Orchestrator.Proposal =
        found?.let { (id, text) -> p.copy(calls = emptyList(), match = Orchestrator.Existing(id, text, update = false, asNew = p.calls)) } ?: p

    /** The item that [words] name best, if they clearly name one. */
    private fun <T> best(words: List<String>, items: List<T>, text: (T) -> String): T? =
        items.map { it to score(words, contentWords(text(it))) }.filter { it.second > 0f }.maxByOrNull { it.second }?.first

    private fun similar(a: List<String>, b: List<String>) = score(b, a) > 0f

    /**
     * How well [said] names an item with [item] words: the share of the item's words that were said. Half of them at
     * least, and either two words or one long one ("vaatwasser" names "Vaatwasser uitladen"; "maken" names nothing).
     */
    fun score(said: List<String>, item: List<String>): Float {
        val hit = item.filter { w -> said.any { MeetingReader.sameWord(it, w) } }
        val share = overlap(said, item)
        return if (share >= 0.5f && (hit.size >= 2 || hit.any { it.length >= 6 })) share else 0f
    }

    /** The share of [b]'s words that [a] has too. */
    fun overlap(a: List<String>, b: List<String>): Float =
        if (a.isEmpty() || b.isEmpty()) 0f else b.count { w -> a.any { MeetingReader.sameWord(it, w) } }.toFloat() / b.size

    /** Two forms of one word: "bel" and "bellen", "uitladen" and "uitgeladen", "poetsen" and "poetst". */
    fun sameRoot(a: String, b: String): Boolean = a == b || MeetingReader.sameWord(a, b) ||
        (minOf(a.length, b.length) >= 3 && (a.startsWith(b) || b.startsWith(a))) ||
        (minOf(a.length, b.length) >= 6 && app.hearth.core.model.WordFixes.distance(a, b) <= 2)

    /** The words that carry meaning: three letters or more, not the small talk around them ("Mia" counts). */
    fun contentWords(text: String): List<String> =
        Regex("[\\p{L}\\p{N}'’-]+").findAll(text.lowercase()).map { it.value }.filter { it.length >= 3 && it !in MeetingReader.STOP_WORDS && it !in SMALL }.toList()

    private val SMALL = setOf("het", "een", "der", "des", "den", "wel", "nog", "dan", "dat", "die", "wat", "zo", "the", "and", "for", "but", "not", "you", "are", "was", "ofzo", "enzo", "zoiets", "hè", "allee", "kan", "zal", "gaat", "had", "mijn", "uw", "jouw", "zijn", "haar", "hun")
}
