package app.hearth.core.tools.board

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.model.BoardKind
import app.hearth.core.model.Anchor
import app.hearth.core.model.BoardStatus
import app.hearth.core.model.FrictionDirection
import app.hearth.core.model.Topic
import app.hearth.core.model.WeekLook
import app.hearth.core.model.glance
import app.hearth.core.model.topic
import app.hearth.core.model.GoalScope
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolSource
import app.hearth.core.tools.backlog.AddRoutineArgs
import app.hearth.core.tools.backlog.AddToBacklogArgs
import app.hearth.core.tools.backlog.MoveBacklogArgs
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** House rules, frictions, goals and members on the Werkbord. */
class BoardToolsTest {
    private val db = inMemoryHearthDatabase()
    private val backlog = BacklogRepository(db)
    private val board = BoardRepository(db)
    private val tools = HearthTools(ShoppingRepository(db), backlog, board, app.hearth.core.data.food.StockRepository(db), app.hearth.core.data.week.WeekRepository(db), app.hearth.core.data.settings.SettingsRepository(db), app.hearth.core.data.week.MeetingRepository(db))
    private val nl = ToolContext(ToolSource.CHAT, Lang.NL, today = LocalDate(2026, 10, 22))

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `rules are written down once`() = runTest {
        assertEquals("Nieuwe huisregel: “Geen schermen na 20u”", tools.registry.run(tools.addHouseRule, AddHouseRuleArgs("huisregel: geen schermen na 20u"), nl).summary)
        assertEquals("Die huisregel staat er al.", tools.registry.run(tools.addHouseRule, AddHouseRuleArgs("Geen schermen na 20u"), nl).summary)
        assertEquals(1, board.allItems().size)
    }

    @Test
    fun `the meeting chooses what a friction needs, then writes it in its own words`() = runTest {
        tools.registry.run(tools.logFriction, LogFrictionArgs("De vaatwasser is nooit leeg"), nl)
        tools.registry.run(tools.logFriction, LogFrictionArgs("We vergeten de tassen"), nl)
        tools.registry.run(tools.logFriction, LogFrictionArgs("’s Ochtends is het altijd haasten"), nl)
        tools.registry.run(tools.addRoutine, AddRoutineArgs("Avondroutine", listOf("Koken", "Pyjama"), Anchor.EVENING), nl)

        // One tap: what it needs. It stays open, labelled, until someone words it.
        assertEquals(
            "“De vaatwasser is nooit leeg”: huisregel te maken. Schrijf het in jullie eigen woorden wanneer jullie willen.",
            tools.registry.run(tools.markFriction, MarkFrictionArgs(texts = listOf("vaatwasser"), direction = FrictionDirection.RULE), nl).summary,
        )
        tools.registry.run(tools.markFriction, MarkFrictionArgs(texts = listOf("tassen"), direction = FrictionDirection.CHANGE_ROUTINE), nl)
        tools.registry.run(tools.markFriction, MarkFrictionArgs(texts = listOf("haasten"), direction = FrictionDirection.NEW_ROUTINE), nl)
        val marked = board.allItems().filter { it.kind == BoardKind.FRICTION }
        assertEquals(listOf(FrictionDirection.RULE, FrictionDirection.CHANGE_ROUTINE, FrictionDirection.NEW_ROUTINE), marked.map { it.direction })
        assertTrue(marked.all { it.isOpen })
        assertEquals("3 te maken", Topic.FRICTION.glance(WeekLook(emptyList(), marked, 0, 0, LocalDate(2026, 10, 22)), Lang.NL))
        assertTrue("De vaatwasser is nooit leeg · huisregel te maken" in tools.registry.run(tools.listBoard, ListBoardArgs(), nl).summary)

        // Worded differently from the complaint.
        tools.registry.run(tools.resolveFriction, ResolveFrictionArgs(texts = listOf("vaatwasser"), outcome = FrictionOutcome.RULE, text = "Wie het laatst eet, zet hem aan"), nl)
        assertEquals(
            "“We vergeten de tassen”: “Avondroutine” past zich aan (stap erbij: Tassen bij de deur)",
            tools.registry.run(tools.resolveFriction, ResolveFrictionArgs(texts = listOf("tassen"), outcome = FrictionOutcome.ROUTINE_CHANGE, routine = "avondroutine", text = "Tassen bij de deur"), nl).summary,
        )
        assertEquals(
            "“’s Ochtends is het altijd haasten” wordt een routine: “Ochtendroutine” (ochtend)",
            tools.registry.run(tools.resolveFriction, ResolveFrictionArgs(texts = listOf("haasten"), outcome = FrictionOutcome.ROUTINE, text = "Ochtendroutine", anchor = Anchor.MORNING), nl).summary,
        )
        val avond = backlog.openItems().first { it.text == "Avondroutine" }
        assertEquals(listOf("Koken", "Pyjama", "Tassen bij de deur"), backlog.stepsOf(listOf(avond.id)).map { it.text })
        assertEquals(Anchor.MORNING, backlog.openItems().first { it.text == "Ochtendroutine" }.anchor)
        val after = board.allItems().filter { it.kind == BoardKind.FRICTION }
        assertEquals(listOf(BoardStatus.BECAME_RULE, BoardStatus.BECAME_ROUTINE, BoardStatus.BECAME_ROUTINE), after.map { it.status })
        assertEquals(avond.id, after[1].linkedId)
    }

    @Test
    fun `changing a routine needs the routine and what changes`() = runTest {
        tools.registry.run(tools.logFriction, LogFrictionArgs("We vergeten de tassen"), nl)
        val noRoutine = runCatching { tools.registry.run(tools.resolveFriction, ResolveFrictionArgs(texts = listOf("tassen"), outcome = FrictionOutcome.ROUTINE_CHANGE), nl) }
        assertEquals("Welke routine passen we aan?", noRoutine.exceptionOrNull()?.message)
        tools.registry.run(tools.addRoutine, AddRoutineArgs("Avondroutine", anchor = Anchor.EVENING), nl)
        val nothing = runCatching { tools.registry.run(tools.resolveFriction, ResolveFrictionArgs(texts = listOf("tassen"), outcome = FrictionOutcome.ROUTINE_CHANGE, routine = "avondroutine"), nl) }
        assertEquals("Wat verandert er: een ander moment, iets leuks erbij, kleiner beginnen of een stap erbij?", nothing.exceptionOrNull()?.message)
        tools.registry.run(tools.resolveFriction, ResolveFrictionArgs(texts = listOf("tassen"), outcome = FrictionOutcome.ROUTINE_CHANGE, routine = "avondroutine", anchor = Anchor.BEDTIME), nl)
        assertEquals(Anchor.BEDTIME, backlog.openItems().first { it.text == "Avondroutine" }.anchor)
    }

    @Test
    fun `a friction becomes a rule, a change, or is let go`() = runTest {
        tools.registry.run(tools.logFriction, LogFrictionArgs("Rommel in de gang"), nl)
        tools.registry.run(tools.logFriction, LogFrictionArgs("Niemand vult de afwasmachine"), nl)
        tools.registry.run(tools.logFriction, LogFrictionArgs("De buren hun muziek"), nl)

        val rule = tools.registry.run(tools.resolveFriction, ResolveFrictionArgs(texts = listOf("rommel"), outcome = FrictionOutcome.RULE, text = "Schoenen in de kast"), nl)
        assertEquals("“Rommel in de gang” wordt huisregel: “Schoenen in de kast”", rule.summary)

        val change = tools.registry.run(
            tools.resolveFriction,
            ResolveFrictionArgs(texts = listOf("afwasmachine"), outcome = FrictionOutcome.CHANGE, text = "Afwasbeurtrol maken", section = Section.ADMIN),
            nl,
        )
        assertEquals("“Niemand vult de afwasmachine”: we pakken het aan. “Afwasbeurtrol maken” staat bij volgende.", change.summary)
        val task = backlog.openItems().single()
        assertEquals(Section.ADMIN to Plan.NEXT, task.section to task.plan)

        tools.registry.run(tools.resolveFriction, ResolveFrictionArgs(texts = listOf("buren"), outcome = FrictionOutcome.LET_GO), nl)

        val all = board.allItems()
        assertEquals(
            listOf(BoardStatus.BECAME_RULE, BoardStatus.BECAME_CHANGE, BoardStatus.LET_GO, BoardStatus.OPEN),
            all.map { it.status },
        )
        assertEquals(task.id, all[1].linkedId)
        assertEquals(listOf("Schoenen in de kast"), all.filter { it.kind == BoardKind.RULE }.map { it.text })
        assertEquals("Huisregels:\n- Schoenen in de kast", tools.registry.run(tools.listBoard, ListBoardArgs(), nl).summary)
    }

    @Test
    fun `goals belong to the family, the couple or a person, and get a small next step`() = runTest {
        assertEquals("Welkom, Sam!", tools.registry.run(tools.addMember, AddMemberArgs("sam"), nl).summary)
        tools.registry.run(tools.addMember, AddMemberArgs("Lien"), nl)

        assertEquals(
            "Nieuw doel (samen): “Elke maand een avondje uit”",
            tools.registry.run(tools.addGoal, AddGoalArgs("elke maand een avondje uit", GoalScope.COUPLE), nl).summary,
        )
        assertEquals("Nieuw doel (Sam): “10 km lopen”", tools.registry.run(tools.addGoal, AddGoalArgs("10 km lopen", member = "Sam"), nl).summary)

        val step = tools.registry.run(tools.setGoalStep, SetGoalStepArgs(goal = "avondje", step = "Oppas vragen", toWeek = true), nl)
        assertEquals("“Elke maand een avondje uit” · volgende stap: Oppas vragen. Staat bij volgende.", step.summary)
        assertEquals(listOf("Oppas vragen"), backlog.openItems().filter { it.plan == Plan.NEXT }.map { it.text })

        assertEquals("Doel bereikt: “10 km lopen”. Proficiat!", tools.registry.run(tools.achieveGoal, AchieveGoalArgs(texts = listOf("10 km")), nl).summary)
        assertEquals(
            "Doelen:\n- Elke maand een avondje uit · volgende stap: Oppas vragen",
            tools.registry.run(tools.listBoard, ListBoardArgs(BoardKind.GOAL), nl).summary,
        )
    }

    @Test
    fun `a goal reached by mistake opens again, and keeps its place in the week`() = runTest {
        tools.registry.run(tools.addGoal, AddGoalArgs("Meer bewegen"), nl)
        tools.registry.run(tools.setGoalStep, SetGoalStepArgs(goal = "bewegen", step = "Wandelen", how = GoalPlanKind.HABIT, anchor = Anchor.EVENING), nl)
        val linked = board.allItems().single().linkedId
        tools.registry.run(tools.achieveGoal, AchieveGoalArgs(texts = listOf("bewegen")), nl)
        assertEquals(BoardStatus.ACHIEVED, board.allItems().single().status)
        assertEquals(linked, board.allItems().single().linkedId)
        val goal = board.allItems().single()
        assertEquals("Terug open: “Meer bewegen”", tools.registry.run(tools.achieveGoal, AchieveGoalArgs(itemIds = listOf(goal.id), reached = false), nl).summary)
        assertTrue(board.allItems().single().isOpen)
    }

    @Test
    fun `a rule, friction or goal can be said in other words`() = runTest {
        tools.registry.run(tools.addHouseRule, AddHouseRuleArgs("Schoenen in de gang"), nl)
        assertEquals("Aangepast: “Schoenen in het rek”", tools.registry.run(tools.editBoardItem, EditBoardItemArgs(text = "schoenen", newText = "schoenen in het rek"), nl).summary)
        assertEquals(listOf("Schoenen in het rek"), board.allItems().map { it.text })
    }

    @Test
    fun `a house rule gets help along the four laws, and the coach suggests one thing`() = runTest {
        tools.registry.run(tools.addHouseRule, AddHouseRuleArgs("Tafel samen afruimen"), nl)
        assertEquals(
            "“Tafel samen afruimen”: rond het eten, begin klein: alleen je eigen bord",
            tools.registry.run(tools.setRuleHelp, SetRuleHelpArgs(text = "tafel", anchor = Anchor.DINNER, smallStep = "Alleen je eigen bord"), nl).summary,
        )
        // Not given: unchanged; empty: removed.
        tools.registry.run(tools.setRuleHelp, SetRuleHelpArgs(text = "tafel", pairing = "met muziek"), nl)
        val rule = board.allItems().single()
        assertEquals(Triple(Anchor.DINNER, "met muziek", "alleen je eigen bord"), Triple(rule.anchor, rule.pairing, rule.smallStep))
        tools.registry.run(tools.setRuleHelp, SetRuleHelpArgs(itemId = rule.id, smallStep = "", noMoment = true), nl)
        assertEquals(null, board.allItems().single().smallStep)
        assertEquals(null, board.allItems().single().anchor)

        // Too much last meeting, and someone says there's no time: start small.
        tools.registry.run(tools.checkHouseRule, app.hearth.core.tools.week.CheckHouseRuleArgs(rule = "tafel", score = 1), nl)
        tools.registry.run(tools.logFriction, LogFrictionArgs("Geen tijd om de tafel af te ruimen"), nl)
        val review = tools.registry.run(tools.reviewRule, ReviewRuleArgs(), nl)
        assertEquals("“Tafel samen afruimen”: Te zwaar: maak het kleiner, niet harder. Wat is de kleine versie om mee te beginnen?", review.summary)
    }

    @Test
    fun `a goal gets a place in the week as a habit or free time for a hobby`() = runTest {
        tools.registry.run(tools.addGoal, AddGoalArgs("Meer bewegen"), nl)
        tools.registry.run(tools.addGoal, AddGoalArgs("Muziek maken"), nl)

        assertEquals(
            "“Meer bewegen” krijgt een ritme: “10 minuten yoga” (ochtend, elke dag).",
            tools.registry.run(tools.setGoalStep, SetGoalStepArgs(goal = "bewegen", step = "10 minuten yoga", how = GoalPlanKind.HABIT, anchor = Anchor.MORNING), nl).summary,
        )
        val yoga = backlog.openItems().first { it.text == "10 minuten yoga" }
        assertEquals(Triple(Section.HABIT, Anchor.MORNING, 1), Triple(yoga.section, yoga.anchor, yoga.intervalDays))
        assertEquals(Topic.RHYTHM, yoga.topic)

        assertEquals(
            "“Muziek maken” krijgt vrije tijd: “Gitaar spelen” (zaterdag).",
            tools.registry.run(tools.setGoalStep, SetGoalStepArgs(goal = "muziek", step = "gitaar spelen", how = GoalPlanKind.FREE_TIME, date = "2026-10-24"), nl).summary,
        )
        val guitar = backlog.openItems().first { it.text == "Gitaar spelen" }
        assertEquals(Section.PROTECTED_TIME to "2026-10-24", guitar.section to guitar.date)

        val goals = board.allItems().filter { it.kind == BoardKind.GOAL }.associateBy { it.text }
        assertEquals(yoga.id, goals.getValue("Meer bewegen").linkedId)
        assertEquals(guitar.id, goals.getValue("Muziek maken").linkedId)

        val noDay = runCatching { tools.registry.run(tools.setGoalStep, SetGoalStepArgs(goal = "muziek", step = "Piano", how = GoalPlanKind.FREE_TIME), nl) }
        assertEquals("Op welke dag maken jullie er tijd voor?", noDay.exceptionOrNull()?.message)
    }

    @Test
    fun `removing a person asks first and keeps their goals as family goals`() = runTest {
        tools.registry.run(tools.addMember, AddMemberArgs("Sam"), nl)
        tools.registry.run(tools.addGoal, AddGoalArgs("Gitaar leren", member = "Sam"), nl)
        val call = ToolCall.of(RemoveMemberTool.NAME, RemoveMemberArgs.serializer(), RemoveMemberArgs("Sam"))
        assertTrue(tools.registry.execute(call, nl) is ToolOutcome.NeedsConfirmation)
        tools.registry.execute(call, nl, confirmed = true)
        assertTrue(board.allMembers().isEmpty())
        val goal = board.allItems().single()
        assertNull(goal.memberId)
        assertEquals(GoalScope.FAMILY, goal.scope)
    }

    @Test
    fun `rules and frictions sent to the backlog land on the board, and can be moved there`() = runTest {
        assertEquals(
            "Op het werkbord: “Wie kookt, wast niet af” (huisregel)",
            tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Wie kookt, wast niet af", Section.NOTE), nl).summary,
        )
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Altijd zoeken naar de sleutels"), nl)
        tools.registry.run(tools.moveBacklogItem, MoveBacklogArgs(texts = listOf("sleutels"), section = Section.FRICTION), nl)
        assertTrue(backlog.openItems().isEmpty())
        assertEquals(listOf(BoardKind.RULE, BoardKind.FRICTION), board.allItems().map { it.kind })

        val call = ToolCall.of(RemoveBoardItemTool.NAME, BoardRefArgs.serializer(), BoardRefArgs(texts = listOf("sleutels")))
        assertEquals("“sleutels” van het werkbord verwijderen?", tools.registry.execute(call, nl).summary)
        tools.registry.execute(call, nl, confirmed = true)
        assertEquals(1, board.allItems().size)
    }
}
