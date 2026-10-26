package app.hearth.core.tools.board

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.model.BoardItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.Anchor
import app.hearth.core.model.BoardStatus
import kotlinx.datetime.LocalDate
import app.hearth.core.model.intervalLabel
import app.hearth.core.model.dayLabel
import app.hearth.core.model.FrictionDirection
import app.hearth.core.model.isRoutine
import app.hearth.core.model.title
import app.hearth.core.tools.backlog.AddRoutineArgs
import app.hearth.core.tools.backlog.AddRoutineTool
import app.hearth.core.tools.backlog.find
import app.hearth.core.model.GoalScope
import app.hearth.core.model.Member
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.TaskKind
import app.hearth.core.model.kind
import app.hearth.core.model.normalizeName
import app.hearth.core.model.stripBoardPrefix
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolResult
import kotlinx.serialization.Serializable

// The Werkbord: house rules, frictions and goals, plus who lives here.

@Serializable
data class BoardItemsResult(val items: List<BoardItem>, override val summary: String) : ToolResult

@Serializable
data class BoardRefArgs(
    @Describe("Ids of Werkbord items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of Werkbord items, when ids are not known.")
    val texts: List<String> = emptyList(),
)

private fun quoted(items: List<BoardItem>) = items.joinToString(", ") { "“${it.text}”" }

private fun notFound(ctx: ToolContext): Nothing =
    throw ToolException(ctx.t("Dat vind ik niet op het werkbord.", "I can't find that on the board."))

/** Open items by id or text (exact first, then contains), optionally of one kind. */
private suspend fun BoardRepository.find(ids: List<String>, texts: List<String>, kind: BoardKind? = null): List<BoardItem> {
    val all = allItems().filter { kind == null || it.kind == kind }
    val open = all.filter { it.isOpen }
    val byText = texts.flatMap { t ->
        val key = normalizeName(stripBoardPrefix(t))
        open.filter { normalizeName(it.text) == key }.ifEmpty {
            open.filter { normalizeName(it.text).contains(key) || key.contains(normalizeName(it.text)) }
        }
    }
    return (all.filter { it.id in ids } + byText).distinctBy { it.id }
}

internal suspend fun BoardRepository.member(name: String?, ctx: ToolContext): Member? {
    if (name.isNullOrBlank()) return null
    val key = normalizeName(name)
    return allMembers().firstOrNull { normalizeName(it.name) == key || it.id == name }
        ?: throw ToolException(ctx.t("Wie is $name? Voeg die persoon eerst toe.", "Who is $name? Add them first."))
}

// ---------- members ----------

@Serializable
data class AddMemberArgs(@Describe("First name, e.g. 'Sam'.") val name: String)

@Serializable
data class MembersResult(val members: List<Member>, override val summary: String) : ToolResult

class AddMemberTool(private val repo: BoardRepository) : Tool<AddMemberArgs, MembersResult> {
    override val name = NAME
    override val description = "Add someone to the household, so goals and frictions can belong to them."
    override val argsSerializer = AddMemberArgs.serializer()
    override val resultSerializer = MembersResult.serializer()

    override suspend fun run(args: AddMemberArgs, ctx: ToolContext): MembersResult {
        val name = args.name.trim().replaceFirstChar { it.uppercase() }
        if (name.isEmpty()) throw ToolException(ctx.t("Hoe heet die persoon?", "What's their name?"))
        val (member, added) = repo.addMember(name)
        val summary = if (added) ctx.t("Welkom, ${member.name}!", "Welcome, ${member.name}!")
        else ctx.t("${member.name} hoort er al bij.", "${member.name} is already here.")
        return MembersResult(listOf(member), summary)
    }

    companion object {
        const val NAME = "add_member"
    }
}

@Serializable
data class RemoveMemberArgs(@Describe("Name or id of the person.") val name: String)

class RemoveMemberTool(private val repo: BoardRepository) : Tool<RemoveMemberArgs, MembersResult> {
    override val name = NAME
    override val description = "Remove someone from the household. Their personal goals become family goals."
    override val destructive = true
    override val argsSerializer = RemoveMemberArgs.serializer()
    override val resultSerializer = MembersResult.serializer()

    override fun confirmQuestion(args: RemoveMemberArgs, ctx: ToolContext) =
        ctx.t("${args.name} uit het huishouden halen?", "Remove ${args.name} from the household?")

    override suspend fun run(args: RemoveMemberArgs, ctx: ToolContext): MembersResult {
        val member = repo.member(args.name, ctx)!!
        repo.removeMember(member.id)
        return MembersResult(listOf(member), ctx.t("${member.name} is uit de lijst.", "${member.name} was removed."))
    }

    companion object {
        const val NAME = "remove_member"
    }
}

// ---------- add_house_rule / log_friction / add_goal ----------

@Serializable
data class AddHouseRuleArgs(@Describe("The agreement, e.g. 'Geen schermen na 20u'.") val text: String)

@Serializable
data class BoardItemResult(val item: BoardItem, val added: Boolean, override val summary: String) : ToolResult

class AddHouseRuleTool(private val repo: BoardRepository) : Tool<AddHouseRuleArgs, BoardItemResult> {
    override val name = NAME
    override val description = "Write down a house rule the household agreed on."
    override val argsSerializer = AddHouseRuleArgs.serializer()
    override val resultSerializer = BoardItemResult.serializer()

    override suspend fun run(args: AddHouseRuleArgs, ctx: ToolContext): BoardItemResult {
        val text = stripBoardPrefix(args.text).ifEmpty { throw ToolException(ctx.t("Welke huisregel?", "Which house rule?")) }
        val (item, added) = repo.add(BoardKind.RULE, text)
        return BoardItemResult(item, added, if (added) ctx.t("Nieuwe huisregel: “${item.text}”", "New house rule: “${item.text}”") else ctx.t("Die huisregel staat er al.", "That house rule is already there."))
    }

    companion object {
        const val NAME = "add_house_rule"
    }
}

@Serializable
data class LogFrictionArgs(
    @Describe("What bothers someone, in their words, e.g. 'Rommel in de gang'.")
    val text: String,
    @Describe("Who noticed it (name), if known.")
    val noticedBy: String? = null,
)

class LogFrictionTool(private val repo: BoardRepository) : Tool<LogFrictionArgs, BoardItemResult> {
    override val name = NAME
    override val description =
        "Note a friction: something someone noticed and doesn't like. It waits on the Werkbord for the weekoverleg, " +
            "where it becomes a house rule, a change, or is let go."
    override val argsSerializer = LogFrictionArgs.serializer()
    override val resultSerializer = BoardItemResult.serializer()

    override suspend fun run(args: LogFrictionArgs, ctx: ToolContext): BoardItemResult {
        val text = stripBoardPrefix(args.text).ifEmpty { throw ToolException(ctx.t("Wat stoort er?", "What's bothering you?")) }
        val who = repo.member(args.noticedBy, ctx)?.id ?: ctx.actorMemberId
        val (item, added) = repo.add(BoardKind.FRICTION, text, memberId = who)
        val summary = if (added) ctx.t("Op het werkbord: “${item.text}”. We bekijken het in het weekoverleg.", "On the board: “${item.text}”. We'll look at it in the weekly meeting.")
        else ctx.t("Staat al op het werkbord: “${item.text}”", "Already on the board: “${item.text}”")
        return BoardItemResult(item, added, summary)
    }

    companion object {
        const val NAME = "log_friction"
    }
}

@Serializable
data class AddIdeaArgs(
    @Describe("A way to make the household easier or quicker, e.g. 'De was op zondag, dan is maandag rustig'.")
    val text: String,
    @Describe("Whose idea (name), if known.")
    val by: String? = null,
)

class AddIdeaTool(private val repo: BoardRepository) : Tool<AddIdeaArgs, BoardItemResult> {
    override val name = NAME
    override val description =
        "Note an idea to make things easier or quicker. Like a friction it waits on the Werkbord for the weekoverleg, " +
            "where it becomes a house rule, a change, or is let go."
    override val argsSerializer = AddIdeaArgs.serializer()
    override val resultSerializer = BoardItemResult.serializer()

    override suspend fun run(args: AddIdeaArgs, ctx: ToolContext): BoardItemResult {
        val text = stripBoardPrefix(args.text).ifEmpty { throw ToolException(ctx.t("Welk idee?", "Which idea?")) }
        val who = repo.member(args.by, ctx)?.id ?: ctx.actorMemberId
        val (item, added) = repo.add(BoardKind.IDEA, text, memberId = who)
        val summary = if (added) ctx.t("Goed idee, het staat op het werkbord: “${item.text}”", "Good idea, it's on the board: “${item.text}”")
        else ctx.t("Dat idee staat er al: “${item.text}”", "That idea is already there: “${item.text}”")
        return BoardItemResult(item, added, summary)
    }

    companion object {
        const val NAME = "add_idea"
    }
}

@Serializable
data class AddGoalArgs(
    @Describe("The goal, e.g. 'Elke maand een avondje met z’n twee'.")
    val text: String,
    @Describe("FAMILY (default), COUPLE (the two of us) or PERSONAL (one person, give member).")
    val scope: GoalScope = GoalScope.FAMILY,
    @Describe("For PERSONAL: whose goal (name).")
    val member: String? = null,
    @Describe("Optional first small step.")
    val nextStep: String? = null,
)

class AddGoalTool(private val repo: BoardRepository) : Tool<AddGoalArgs, BoardItemResult> {
    override val name = NAME
    override val description = "Add a personal, couple or family goal, optionally with one small next step."
    override val argsSerializer = AddGoalArgs.serializer()
    override val resultSerializer = BoardItemResult.serializer()

    override suspend fun run(args: AddGoalArgs, ctx: ToolContext): BoardItemResult {
        val text = stripBoardPrefix(args.text).ifEmpty { throw ToolException(ctx.t("Welk doel?", "Which goal?")) }
        val member = repo.member(args.member, ctx)
        val scope = if (member != null) GoalScope.PERSONAL else args.scope
        val (item, added) = repo.add(BoardKind.GOAL, text, memberId = member?.id, scope = scope, nextStep = args.nextStep?.trim()?.ifEmpty { null })
        val whose = when (scope) {
            GoalScope.FAMILY -> ctx.t("gezin", "family")
            GoalScope.COUPLE -> ctx.t("samen", "together")
            GoalScope.PERSONAL -> member?.name ?: ctx.t("persoonlijk", "personal")
        }
        val summary = if (added) ctx.t("Nieuw doel ($whose): “${item.text}”", "New goal ($whose): “${item.text}”")
        else ctx.t("Dat doel staat er al.", "That goal is already there.")
        return BoardItemResult(item, added, summary)
    }

    companion object {
        const val NAME = "add_goal"
    }
}

// ---------- list_board ----------

@Serializable
data class ListBoardArgs(
    @Describe("RULE, FRICTION, GOAL or IDEA; leave empty for everything open.")
    val kind: BoardKind? = null,
)

class ListBoardTool(private val repo: BoardRepository) : Tool<ListBoardArgs, BoardItemsResult> {
    override val name = NAME
    override val description = "Show the Werkbord: house rules, open frictions and goals (with their next step), and who lives here."
    override val argsSerializer = ListBoardArgs.serializer()
    override val resultSerializer = BoardItemsResult.serializer()

    override suspend fun run(args: ListBoardArgs, ctx: ToolContext): BoardItemsResult {
        val names = repo.allMembers().associate { it.id to it.name }
        val items = repo.allItems().filter { it.isOpen && (args.kind == null || it.kind == args.kind) }
        if (items.isEmpty()) return BoardItemsResult(items, ctx.t("Het werkbord is nog leeg.", "The board is still empty."))
        val summary = BoardKind.entries.mapNotNull { kind ->
            val ofKind = items.filter { it.kind == kind }.ifEmpty { return@mapNotNull null }
            val title = when (kind) {
                BoardKind.RULE -> ctx.t("Huisregels", "House rules")
                BoardKind.FRICTION -> ctx.t("Frictie", "Frictions")
                BoardKind.GOAL -> ctx.t("Doelen", "Goals")
                BoardKind.IDEA -> ctx.t("Ideeën", "Ideas")
            }
            title + ":\n" + ofKind.joinToString("\n") { item ->
                val who = item.memberId?.let(names::get)?.let { " ($it)" } ?: ""
                val step = item.nextStep?.let { ctx.t(" · volgende stap: $it", " · next step: $it") } ?: ""
                val needs = item.direction?.let { " · " + directionWords(it, ctx) } ?: ""
                "- ${item.text}$who$step$needs"
            }
        }.joinToString("\n")
        return BoardItemsResult(items, summary)
    }

    companion object {
        const val NAME = "list_board"
    }
}

// ---------- resolve_friction ----------

/** RULE, CHANGE (a task), ROUTINE (a new routine), ROUTINE_CHANGE (adjust one) or LET_GO. */
enum class FrictionOutcome { RULE, CHANGE, LET_GO, ROUTINE, ROUTINE_CHANGE }

@Serializable
data class ResolveFrictionArgs(
    @Describe("Ids of frictions.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of frictions, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe(
        "RULE (we agree on a house rule), CHANGE (a task with focus: paperwork or a big task, as next up), ROUTINE " +
            "(a new routine), ROUTINE_CHANGE (adjust an existing routine: a step and/or another moment) or LET_GO.",
    )
    val outcome: FrictionOutcome,
    @Describe(
        "In the family's own words, usually phrased differently from the friction. RULE: the rule. CHANGE: the task. " +
            "ROUTINE: the routine's name. ROUTINE_CHANGE: a step to add (optional). Defaults to the friction's text, except for ROUTINE_CHANGE.",
    )
    val text: String? = null,
    @Describe("For CHANGE: ADMIN (admin), PROJECT (grote taak, default) or CHORE.")
    val section: Section = Section.PROJECT,
    @Describe("For ROUTINE_CHANGE: the routine to adjust (its name or id).")
    val routine: String? = null,
    @Describe("For ROUTINE and ROUTINE_CHANGE: the moment of the day (MORNING, AFTERNOON, DINNER, EVENING, BEDTIME).")
    val anchor: Anchor? = null,
)

class ResolveFrictionTool(
    private val repo: BoardRepository,
    private val backlog: BacklogRepository,
    private val addRoutine: AddRoutineTool,
) : Tool<ResolveFrictionArgs, BoardItemsResult> {
    override val name = NAME
    override val description =
        "Decide what to do about a friction or an idea, in the family's own words: a house rule, a task with focus (next up), " +
            "a new routine, a change to a routine, or let it go."
    override val argsSerializer = ResolveFrictionArgs.serializer()
    override val resultSerializer = BoardItemsResult.serializer()

    override suspend fun run(args: ResolveFrictionArgs, ctx: ToolContext): BoardItemsResult {
        val frictions = (repo.find(args.itemIds, args.texts, BoardKind.FRICTION) + repo.find(args.itemIds, args.texts, BoardKind.IDEA))
            .filter { it.isOpen }.distinctBy { it.id }.ifEmpty { notFound(ctx) }
        val custom = args.text?.let(::stripBoardPrefix)?.ifEmpty { null }
        // Which routine changes, found once for all frictions.
        val routine = if (args.outcome != FrictionOutcome.ROUTINE_CHANGE) null else {
            val ref = args.routine?.trim()?.ifEmpty { null } ?: throw ToolException(ctx.t("Welke routine passen we aan?", "Which routine do we adjust?"))
            backlog.find(listOf(ref), listOf(ref)).firstOrNull { it.isRoutine() }
                ?: throw ToolException(ctx.t("Die routine vind ik niet. Kies er een uit Ritmes & routines.", "I can't find that routine. Pick one from Rhythms & routines."))
        }
        if (routine != null && custom == null && args.anchor == null) {
            throw ToolException(ctx.t("Wat verandert er: een stap erbij, of een ander moment?", "What changes: an extra step, or another moment?"))
        }
        val lines = frictions.map { friction ->
            val words = custom ?: friction.text
            when (args.outcome) {
                FrictionOutcome.RULE -> {
                    val rule = repo.add(BoardKind.RULE, words).first
                    repo.close(friction.id, BoardStatus.BECAME_RULE, rule.id)
                    ctx.t("“${friction.text}” wordt huisregel: “${rule.text}”", "“${friction.text}” becomes a house rule: “${rule.text}”")
                }
                FrictionOutcome.CHANGE -> {
                    val section = args.section.takeIf { it.kind != TaskKind.SORT } ?: Section.PROJECT
                    val task = backlog.add(words, section, Plan.NEXT, null, false, ctx.actorMemberId).first
                    repo.close(friction.id, BoardStatus.BECAME_CHANGE, task.id)
                    ctx.t("“${friction.text}”: we pakken het aan. “${task.text}” staat bij volgende.", "“${friction.text}”: we're on it. “${task.text}” is next up.")
                }
                FrictionOutcome.ROUTINE -> {
                    val made = addRoutine.run(AddRoutineArgs(words, anchor = args.anchor), ctx).item
                    repo.close(friction.id, BoardStatus.BECAME_ROUTINE, made.id)
                    val moment = made.anchor?.let { " (${it.title(ctx.lang).lowercase()})" } ?: ""
                    ctx.t("“${friction.text}” wordt een routine: “${made.text}”$moment", "“${friction.text}” becomes a routine: “${made.text}”$moment")
                }
                FrictionOutcome.ROUTINE_CHANGE -> {
                    val r = routine!!
                    addRoutine.run(AddRoutineArgs(r.text, steps = listOfNotNull(custom), anchor = args.anchor, section = r.section), ctx)
                    repo.close(friction.id, BoardStatus.BECAME_ROUTINE, r.id)
                    val what = listOfNotNull(
                        custom?.let { ctx.t("stap erbij: $it", "new step: $it") },
                        args.anchor?.let { ctx.t("nu ", "now ") + it.title(ctx.lang).lowercase() },
                    ).joinToString(", ")
                    ctx.t("“${friction.text}”: “${r.text}” past zich aan ($what)", "“${friction.text}”: “${r.text}” changes ($what)")
                }
                FrictionOutcome.LET_GO -> {
                    repo.close(friction.id, BoardStatus.LET_GO)
                    ctx.t("“${friction.text}” laten we los. Ook dat is een keuze.", "Letting “${friction.text}” go. That's a choice too.")
                }
            }
        }
        val status = when (args.outcome) {
            FrictionOutcome.RULE -> BoardStatus.BECAME_RULE
            FrictionOutcome.CHANGE -> BoardStatus.BECAME_CHANGE
            FrictionOutcome.ROUTINE, FrictionOutcome.ROUTINE_CHANGE -> BoardStatus.BECAME_ROUTINE
            FrictionOutcome.LET_GO -> BoardStatus.LET_GO
        }
        return BoardItemsResult(frictions.map { it.copy(status = status) }, lines.joinToString("\n"))
    }

    companion object {
        const val NAME = "resolve_friction"
    }
}

// ---------- mark_friction ----------

@Serializable
data class MarkFrictionArgs(
    @Describe("Ids of frictions or ideas.")
    val itemIds: List<String> = emptyList(),
    @Describe("Their texts, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe(
        "What it needs: RULE (a house rule to write), NEW_ROUTINE, CHANGE_ROUTINE (adjust an existing routine) or TASK " +
            "(a task with focus). Empty: back to 'still to talk about'.",
    )
    val direction: FrictionDirection? = null,
)

/** "Huisregel te maken", "routine aan te passen": the words for a direction, in chat and summaries. */
internal fun directionWords(direction: FrictionDirection, ctx: ToolContext): String = when (direction) {
    FrictionDirection.RULE -> ctx.t("huisregel te maken", "house rule to write")
    FrictionDirection.NEW_ROUTINE -> ctx.t("nieuwe routine te maken", "new routine to make")
    FrictionDirection.CHANGE_ROUTINE -> ctx.t("routine aan te passen", "routine to adjust")
    FrictionDirection.TASK -> ctx.t("taak te maken", "task to make")
}

/**
 * The meeting decides what a friction needs in one tap, before anyone words it: the rule or routine is usually
 * phrased differently from the complaint. It stays open, with its direction, until it's written (resolve_friction).
 */
class MarkFrictionTool(private val repo: BoardRepository) : Tool<MarkFrictionArgs, BoardItemsResult> {
    override val name = NAME
    override val description =
        "In the weekoverleg: choose what a friction or idea needs, before anyone words it: a house rule to write, a new " +
            "routine, a change to a routine, or a task with focus. It stays open with that label until resolve_friction " +
            "writes it in the family's own words."
    override val argsSerializer = MarkFrictionArgs.serializer()
    override val resultSerializer = BoardItemsResult.serializer()

    override suspend fun run(args: MarkFrictionArgs, ctx: ToolContext): BoardItemsResult {
        val frictions = (repo.find(args.itemIds, args.texts, BoardKind.FRICTION) + repo.find(args.itemIds, args.texts, BoardKind.IDEA))
            .filter { it.isOpen }.distinctBy { it.id }.ifEmpty { notFound(ctx) }
        repo.setDirection(frictions.map { it.id }, args.direction)
        val summary = when (val d = args.direction) {
            null -> ctx.t("${quoted(frictions)} staat weer bij te bespreken.", "${quoted(frictions)} is back to talk about.")
            else -> ctx.t("${quoted(frictions)}: ${directionWords(d, ctx)}. Schrijf het in jullie eigen woorden wanneer jullie willen.",
                "${quoted(frictions)}: ${directionWords(d, ctx)}. Write it in your own words whenever you like.")
        }
        return BoardItemsResult(frictions.map { it.copy(direction = args.direction) }, summary)
    }

    companion object {
        const val NAME = "mark_friction"
    }
}

// ---------- set_goal_step / achieve_goal ----------

/** How a goal gets a place in the week: one small step, a habit (yoga every morning) or free time on a day (guitar on Saturday). */
enum class GoalPlanKind { STEP, HABIT, FREE_TIME }

@Serializable
data class SetGoalStepArgs(
    @Describe("Id of the goal.")
    val goalId: String? = null,
    @Describe("The goal's text, when the id is not known.")
    val goal: String = "",
    @Describe("In the family's words: the small step, the habit (e.g. '10 minuten yoga') or what the free time is for (e.g. 'Gitaar spelen').")
    val step: String,
    @Describe("STEP: also put the step on the backlog as next up, so it shows on the dashboard.")
    val toWeek: Boolean = false,
    @Describe(
        "How it gets a place in the week: STEP (one small step, default), HABIT (a routine at a moment of the day, e.g. " +
            "yoga every morning) or FREE_TIME (time for it on a day, e.g. a hobby on Saturday).",
    )
    val how: GoalPlanKind = GoalPlanKind.STEP,
    @Describe("HABIT: the moment of the day (MORNING, AFTERNOON, DINNER, EVENING, BEDTIME).")
    val anchor: Anchor? = null,
    @Describe("HABIT: every how many days; 1 = daily (default), 7 = weekly.")
    val intervalDays: Int? = null,
    @Describe("FREE_TIME: the day, ISO date (YYYY-MM-DD).")
    val date: String? = null,
)

/**
 * A goal comes into the week the way it fits: often a habit ("elke dag yoga" in Ritmes & routines) or free time
 * for a hobby (in Vrije tijd, on a day), otherwise one small step. The goal keeps a link to it.
 */
class SetGoalStepTool(
    private val repo: BoardRepository,
    private val backlog: BacklogRepository,
    private val addRoutine: AddRoutineTool,
) : Tool<SetGoalStepArgs, BoardItemsResult> {
    override val name = NAME
    override val description =
        "Give a goal its place in the week: a habit at a moment of the day (lands in Ritmes & routines), free time for " +
            "it on a day (lands in Vrije tijd), or one small next step (optionally next up)."
    override val argsSerializer = SetGoalStepArgs.serializer()
    override val resultSerializer = BoardItemsResult.serializer()

    override suspend fun run(args: SetGoalStepArgs, ctx: ToolContext): BoardItemsResult {
        val goal = repo.find(listOfNotNull(args.goalId), listOf(args.goal).filter { it.isNotBlank() }, BoardKind.GOAL).firstOrNull { it.isOpen } ?: notFound(ctx)
        val step = args.step.trim().replaceFirstChar { it.uppercase() }.ifEmpty { throw ToolException(ctx.t("Welke kleine stap?", "Which small step?")) }
        val (linked, summary) = when (args.how) {
            GoalPlanKind.STEP -> {
                val linked = if (args.toWeek) backlog.add(step, Section.ADMIN, Plan.NEXT, null, false, ctx.actorMemberId).first.id else goal.linkedId
                val week = if (args.toWeek) ctx.t(" Staat bij volgende.", " It's next up.") else ""
                linked to ctx.t("“${goal.text}” · volgende stap: $step.$week", "“${goal.text}” · next step: $step.$week")
            }
            GoalPlanKind.HABIT -> {
                val interval = args.intervalDays?.coerceAtLeast(1) ?: 1
                val habit = addRoutine.run(AddRoutineArgs(step, anchor = args.anchor, section = Section.HABIT, intervalDays = interval), ctx).item
                val details = listOfNotNull(habit.anchor?.title(ctx.lang)?.lowercase(), intervalLabel(interval, ctx.lang)).joinToString(", ")
                habit.id to ctx.t("“${goal.text}” krijgt een ritme: “${habit.text}” ($details).", "“${goal.text}” gets a rhythm: “${habit.text}” ($details).")
            }
            GoalPlanKind.FREE_TIME -> {
                val day = args.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: throw ToolException(ctx.t("Op welke dag maken jullie er tijd voor?", "On which day do you make time for it?"))
                val free = backlog.add(step, Section.PROTECTED_TIME, Plan.DAY, day.toString(), false, ctx.actorMemberId).first
                free.id to ctx.t("“${goal.text}” krijgt vrije tijd: “${free.text}” (${dayLabel(day, ctx.today, ctx.lang)}).", "“${goal.text}” gets free time: “${free.text}” (${dayLabel(day, ctx.today, ctx.lang)}).")
            }
        }
        repo.setNextStep(goal.id, step, linked)
        return BoardItemsResult(listOf(goal.copy(nextStep = step, linkedId = linked)), summary)
    }

    companion object {
        const val NAME = "set_goal_step"
    }
}

class AchieveGoalTool(private val repo: BoardRepository) : Tool<BoardRefArgs, BoardItemsResult> {
    override val name = NAME
    override val description = "Mark a goal as reached. It stays on the board as a win."
    override val argsSerializer = BoardRefArgs.serializer()
    override val resultSerializer = BoardItemsResult.serializer()

    override suspend fun run(args: BoardRefArgs, ctx: ToolContext): BoardItemsResult {
        val goals = repo.find(args.itemIds, args.texts, BoardKind.GOAL).filter { it.isOpen }.ifEmpty { notFound(ctx) }
        goals.forEach { repo.close(it.id, BoardStatus.ACHIEVED) }
        return BoardItemsResult(goals.map { it.copy(status = BoardStatus.ACHIEVED) }, ctx.t("Doel bereikt: ${quoted(goals)}. Proficiat!", "Goal reached: ${quoted(goals)}. Well done!"))
    }

    companion object {
        const val NAME = "achieve_goal"
    }
}

// ---------- remove_board_item (destructive) ----------

class RemoveBoardItemTool(private val repo: BoardRepository) : Tool<BoardRefArgs, BoardItemsResult> {
    override val name = NAME
    override val description = "Delete a house rule, friction or goal from the Werkbord (e.g. a rule we dropped)."
    override val destructive = true
    override val argsSerializer = BoardRefArgs.serializer()
    override val resultSerializer = BoardItemsResult.serializer()

    override fun confirmQuestion(args: BoardRefArgs, ctx: ToolContext): String {
        val what = args.texts.joinToString(", ") { "“$it”" }.ifEmpty { ctx.t("dit", "this") }
        return ctx.t("$what van het werkbord verwijderen?", "Delete $what from the board?")
    }

    override suspend fun run(args: BoardRefArgs, ctx: ToolContext): BoardItemsResult {
        val items = repo.find(args.itemIds, args.texts).ifEmpty { notFound(ctx) }
        repo.delete(items.map { it.id })
        return BoardItemsResult(items, ctx.t("Verwijderd: ${quoted(items)}", "Deleted: ${quoted(items)}"))
    }

    companion object {
        const val NAME = "remove_board_item"
    }
}
