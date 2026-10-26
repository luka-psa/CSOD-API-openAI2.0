package app.hearth.core.tools.backlog

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.model.boardKind
import app.hearth.core.model.stripBoardPrefix
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Anchor
import app.hearth.core.model.title
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.dayLabel
import app.hearth.core.model.dontForget
import app.hearth.core.model.dueRoutines
import app.hearth.core.model.intervalLabel
import app.hearth.core.model.isRoutine
import app.hearth.core.model.TaskKind
import app.hearth.core.model.kind
import app.hearth.core.model.isRoutineStep
import app.hearth.core.model.normalizeName
import app.hearth.core.model.plusDays
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolResult
import app.hearth.core.model.rhythmLabel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** When something should happen, in the words people use. */
enum class When { UNPLANNED, NEXT, TODAY, TOMORROW, DAY, LATER }

internal data class Resolved(val plan: Plan, val date: LocalDate?)

internal fun resolve(whenValue: When, date: String?, ctx: ToolContext): Resolved = when (whenValue) {
    When.UNPLANNED -> Resolved(Plan.UNPLANNED, null)
    When.NEXT -> Resolved(Plan.NEXT, null)
    When.LATER -> Resolved(Plan.LATER, null)
    When.TODAY -> Resolved(Plan.DAY, ctx.today)
    When.TOMORROW -> Resolved(Plan.DAY, ctx.today.plusDays(1))
    When.DAY -> {
        val parsed = date?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
            ?: throw ToolException(ctx.t("Welke dag? (bv. 2026-10-24)", "Which day? (e.g. 2026-10-24)"))
        Resolved(Plan.DAY, parsed)
    }
}

/** "vandaag", "Volgende", "later", "klaar tegen vrijdag". */
fun planLabel(item: BacklogItem, ctx: ToolContext): String {
    val date = item.localDate
    return when (item.plan) {
        Plan.UNPLANNED -> ctx.t("in te plannen", "to plan")
        Plan.NEXT -> ctx.t("volgende", "next up")
        Plan.LATER -> ctx.t("later", "later")
        Plan.DAY -> {
            val day = date?.let { dayLabel(it, ctx.today, ctx.lang) } ?: "?"
            if (item.finishBy) ctx.t("klaar tegen $day", "done by $day") else day
        }
    }
}

/** Finds open items by id, or by text: exact first, then a looser "contains" match. */
internal suspend fun BacklogRepository.find(ids: List<String>, texts: List<String>): List<BacklogItem> {
    val open = openItems()
    val byText = texts.flatMap { t ->
        val key = normalizeName(t)
        open.filter { normalizeName(it.text) == key }.ifEmpty {
            open.filter { normalizeName(it.text).contains(key) || key.contains(normalizeName(it.text)) }
        }
    }
    return (itemsByIds(ids) + byText).distinctBy { it.id }
}

internal fun quoted(items: List<BacklogItem>) = items.joinToString(", ") { "“${it.text}”" }

@Serializable
data class BacklogItemsResult(val items: List<BacklogItem>, override val summary: String) : ToolResult

@Serializable
data class BacklogRefArgs(
    @Describe("Ids of backlog items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of backlog items, when ids are not known.")
    val texts: List<String> = emptyList(),
)

// ---------- add_to_backlog ----------

@Serializable
data class AddToBacklogArgs(
    @Describe("What needs doing, close to the person's words, e.g. 'Tandarts bellen voor Mia'.")
    val text: String,
    @Describe("The section it belongs to. Use OTHER when unsure.")
    val section: Section = Section.OTHER,
    @Describe("When: UNPLANNED (default), NEXT (soon, no day), TODAY, TOMORROW, DAY (with date) or LATER.")
    val `when`: When = When.UNPLANNED,
    @Describe("Only for DAY: the date as yyyy-MM-dd.")
    val date: String? = null,
    @Describe("True when it must be finished by that day rather than done on it.")
    val finishBy: Boolean = false,
    @Describe("Admin that needs attention soon (urgent).")
    val urgent: Boolean = false,
    @Describe("For a routine (CHORE, RHYTHM, HABIT): roughly every how many days, e.g. 7 or 14. Leave empty when unknown.")
    val intervalDays: Int? = null,
    @Describe("For a routine with a moment of the day: MORNING, AFTERNOON (after school), DINNER (around dinner), EVENING or BEDTIME.")
    val anchor: Anchor? = null,
    @Describe(WEEKDAYS_DOC)
    val weekdays: List<DayOfWeek> = emptyList(),
    @Describe(DAY_OF_MONTH_DOC)
    val dayOfMonth: Int? = null,
    @Describe(WEEK_OF_MONTH_DOC)
    val weekOfMonth: Int? = null,
    @Describe(TIME_DOC)
    val time: String? = null,
)

@Serializable
data class AddToBacklogResult(val item: BacklogItem, val added: Boolean, override val summary: String) : ToolResult

class AddToBacklogTool(private val repo: BacklogRepository, private val board: BoardRepository? = null) : Tool<AddToBacklogArgs, AddToBacklogResult> {
    override val name = NAME
    override val description =
        "Put something that needs doing on the shared backlog, labelled with its section. The section decides its list: " +
            "routines (CHORE, RHYTHM, HABIT: comes back now and then, give intervalDays), admin (ADMIN, APPOINTMENT: " +
            "deadline or urgency), projects (PROJECT: bigger, gets steps via add_project_step). Optionally plan it " +
            "(next up, a day, or later). NOTE, FRICTION and GOAL go to the Werkbord. Not for shopping items."
    override val argsSerializer = AddToBacklogArgs.serializer()
    override val resultSerializer = AddToBacklogResult.serializer()

    override suspend fun run(args: AddToBacklogArgs, ctx: ToolContext): AddToBacklogResult {
        val text = args.text.trim()
        if (text.isEmpty()) throw ToolException(ctx.t("Wat moet er gebeuren?", "What needs doing?"))
        // House rules, frictions and goals live on the Werkbord.
        val boardKind = args.section.boardKind
        if (board != null && boardKind != null) {
            val (entry, added) = board.add(boardKind, stripBoardPrefix(text))
            val shown = BacklogItem(entry.id, entry.text, args.section, createdAt = entry.createdAt)
            val label = args.section.label(ctx.lang)
            return AddToBacklogResult(shown, added, if (added) ctx.t("Op het werkbord: “${entry.text}” ($label)", "On the board: “${entry.text}” ($label)") else ctx.t("Staat al op het werkbord: “${entry.text}”", "Already on the board: “${entry.text}”"))
        }
        val (plan, date) = resolve(args.`when`, args.date, ctx)
        val schedule = scheduleOf(args.weekdays, args.dayOfMonth, args.weekOfMonth, args.time, ctx)
        // Fixed days make it a routine.
        val section = if (schedule != null && args.section.kind != TaskKind.ROUTINE) Section.CHORE else args.section
        val routine = section.kind == TaskKind.ROUTINE
        val anchor = args.anchor?.takeIf { routine }
        // A routine with a moment of the day comes back daily unless said otherwise.
        val interval = (args.intervalDays ?: if (anchor != null) 1 else null)?.takeIf { it > 0 && routine }
        val (item, added) = repo.add(
            text, section, plan, date?.toString(), args.finishBy && date != null, ctx.actorMemberId,
            urgent = args.urgent, intervalDays = interval, anchor = anchor, schedule = schedule,
        )
        val label = item.section.label(ctx.lang)
        val summary = if (added) {
            val whenText = when {
                item.plan != Plan.UNPLANNED -> " · ${planLabel(item, ctx)}"
                item.isRoutine() -> (item.anchor?.let { " · ${it.title(ctx.lang).lowercase()}" } ?: "") + " · ${rhythmLabel(item, ctx.lang)}"
                else -> ""
            } + if (item.urgent) ctx.t(" · dringend", " · urgent") else ""
            ctx.t("Bij Taken: “${item.text}” ($label)$whenText", "On the backlog: “${item.text}” ($label)$whenText")
        } else if (args.`when` != When.UNPLANNED && (item.plan != plan || item.date != date?.toString())) {
            // Said again with another day: it moves (a later day counts as postponed).
            repo.plan(listOf(item.id), plan, date?.toString(), args.finishBy && date != null)
            val moved = repo.itemsByIds(listOf(item.id)).first()
            return AddToBacklogResult(moved, false, ctx.t("“${moved.text}” staat nu op ${planLabel(moved, ctx)}.", "“${moved.text}” is now ${planLabel(moved, ctx)}."))
        } else {
            ctx.t("Staat al bij Taken: “${item.text}”", "Already on the backlog: “${item.text}”")
        }
        return AddToBacklogResult(item, added, summary)
    }

    companion object {
        const val NAME = "add_to_backlog"
    }
}

// ---------- list_backlog ----------

enum class BacklogView { ALL, TODAY, NEXT, UNPLANNED, WEEK, DONT_FORGET, ROUTINES_DUE, ROUTINES, ADMIN, PROJECTS, WAITING }

@Serializable
data class ListBacklogArgs(
    @Describe(
        "ALL open items (default), TODAY (planned for today or earlier), NEXT, UNPLANNED, WEEK (next 7 days), " +
            "DONT_FORGET (urgent, deadlines soon, follow-ups due), ROUTINES_DUE (about time again), ROUTINES, ADMIN, " +
            "PROJECTS (with their steps), WAITING (waiting for a reply).",
    )
    val view: BacklogView = BacklogView.ALL,
)

class ListBacklogTool(private val repo: BacklogRepository) : Tool<ListBacklogArgs, BacklogItemsResult> {
    override val name = NAME
    override val description = "Show what is open on the backlog, with section and plan (day, next up, later)."
    override val argsSerializer = ListBacklogArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: ListBacklogArgs, ctx: ToolContext): BacklogItemsResult {
        val weekEnd = ctx.today.plusDays(7)
        val open = repo.openItems()
        if (args.view == BacklogView.DONT_FORGET) return listed(dontForget(open, ctx.today), ctx)
        if (args.view == BacklogView.ROUTINES_DUE) return listed(dueRoutines(open, ctx.today), ctx)
        // Routine steps are listed with their routine, not on their own.
        val items = open.filterNot { it.isRoutineStep }.filter { item ->
            val d = item.localDate
            when (args.view) {
                BacklogView.DONT_FORGET, BacklogView.ROUTINES_DUE -> true
                BacklogView.ROUTINES -> item.isRoutine()
                BacklogView.ADMIN -> item.kind == TaskKind.ADMIN
                BacklogView.PROJECTS -> item.kind == TaskKind.PROJECT
                BacklogView.WAITING -> item.waiting
                BacklogView.ALL -> true
                BacklogView.TODAY -> item.plan == Plan.DAY && d != null && d <= ctx.today
                BacklogView.NEXT -> item.plan == Plan.NEXT
                BacklogView.UNPLANNED -> item.plan == Plan.UNPLANNED
                BacklogView.WEEK -> item.plan == Plan.DAY && d != null && d < weekEnd
            }
        }.sortedWith(compareBy({ it.plan.ordinal }, { it.date ?: "" }))
        return listed(items, ctx)
    }

    private fun listed(items: List<BacklogItem>, ctx: ToolContext): BacklogItemsResult {
        val byId = items.associateBy { it.id }
        val summary = if (items.isEmpty()) {
            ctx.t("Niets open hier.", "Nothing open here.")
        } else {
            items.joinToString("\n") { item ->
                val parent = item.parentId?.let { byId[it]?.text ?: "" }?.let { if (it.isEmpty()) "" else "$it: " } ?: ""
                "$parent${item.text} (${item.section.label(ctx.lang)}, ${describe(item, ctx)})"
            }
        }
        return BacklogItemsResult(items, summary)
    }

    companion object {
        const val NAME = "list_backlog"
    }
}

/** The status in words: plan, rhythm, urgency, waiting. */
fun describe(item: BacklogItem, ctx: ToolContext): String = buildList {
    if (item.isRoutine() && item.plan != Plan.DAY) {
        add(rhythmLabel(item, ctx.lang))
        add(app.hearth.core.model.lastDoneLabel(item, ctx.today, ctx.lang))
    } else if (item.waiting) {
        add(ctx.t("wacht op antwoord", "waiting for a reply"))
        item.localDate?.let { add(ctx.t("opvolgen ${dayLabel(it, ctx.today, ctx.lang)}", "follow up ${dayLabel(it, ctx.today, ctx.lang)}")) }
    } else {
        add(planLabel(item, ctx))
    }
    if (item.urgent) add(ctx.t("dringend", "urgent"))
}.joinToString(", ")

// ---------- plan_backlog_item ----------

@Serializable
data class PlanBacklogArgs(
    @Describe("Ids of backlog items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of backlog items, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe("NEXT (soon, no day), TODAY, TOMORROW, DAY (with date), LATER, or UNPLANNED.")
    val `when`: When,
    @Describe("Only for DAY: the date as yyyy-MM-dd.")
    val date: String? = null,
    @Describe("True when it must be finished by that day rather than done on it.")
    val finishBy: Boolean = false,
)

class PlanBacklogItemTool(private val repo: BacklogRepository) : Tool<PlanBacklogArgs, BacklogItemsResult> {
    override val name = NAME
    override val description = "Decide when backlog items happen: next up, a specific day (do on / finish by), later, or back to unplanned."
    override val argsSerializer = PlanBacklogArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: PlanBacklogArgs, ctx: ToolContext): BacklogItemsResult {
        val items = repo.find(args.itemIds, args.texts)
        if (items.isEmpty()) throw ToolException(ctx.t("Dat vind ik niet bij de taken.", "I can't find that on the backlog."))
        val (plan, date) = resolve(args.`when`, args.date, ctx)
        val finishBy = args.finishBy && date != null
        repo.plan(items.map { it.id }, plan, date?.toString(), finishBy)
        val updated = items.map { it.copy(plan = plan, date = date?.toString(), finishBy = finishBy) }
        val label = planLabel(updated.first(), ctx)
        return BacklogItemsResult(updated, ctx.t("${quoted(updated)} · $label", "${quoted(updated)} · $label"))
    }

    companion object {
        const val NAME = "plan_backlog_item"
    }
}

// ---------- complete / reopen ----------

class CompleteBacklogItemTool(private val repo: BacklogRepository) : Tool<BacklogRefArgs, BacklogItemsResult> {
    override val name = NAME
    override val description =
        "Mark backlog items as done (booked, handled, finished). They leave the backlog and count as done today. " +
            "A routine stays and comes back after its interval; a project takes its open steps along."
    override val argsSerializer = BacklogRefArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: BacklogRefArgs, ctx: ToolContext): BacklogItemsResult {
        val items = repo.find(args.itemIds, args.texts).filterNot { it.done }
        if (items.isEmpty()) throw ToolException(ctx.t("Dat vind ik niet bij de taken.", "I can't find that on the backlog."))
        // Routines come back: log the day instead of closing them.
        val (routines, others) = items.partition { it.isRoutine() || it.isRoutineStep }
        repo.logRoutine(routines.map { it.id }, ctx.today.toString())
        // Every step of a routine ticked today: the routine itself is done for today.
        val parents = routines.filter { it.isRoutineStep }.mapNotNull { it.parentId }.distinct()
        val finished = parents.filter { p -> repo.stepsOf(listOf(p)).all { it.lastDone == ctx.today.toString() } }
        repo.logRoutine(finished, ctx.today.toString())
        repo.setDone(others.map { it.id }, done = true)
        val updated = routines.map { it.copy(lastDone = ctx.today.toString(), plan = Plan.UNPLANNED, date = null, finishBy = false) } + others.map { it.copy(done = true) }
        val summary = buildList {
            if (others.isNotEmpty()) add(ctx.t("Gedaan: ${quoted(others)}. Mooi zo!", "Done: ${quoted(others)}. Nice!"))
            routines.forEach { r ->
                val next = r.intervalDays?.let { rhythmLabel(r, ctx.lang) }?.let { " " + ctx.t("Volgende keer: $it.", "Next time: $it.") } ?: ""
                add(ctx.t("Gedaan: “${r.text}”. Mooi zo!$next", "Done: “${r.text}”. Nice!$next"))
            }
        }.joinToString("\n")
        return BacklogItemsResult(updated, summary)
    }

    companion object {
        const val NAME = "complete_backlog_item"
    }
}

class ReopenBacklogItemTool(private val repo: BacklogRepository) : Tool<BacklogRefArgs, BacklogItemsResult> {
    override val name = NAME
    override val description = "Undo 'done': put backlog items back as open."
    override val argsSerializer = BacklogRefArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: BacklogRefArgs, ctx: ToolContext): BacklogItemsResult {
        val items = repo.itemsByIds(args.itemIds).filter { it.done }
        if (items.isEmpty()) throw ToolException(ctx.t("Niets om terug te zetten.", "Nothing to put back."))
        repo.setDone(items.map { it.id }, done = false)
        return BacklogItemsResult(items.map { it.copy(done = false) }, ctx.t("Terug open: ${quoted(items)}", "Open again: ${quoted(items)}"))
    }

    companion object {
        const val NAME = "reopen_backlog_item"
    }
}

// ---------- remove_backlog_item (destructive) ----------

class RemoveBacklogItemTool(private val repo: BacklogRepository) : Tool<BacklogRefArgs, BacklogItemsResult> {
    override val name = NAME
    override val description =
        "Delete backlog items that were added by mistake (a project's steps go with it). For finished things use complete_backlog_item."
    override val destructive = true
    override val argsSerializer = BacklogRefArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override fun confirmQuestion(args: BacklogRefArgs, ctx: ToolContext): String {
        val what = args.texts.joinToString(", ") { "“$it”" }.ifEmpty { ctx.t("dit", "this") }
        return ctx.t("$what uit Taken verwijderen?", "Delete $what from the backlog?")
    }

    override suspend fun run(args: BacklogRefArgs, ctx: ToolContext): BacklogItemsResult {
        val items = repo.find(args.itemIds, args.texts)
        if (items.isEmpty()) throw ToolException(ctx.t("Dat vind ik niet bij de taken.", "I can't find that on the backlog."))
        repo.delete(items.map { it.id })
        return BacklogItemsResult(items, ctx.t("Verwijderd: ${quoted(items)}", "Deleted: ${quoted(items)}"))
    }

    companion object {
        const val NAME = "remove_backlog_item"
    }
}
