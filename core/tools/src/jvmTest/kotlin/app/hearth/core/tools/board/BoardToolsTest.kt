package app.hearth.core.tools.board

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.model.BoardKind
import app.hearth.core.model.BoardStatus
import app.hearth.core.model.GoalScope
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolSource
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
    private val tools = HearthTools(ShoppingRepository(db), backlog, board, app.hearth.core.data.food.StockRepository(db), app.hearth.core.data.week.WeekRepository(db))
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

        assertEquals("Doel bereikt: “10 km lopen”. Proficiat!", tools.registry.run(tools.achieveGoal, BoardRefArgs(texts = listOf("10 km")), nl).summary)
        assertEquals(
            "Doelen:\n- Elke maand een avondje uit · volgende stap: Oppas vragen",
            tools.registry.run(tools.listBoard, ListBoardArgs(BoardKind.GOAL), nl).summary,
        )
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
