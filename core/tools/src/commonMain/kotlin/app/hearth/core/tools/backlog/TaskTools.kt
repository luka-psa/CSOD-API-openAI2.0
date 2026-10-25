package app.hearth.core.tools.backlog

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.model.boardKind
import app.hearth.core.model.stripBoardPrefix
import app.hearth.core.model.Ease
import app.hearth.core.model.title
import app.hearth.core.model.Anchor
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
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
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// Tools for the three kinds of open work: routines, admin and projects.

private fun notFound(ctx: ToolContext): Nothing =
    throw ToolException(ctx.t("Dat vind ik niet op de backlog.", "I can't find that on the backlog."))

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
        return BacklogItemsResult(updated, ctx.t("${quoted(updated)} staat nu bij ${args.section.label(ctx.lang)}", "${quoted(updated)} is now under ${args.section.label(ctx.lang)}"))
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
    @Describe("EASY (loopt vanzelf), EFFORT (vraagt moeite) or HARD (te zwaar: make it smaller).")
    val ease: Ease,
)

/**
 * Habits are lived, not ticked off; the family says how hard one is. Atomic Habits: a hard habit asks for a
 * smaller first step, not more effort.
 */
class RateHabitTool(private val repo: BacklogRepository) : Tool<RateHabitArgs, BacklogItemsResult> {
    override val name = NAME
    override val description =
        "Say how hard a habit is for the household: EASY, EFFORT or HARD. Shown as a colour (never red); a hard " +
            "habit gets a smaller first step (set_small_step)."
    override val argsSerializer = RateHabitArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: RateHabitArgs, ctx: ToolContext): BacklogItemsResult {
        val found = repo.find(args.itemIds, args.texts).ifEmpty { notFound(ctx) }
        repo.setEase(found.map { it.id }, args.ease)
        val names = found.joinToString(", ") { "“${it.text}”" }
        val summary = when (args.ease) {
            Ease.EASY -> ctx.t("$names loopt vanzelf. Mooi.", "$names runs easily. Nice.")
            Ease.EFFORT -> ctx.t("$names vraagt moeite. Een kleinere eerste stap kan helpen.", "$names takes effort. A smaller first step can help.")
            Ease.HARD -> ctx.t("$names is te zwaar. Wat is een kleinere eerste stap?", "$names is too much. What's a smaller first step?")
        }
        return BacklogItemsResult(found.map { it.copy(ease = args.ease) }, summary)
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
    @Describe("Roughly every how many days. Default: daily.")
    val intervalDays: Int? = null,
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
        val (routine, added) = repo.add(title, section, Plan.UNPLANNED, null, false, ctx.actorMemberId, intervalDays = args.intervalDays ?: 1, anchor = args.anchor)
        if (!added && args.anchor != null && routine.anchor != args.anchor) repo.setAnchor(listOf(routine.id), args.anchor)
        val steps = args.steps.map { it.trim().trim('.', ',').replaceFirstChar { c -> c.uppercase() } }.filter { it.isNotEmpty() }
        val new = steps.count { step -> repo.add(step, routine.section, Plan.UNPLANNED, null, false, ctx.actorMemberId, parentId = routine.id).second }
        val moment = (args.anchor ?: routine.anchor)?.let { " (${it.title(ctx.lang).lowercase()})" } ?: ""
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
