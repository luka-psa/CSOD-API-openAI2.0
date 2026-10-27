package app.hearth.core.tools.backlog

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.HabitReview
import app.hearth.core.model.HabitStage
import app.hearth.core.model.frictionsAbout
import app.hearth.core.model.habitReview
import app.hearth.core.model.isHabit
import app.hearth.core.model.line
import app.hearth.core.model.tag
import app.hearth.core.model.weather
import app.hearth.core.tools.ToolResult
import app.hearth.core.model.boardKind
import app.hearth.core.model.stripBoardPrefix
import app.hearth.core.model.Ease
import app.hearth.core.model.SCORE_MAX
import app.hearth.core.model.easeOf
import app.hearth.core.model.score
import app.hearth.core.model.scoreWord
import app.hearth.core.model.title
import app.hearth.core.model.Anchor
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.topic
import app.hearth.core.model.TaskKind
import app.hearth.core.model.dayLabel
import app.hearth.core.model.intervalLabel
import app.hearth.core.model.isRoutine
import app.hearth.core.model.kind
import app.hearth.core.model.normalizeName
import app.hearth.core.model.plusDays
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.model.Schedule
import app.hearth.core.model.rhythmLabel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// Tools for the three kinds of open work: routines, admin and projects.

private fun notFound(ctx: ToolContext): Nothing =
    throw ToolException(ctx.t("Dat vind ik niet bij de taken.", "I can't find that on the backlog."))

// ---------- set_routine_interval ----------

@Serializable
data class SetRoutineIntervalArgs(
    @Describe("Ids of routines.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of routines, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe("Roughly every how many days: 1 daily, 7 weekly, 14, 30 monthly, 90 per season. Empty: now and then.")
    val days: Int? = null,
)

class SetRoutineIntervalTool(private val repo: BacklogRepository) : Tool<SetRoutineIntervalArgs, BacklogItemsResult> {
    override val name = NAME
    override val description = "Set how often a routine comes back (sheets every 2 weeks, mowing every week). Routines are never late."
    override val argsSerializer = SetRoutineIntervalArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: SetRoutineIntervalArgs, ctx: ToolContext): BacklogItemsResult {
        val found = repo.find(args.itemIds, args.texts).ifEmpty { notFound(ctx) }
        val days = args.days?.takeIf { it > 0 }
        // Giving something a rhythm makes it a routine.
        val toMove = found.filter { !it.isRoutine() && !it.isStep }
        repo.setSection(toMove.map { it.id }, Section.CHORE)
        val items = found.filter { !it.isStep }.ifEmpty { notFound(ctx) }
        repo.setInterval(items.map { it.id }, days)
        val updated = items.map { if (it.isRoutine()) it.copy(intervalDays = days) else it.copy(section = Section.CHORE, intervalDays = days) }
        return BacklogItemsResult(updated, "${quoted(updated)} · ${intervalLabel(days, ctx.lang)}")
    }

    companion object {
        const val NAME = "set_routine_interval"
    }
}

// ---------- set_routine_schedule ----------

internal const val WEEKDAYS_DOC = "For a routine on fixed days of the week, e.g. [THURSDAY] or [MONDAY, WEDNESDAY, FRIDAY]."
internal const val DAY_OF_MONTH_DOC = "For a monthly routine on a date: 1–31 ('de 1ste van de maand' = 1)."
internal const val WEEK_OF_MONTH_DOC = "For a monthly routine on the nth weekday (give one weekday): 1–4, or -1 for the last ('eerste zaterdag' = 1)."
internal const val TIME_DOC = "Only when it really happens at a fixed time, as HH:mm ('20:00'). Most routines have a moment of the day instead."

/** Fixed days from tool arguments; a time without days means every day at that time. Null: no fixed days. */
internal fun scheduleOf(weekdays: List<DayOfWeek>, dayOfMonth: Int?, weekOfMonth: Int?, time: String?, ctx: ToolContext): Schedule? {
    if (dayOfMonth != null && dayOfMonth !in 1..31) throw ToolException(ctx.t("Een dag van de maand gaat van 1 tot 31.", "A day of the month is 1 to 31."))
    if (weekOfMonth != null && weekOfMonth !in listOf(1, 2, 3, 4, -1)) throw ToolException(ctx.t("Kies de eerste tot vierde, of de laatste.", "Pick the first to fourth, or the last."))
    val clock = time?.takeIf { it.isNotBlank() }?.let {
        Schedule.cleanTime(it) ?: throw ToolException(ctx.t("Dat uur begrijp ik niet. Bijvoorbeeld 20:00.", "I don't understand that time. For example 20:00."))
    }
    return when {
        dayOfMonth != null -> Schedule(monthDay = dayOfMonth, time = clock)
        weekOfMonth != null && weekdays.isNotEmpty() -> Schedule(listOf(weekdays.first()), monthWeek = weekOfMonth, time = clock)
        weekdays.isNotEmpty() -> Schedule(weekdays.distinct().sorted(), time = clock)
        clock != null -> Schedule(DayOfWeek.entries.toList(), time = clock)
        else -> null
    }
}

@Serializable
data class SetRoutineScheduleArgs(
    @Describe("Ids of routines.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of routines, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe(WEEKDAYS_DOC)
    val weekdays: List<DayOfWeek> = emptyList(),
    @Describe(DAY_OF_MONTH_DOC)
    val dayOfMonth: Int? = null,
    @Describe(WEEK_OF_MONTH_DOC)
    val weekOfMonth: Int? = null,
    @Describe(TIME_DOC)
    val time: String? = null,
)

/**
 * Fixed days for a routine ("vuilbak buiten elke donderdag", "zwemles elke woensdag om 14:00", "huur de 1ste van de
 * maand"). On its day it is about time; after it, it stays open until done or its next day. No days: back to roughly
 * weekly.
 */
class SetRoutineScheduleTool(private val repo: BacklogRepository) : Tool<SetRoutineScheduleArgs, BacklogItemsResult> {
    override val name = NAME
    override val description =
        "Give a routine fixed days: weekdays, a date of the month or the nth weekday of the month, and a time only when " +
            "it is really fixed. No days: back to 'roughly every week'. Routines are never late."
    override val argsSerializer = SetRoutineScheduleArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: SetRoutineScheduleArgs, ctx: ToolContext): BacklogItemsResult {
        val schedule = scheduleOf(args.weekdays, args.dayOfMonth, args.weekOfMonth, args.time, ctx)
        val found = repo.find(args.itemIds, args.texts).filter { !it.isStep }.ifEmpty { notFound(ctx) }
        // Fixed days make it a routine.
        repo.setSection(found.filter { !it.isRoutine() }.map { it.id }, Section.CHORE)
        repo.setSchedule(found.map { it.id }, schedule)
        val updated = repo.itemsByIds(found.map { it.id })
        return BacklogItemsResult(updated, "${quoted(updated)} · ${updated.firstOrNull()?.let { rhythmLabel(it, ctx.lang) } ?: ""}")
    }

    companion object {
        const val NAME = "set_routine_schedule"
    }
}

// ---------- mark_urgent ----------

@Serializable
data class MarkUrgentArgs(
    @Describe("Ids of backlog items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of backlog items, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe("True to mark urgent (default), false to take the flag off.")
    val urgent: Boolean = true,
)

class MarkUrgentTool(private val repo: BacklogRepository) : Tool<MarkUrgentArgs, BacklogItemsResult> {
    override val name = NAME
    override val description = "Flag items as urgent so they show under 'Don't forget' on the dashboard, or take the flag off."
    override val argsSerializer = MarkUrgentArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: MarkUrgentArgs, ctx: ToolContext): BacklogItemsResult {
        val items = repo.find(args.itemIds, args.texts).ifEmpty { notFound(ctx) }
        repo.setUrgent(items.map { it.id }, args.urgent)
        val updated = items.map { it.copy(urgent = args.urgent) }
        val summary = if (args.urgent) ctx.t("Dringend: ${quoted(updated)}", "Urgent: ${quoted(updated)}")
        else ctx.t("Niet meer dringend: ${quoted(updated)}", "No longer urgent: ${quoted(updated)}")
        return BacklogItemsResult(updated, summary)
    }

    companion object {
        const val NAME = "mark_urgent"
    }
}

// ---------- wait_for_reply ----------

@Serializable
data class WaitForReplyArgs(
    @Describe("Ids of backlog items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of backlog items, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe("Follow-up day as yyyy-MM-dd. Default: in 3 days.")
    val followUp: String? = null,
    @Describe("False when the reply came in: the item is open again, to plan.")
    val waiting: Boolean = true,
)

class WaitForReplyTool(private val repo: BacklogRepository) : Tool<WaitForReplyArgs, BacklogItemsResult> {
    override val name = NAME
    override val description =
        "Park admin while waiting for someone (garage, insurance, school). It comes back under 'Don't forget' on the follow-up day."
    override val argsSerializer = WaitForReplyArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: WaitForReplyArgs, ctx: ToolContext): BacklogItemsResult {
        val items = repo.find(args.itemIds, args.texts).filterNot { it.isRoutine() }.ifEmpty { notFound(ctx) }
        val ids = items.map { it.id }
        if (!args.waiting) {
            repo.setWaiting(ids, waiting = false, followUp = null)
            repo.plan(ids, Plan.NEXT, null, false)
            val updated = items.map { it.copy(waiting = false, plan = Plan.NEXT, date = null, finishBy = false) }
            return BacklogItemsResult(updated, ctx.t("Antwoord binnen: ${quoted(updated)} staat bij volgende", "Reply in: ${quoted(updated)} is next up"))
        }
        val day = args.followUp?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
            ?: ctx.today.plusDays(DEFAULT_FOLLOW_UP_DAYS)
        repo.setWaiting(ids, waiting = true, followUp = day.toString())
        val updated = items.map { it.copy(waiting = true, plan = Plan.DAY, date = day.toString(), finishBy = false) }
        val label = dayLabel(day, ctx.today, ctx.lang)
        return BacklogItemsResult(updated, ctx.t("${quoted(updated)} wacht op antwoord · opvolgen $label", "${quoted(updated)} is waiting for a reply · follow up $label"))
    }

    companion object {
        const val NAME = "wait_for_reply"
        const val DEFAULT_FOLLOW_UP_DAYS = 3
    }
}

// ---------- move_backlog_item ----------

@Serializable
data class MoveBacklogArgs(
    @Describe("Ids of backlog items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of backlog items, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe("The section it really belongs to, e.g. CHORE (routine), ADMIN, PROJECT.")
    val section: Section,
)

class MoveBacklogItemTool(private val repo: BacklogRepository, private val board: BoardRepository? = null) : Tool<MoveBacklogArgs, BacklogItemsResult> {
    override val name = NAME
    override val description = "Move items to another section when they were sorted wrongly (e.g. from 'to sort' to a routine or project)."
    override val argsSerializer = MoveBacklogArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: MoveBacklogArgs, ctx: ToolContext): BacklogItemsResult {
        val items = repo.find(args.itemIds, args.texts).filterNot { it.isStep }.ifEmpty { notFound(ctx) }
        val boardKind = args.section.boardKind
        if (board != null && boardKind != null) {
            // A house rule, friction or goal moves to the Werkbord.
            items.forEach { board.add(boardKind, stripBoardPrefix(it.text)) }
            repo.delete(items.map { it.id })
            return BacklogItemsResult(items.map { it.copy(section = args.section) }, ctx.t("${quoted(items)} staat nu op het werkbord (${args.section.label(ctx.lang)})", "${quoted(items)} is now on the board (${args.section.label(ctx.lang)})"))
        }
        repo.setSection(items.map { it.id }, args.section)
        val updated = items.map { it.copy(section = args.section) }
        // Named like the panel it now shows in ("Grote taken"), so the words on screen and in chat agree.
        val place = args.section.topic()?.title(ctx.lang) ?: args.section.label(ctx.lang)
        return BacklogItemsResult(updated, ctx.t("${quoted(updated)} staat nu bij $place", "${quoted(updated)} is now under $place"))
    }

    companion object {
        const val NAME = "move_backlog_item"
    }
}

// ---------- add_project_step ----------

@Serializable
data class AddProjectStepArgs(
    @Describe("The project, by name, e.g. 'IKEA kast'. Created when it doesn't exist yet.")
    val project: String = "",
    @Describe("Id of the project, when known.")
    val projectId: String? = null,
    @Describe("The step, e.g. 'Verf halen bij Gamma' or 'Kast ophalen'.")
    val text: String,
    @Describe("When: UNPLANNED (default), NEXT, TODAY, TOMORROW, DAY (with date) or LATER.")
    val `when`: When = When.UNPLANNED,
    @Describe("Only for DAY: the date as yyyy-MM-dd.")
    val date: String? = null,
)

class AddProjectStepTool(private val repo: BacklogRepository) : Tool<AddProjectStepArgs, AddToBacklogResult> {
    override val name = NAME
    override val description =
        "Add a step to a project (prep like buying paint, the job itself, a follow-up). Creates the project if needed."
    override val argsSerializer = AddProjectStepArgs.serializer()
    override val resultSerializer = AddToBacklogResult.serializer()

    override suspend fun run(args: AddProjectStepArgs, ctx: ToolContext): AddToBacklogResult {
        val text = args.text.trim()
        if (text.isEmpty()) throw ToolException(ctx.t("Welke stap?", "Which step?"))
        val open = repo.openItems().filter { !it.isStep && it.kind == TaskKind.PROJECT }
        val key = normalizeName(args.project)
        val project = args.projectId?.let { id -> open.firstOrNull { it.id == id } }
            ?: open.firstOrNull { key.isNotEmpty() && normalizeName(it.text) == key }
            ?: open.firstOrNull { key.isNotEmpty() && normalizeName(it.text).contains(key) }
            ?: if (args.project.isNotBlank()) {
                repo.add(args.project.trim(), Section.PROJECT, Plan.UNPLANNED, null, false, ctx.actorMemberId).first
            } else {
                throw ToolException(ctx.t("Bij welk project?", "For which project?"))
            }
        val (plan, date) = resolve(args.`when`, args.date, ctx)
        val (step, added) = repo.add(text, Section.PROJECT, plan, date?.toString(), false, ctx.actorMemberId, parentId = project.id)
        val summary = if (added) ctx.t("Stap bij “${project.text}”: “${step.text}”", "Step for “${project.text}”: “${step.text}”")
        else ctx.t("Die stap staat er al: “${step.text}”", "That step is already there: “${step.text}”")
        return AddToBacklogResult(step, added, summary)
    }

    companion object {
        const val NAME = "add_project_step"
    }
}

// ---------- rate_habit ----------

@Serializable
data class RateHabitArgs(
    @Describe("Ids of habits.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of habits, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe("How it goes, from 0 (komt er niet van) to 5 (loopt vanzelf).")
    val score: Int? = null,
    @Describe("Older form: EASY (loopt vanzelf), EFFORT (vraagt moeite) or HARD (te zwaar). Use score instead.")
    val ease: Ease? = null,
)

/**
 * Habits are lived, not ticked off; the family says how hard one is. Atomic Habits: a hard habit asks for a
 * smaller first step, not more effort.
 */
class RateHabitTool(
    private val repo: BacklogRepository,
    private val meeting: MeetingRepository,
    private val board: BoardRepository,
) : Tool<RateHabitArgs, BacklogItemsResult> {
    override val name = NAME
    override val description =
        "Say how a habit goes for the household, from 0 (komt er niet van) to 5 (loopt vanzelf). Shown as its score " +
            "with its weather (bliksem 0–1, wolk 2–3, zon 4–5; never red); one score per meeting is kept for review_habit, " +
            "and the answer says the one thing to try next."
    override val argsSerializer = RateHabitArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: RateHabitArgs, ctx: ToolContext): BacklogItemsResult {
        val score = (args.score ?: args.ease?.score)?.coerceIn(0, SCORE_MAX)
            ?: throw ToolException(ctx.t("Hoe loopt het, van 0 tot 5?", "How does it go, from 0 to 5?"))
        val found = repo.find(args.itemIds, args.texts).ifEmpty { notFound(ctx) }
        repo.setScore(found.map { it.id }, score)
        // One score per meeting is kept, so the habit coach sees how it goes over the weeks.
        found.forEach { meeting.setHabitCheck(it.id, score) }
        val names = found.joinToString(", ") { "“${it.text}”" }
        // One habit: the coach's one thing to try, the same as on the screen.
        val tip = found.singleOrNull()?.takeIf { it.isHabit }?.let { reviewHabits(listOf(it), repo, meeting, board, ctx).single() }
            ?.takeIf { it.stage != HabitStage.NEW && it.stage != HabitStage.STEADY }?.tips?.firstOrNull()?.text
        val ease = easeOf(score)
        val said = "$names: ${scoreWord(score, ctx.lang)} ($score/$SCORE_MAX)."
        val summary = when (ease) {
            Ease.EASY -> "$said " + ctx.t("Mooi.", "Nice.") + (tip?.let { " $it" } ?: "")
            Ease.EFFORT -> "$said " + (tip ?: ctx.t("Een kleinere eerste stap kan helpen.", "A smaller first step can help."))
            Ease.HARD -> "$said " + (tip ?: ctx.t("Wat is een kleinere eerste stap?", "What's a smaller first step?"))
        }
        return BacklogItemsResult(found.map { it.copy(ease = ease, score = score) }, summary)
    }

    companion object {
        const val NAME = "rate_habit"
    }
}

// ---------- add_routine ----------

@Serializable
data class AddRoutineArgs(
    @Describe("The routine's name, e.g. 'Avondroutine' or 'Tas voor de opvang'.")
    val name: String,
    @Describe("Its steps in order (a sequence like boodschappen, Mia ophalen, koken, pyjama) or the lines of a checklist (luiers, flesjes, reservekleren).")
    val steps: List<String> = emptyList(),
    @Describe("The moment of the day: MORNING, AFTERNOON, DINNER, EVENING or BEDTIME. Empty: whenever it fits.")
    val anchor: Anchor? = null,
    @Describe("RHYTHM (default), HABIT or CHORE.")
    val section: Section = Section.RHYTHM,
    @Describe("Roughly every how many days. Default: daily with a moment of the day; none for a checklist without a moment (used when needed).")
    val intervalDays: Int? = null,
    @Describe(WEEKDAYS_DOC)
    val weekdays: List<DayOfWeek> = emptyList(),
    @Describe(DAY_OF_MONTH_DOC)
    val dayOfMonth: Int? = null,
    @Describe(WEEK_OF_MONTH_DOC)
    val weekOfMonth: Int? = null,
    @Describe(TIME_DOC)
    val time: String? = null,
)

/**
 * A routine with steps: the evening sequence or the bag for the opvang. Saying it again adds the steps
 * that are missing, so a routine grows as the family thinks of things.
 */
class AddRoutineTool(private val repo: BacklogRepository) : Tool<AddRoutineArgs, AddToBacklogResult> {
    override val name = NAME
    override val description =
        "Lay out a routine with its steps in order (the evening: boodschappen, Mia ophalen, koken, pyjama) or a " +
            "checklist (the bag for the babysitter: luiers, flesjes, reservekleren), on a moment of the day. Steps are " +
            "ticked per day and come back tomorrow. Calling it again for the same routine adds the missing steps."
    override val argsSerializer = AddRoutineArgs.serializer()
    override val resultSerializer = AddToBacklogResult.serializer()

    override suspend fun run(args: AddRoutineArgs, ctx: ToolContext): AddToBacklogResult {
        val title = args.name.trim().replaceFirstChar { it.uppercase() }.ifEmpty { throw ToolException(ctx.t("Hoe heet de routine?", "What's the routine called?")) }
        val section = args.section.takeIf { it.kind == TaskKind.ROUTINE } ?: Section.RHYTHM
        // A moment of the day makes it daily; without one it's a checklist for when it's needed (the bag for the babysit).
        val interval = args.intervalDays ?: if (args.anchor != null) 1 else null
        val schedule = scheduleOf(args.weekdays, args.dayOfMonth, args.weekOfMonth, args.time, ctx)
        val (routine, added) = repo.add(title, section, Plan.UNPLANNED, null, false, ctx.actorMemberId, intervalDays = interval, anchor = args.anchor, schedule = schedule)
        if (!added && args.anchor != null && routine.anchor != args.anchor) repo.setAnchor(listOf(routine.id), args.anchor)
        if (!added && schedule != null && routine.schedule != schedule) repo.setSchedule(listOf(routine.id), schedule)
        val steps = args.steps.map { it.trim().trim('.', ',').replaceFirstChar { c -> c.uppercase() } }.filter { it.isNotEmpty() }
        val new = steps.count { step -> repo.add(step, routine.section, Plan.UNPLANNED, null, false, ctx.actorMemberId, parentId = routine.id).second }
        val moment = (schedule?.label(ctx.lang) ?: (args.anchor ?: routine.anchor)?.title(ctx.lang)?.lowercase())?.let { " ($it)" } ?: ""
        val summary = when {
            added -> ctx.t("Routine “${routine.text}”$moment: ", "Routine “${routine.text}”$moment: ") + steps.joinToString(" → ").ifEmpty { ctx.t("nog zonder stappen", "no steps yet") }
            new > 0 -> ctx.t("$new ${if (new == 1) "stap" else "stappen"} erbij in “${routine.text}”.", "$new ${if (new == 1) "step" else "steps"} added to “${routine.text}”.")
            else -> ctx.t("“${routine.text}” heeft die stappen al.", "“${routine.text}” already has those steps.")
        }
        return AddToBacklogResult(routine, added, summary)
    }

    companion object {
        const val NAME = "add_routine"
    }
}

// ---------- move_step ----------

@Serializable
data class MoveStepArgs(
    @Describe("Id of the step.")
    val stepId: String? = null,
    @Describe("The step's words, when the id isn't known.")
    val step: String? = null,
    @Describe("The routine or big task it belongs to (name or id); needed to find a step by its words.")
    val parent: String? = null,
    @Describe("Its new place in the order: 1 = first.")
    val position: Int,
)

/** The evening goes in the order of its steps: put one at another place ("avond eten voor Ada naar bed"). */
class MoveStepTool(private val repo: BacklogRepository) : Tool<MoveStepArgs, BacklogItemsResult> {
    override val name = NAME
    override val description =
        "Change the order of a routine's (or a big task's) steps: put a step at a new place, 1 = first. Routines are " +
            "shown and ticked in this order."
    override val argsSerializer = MoveStepArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: MoveStepArgs, ctx: ToolContext): BacklogItemsResult {
        val step = args.stepId?.let { id -> repo.byIds(listOf(id)).firstOrNull { it.isStep } } ?: run {
            val words = args.step?.let(::normalizeName)?.ifEmpty { null } ?: notFound(ctx)
            val parent = args.parent?.trim()?.ifEmpty { null }?.let { p -> repo.find(listOf(p), listOf(p)).firstOrNull { !it.isStep } }
                ?: throw ToolException(ctx.t("Bij welke routine hoort die stap?", "Which routine does that step belong to?"))
            val siblings = repo.stepsOf(listOf(parent.id))
            siblings.firstOrNull { normalizeName(it.text) == words } ?: siblings.firstOrNull { normalizeName(it.text).contains(words) } ?: notFound(ctx)
        }
        val order = repo.moveStep(step.id, args.position - 1)
        val place = order.indexOfFirst { it.id == step.id } + 1
        val parentName = repo.byIds(listOfNotNull(step.parentId)).firstOrNull()?.text ?: ""
        return BacklogItemsResult(
            order,
            ctx.t("“${step.text}” is nu stap $place van “$parentName”: ", "“${step.text}” is now step $place of “$parentName”: ") + order.joinToString(" → ") { it.text },
        )
    }

    companion object {
        const val NAME = "move_step"
    }
}

// ---------- set_anchor ----------

@Serializable
data class SetAnchorArgs(
    @Describe("Ids of routines.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of routines, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe("MORNING, AFTERNOON (after school), DINNER (around dinner), EVENING or BEDTIME. Empty: whenever it fits.")
    val anchor: Anchor? = null,
)

/** Hangs a routine on a moment of the day, so the morning and evening routines can be laid out in order. */
class SetAnchorTool(private val repo: BacklogRepository) : Tool<SetAnchorArgs, BacklogItemsResult> {
    override val name = NAME
    override val description =
        "Put a routine (habit, rhythm or chore) on a moment of the day: the morning routine, after school, around " +
            "dinner, the evening or bedtime. Never a clock time."
    override val argsSerializer = SetAnchorArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: SetAnchorArgs, ctx: ToolContext): BacklogItemsResult {
        val found = repo.find(args.itemIds, args.texts).filter { it.isRoutine() }.ifEmpty { notFound(ctx) }
        repo.setAnchor(found.map { it.id }, args.anchor)
        val names = found.joinToString(", ") { "“${it.text}”" }
        val summary = args.anchor?.let { ctx.t("$names hoort nu bij ${it.title(ctx.lang).lowercase()}.", "$names now belongs to ${it.title(ctx.lang).lowercase()}.") }
            ?: ctx.t("$names past nu wanneer het uitkomt.", "$names now fits whenever it suits.")
        return BacklogItemsResult(found.map { it.copy(anchor = args.anchor) }, summary)
    }

    companion object {
        const val NAME = "set_anchor"
    }
}

// ---------- set_small_step ----------

@Serializable
data class SmallStepArgs(
    @Describe("Ids of habits or tasks.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe("A smaller first step, e.g. 'twee minuten opruimen na het eten'. Empty removes it.")
    val step: String = "",
)

/** "Begin klein: …": the smallest version of a habit or a task that keeps getting pushed back. */
class SetSmallStepTool(private val repo: BacklogRepository) : Tool<SmallStepArgs, BacklogItemsResult> {
    override val name = NAME
    override val description = "Give a hard habit, or a task that keeps being postponed, a smaller first step to start with."
    override val argsSerializer = SmallStepArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: SmallStepArgs, ctx: ToolContext): BacklogItemsResult {
        val found = repo.find(args.itemIds, args.texts).ifEmpty { notFound(ctx) }
        val step = args.step.trim().replaceFirstChar { it.uppercase() }.ifEmpty { null }
        repo.setSmallStep(found.map { it.id }, step)
        val summary = if (step == null) ctx.t("Kleine stap weggehaald.", "Small step removed.")
        else ctx.t("Begin klein: “$step”.", "Start small: “$step”.")
        return BacklogItemsResult(found.map { it.copy(smallStep = step) }, summary)
    }

    companion object {
        const val NAME = "set_small_step"
    }
}

// ---------- set_habit_pairing ----------

@Serializable
data class SetPairingArgs(
    @Describe("Ids of habits.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of habits, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe("Something nice that goes with it: 'met muziek', 'samen met Mia', 'daarna koffie'. Empty removes it.")
    val pairing: String = "",
)

/** Atomic Habits' second law, make it attractive: something nice that goes with the habit, shown with it. */
class SetHabitPairingTool(private val repo: BacklogRepository) : Tool<SetPairingArgs, BacklogItemsResult> {
    override val name = NAME
    override val description =
        "Make a habit attractive (the second law of Atomic Habits): pair it with something nice, like music, doing it " +
            "together or a treat after. Shown with the habit on the screen."
    override val argsSerializer = SetPairingArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: SetPairingArgs, ctx: ToolContext): BacklogItemsResult {
        val found = repo.find(args.itemIds, args.texts).filter { it.isRoutine() }.ifEmpty { notFound(ctx) }
        val pairing = args.pairing.trim().trimEnd('.').replaceFirstChar { it.lowercase() }.ifEmpty { null }
        repo.setPairing(found.map { it.id }, pairing)
        val names = found.joinToString(", ") { "“${it.text}”" }
        val summary = pairing?.let { ctx.t("$names, en erbij: $it.", "$names, and with it: $it.") }
            ?: ctx.t("$names zonder iets erbij.", "$names without anything with it.")
        return BacklogItemsResult(found.map { it.copy(pairing = pairing) }, summary)
    }

    companion object {
        const val NAME = "set_habit_pairing"
    }
}

// ---------- review_habit ----------

@Serializable
data class ReviewHabitArgs(
    @Describe("Id of the habit.")
    val itemId: String? = null,
    @Describe("The habit's name, when the id isn't known. Empty with no id: every habit that asks for something.")
    val text: String? = null,
)

@Serializable
data class ReviewHabitResult(val items: List<BacklogItem>, val reviews: List<HabitReview>, override val summary: String) : ToolResult

/** The habit coach for the habits (or all of them), the same as on the tablet. */
internal suspend fun reviewHabits(habits: List<BacklogItem>, backlog: BacklogRepository, meeting: MeetingRepository, board: BoardRepository, ctx: ToolContext): List<HabitReview> {
    val boardItems = board.allItems()
    val steps = backlog.stepsOf(habits.map { it.id }).groupBy { it.parentId }
    return habits.map { h ->
        val goal = boardItems.firstOrNull { it.kind == BoardKind.GOAL && it.isOpen && it.linkedId == h.id }?.text
        habitReview(h, meeting.habitHistory(h.id), frictionsAbout(h, boardItems, ctx.today), steps[h.id]?.size ?: 0, goal, ctx.lang)
    }
}

/**
 * Atomic Habits' four laws for one habit: its score over the meetings, the frictions about it, how each law is in
 * place, and what to try next. Read-only, so a family's own AI can think it through with them and then change one
 * thing with the other tools.
 */
class ReviewHabitTool(
    private val backlog: BacklogRepository,
    private val meeting: MeetingRepository,
    private val board: BoardRepository,
) : Tool<ReviewHabitArgs, ReviewHabitResult> {
    override val name = NAME
    override val description =
        "Review a habit with the four laws of Atomic Habits (make it obvious, attractive, easy, satisfying): its score " +
            "(0–5) per weekoverleg, the frictions talked about it, how each law is in place, and Hearth's suggestion. Without a " +
            "habit: every habit that asks for something. Suggest one change at a time and apply it with set_anchor or " +
            "set_routine_schedule (obvious), set_habit_pairing (attractive), set_small_step, set_routine_interval or " +
            "move_step (easy) and add_win (satisfying). Smaller when it is too much; one step bigger only after it ran " +
            "easily three meetings in a row (the last one a 5). Never streaks, never blame."
    override val argsSerializer = ReviewHabitArgs.serializer()
    override val resultSerializer = ReviewHabitResult.serializer()

    override suspend fun run(args: ReviewHabitArgs, ctx: ToolContext): ReviewHabitResult {
        val asked = args.itemId != null || !args.text.isNullOrBlank()
        val habits = if (asked) {
            backlog.find(listOfNotNull(args.itemId), listOfNotNull(args.text?.trim()?.ifEmpty { null })).filter { it.isRoutine() }.ifEmpty { notFound(ctx) }
        } else {
            backlog.openItems().filter { it.isHabit }
        }
        val all = reviewHabits(habits, backlog, meeting, board, ctx)
        val shown = if (asked) all else all.filter { it.tips.isNotEmpty() }
        val items = shown.mapNotNull { r -> habits.firstOrNull { it.id == r.itemId } }
        if (shown.isEmpty()) {
            return ReviewHabitResult(emptyList(), emptyList(), ctx.t("Alle gewoontes lopen. Niets aan veranderen.", "Every habit runs. Nothing to change."))
        }
        val summary = shown.joinToString("\n\n") { r -> describeReview(items.first { it.id == r.itemId }, r, ctx) }
        return ReviewHabitResult(items, shown, summary)
    }

    companion object {
        const val NAME = "review_habit"
    }
}

/** A few lines a person (or their AI) can read: the stage, the weather, what was said, the four laws, what to try. */
private fun describeReview(habit: BacklogItem, r: HabitReview, ctx: ToolContext): String = buildList {
    add("“${habit.text}” · ${r.stage.line(ctx.lang)}")
    if (r.ratings.isNotEmpty()) add(ctx.t("Score per overleg (0–5): ", "Score per meeting (0–5): ") + r.ratings.takeLast(6).joinToString(", ") { "$it (${easeOf(it).weather(ctx.lang)})" })
    if (r.frictions.isNotEmpty()) add(ctx.t("Besproken: ", "Talked about: ") + r.frictions.takeLast(3).joinToString("; ") { "“$it”" })
    add(r.laws.joinToString(" · ") { l -> "${l.law.title(ctx.lang)}: ${l.now ?: ctx.t("nog niets", "nothing yet")}" })
    r.tips.forEach { add(ctx.t("Probeer", "Try") + " (${it.tag(ctx.lang)}): ${it.text}") }
}.joinToString("\n")
