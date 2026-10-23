package app.hearth.core.tools.backlog

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.model.DayLoad
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.model.RoutineState
import app.hearth.core.model.Section
import app.hearth.core.model.dayLoad
import app.hearth.core.model.dontForget
import app.hearth.core.model.dueRoutines
import app.hearth.core.model.plannedOn
import app.hearth.core.model.plannedToday
import app.hearth.core.model.plusDays
import app.hearth.core.model.routineState
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Routines, admin and projects: each planned in its own way, and the dashboard reading them. */
class TaskToolsTest {
    private val db = inMemoryHearthDatabase()
    private val backlog = BacklogRepository(db)
    private val tools = HearthTools(ShoppingRepository(db), backlog, app.hearth.core.data.board.BoardRepository(db))

    // Thursday 22 October 2026.
    private val thursday = LocalDate(2026, 10, 22)
    private val nl = ToolContext(ToolSource.CHAT, Lang.NL, today = thursday)

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun add(args: AddToBacklogArgs, ctx: ToolContext = nl) = tools.registry.run(tools.addToBacklog, args, ctx)

    @Test
    fun `a routine comes back after its interval instead of closing`() = runTest {
        val added = add(AddToBacklogArgs("Lakens verversen", Section.CHORE, intervalDays = 14))
        assertEquals("Op de backlog: “Lakens verversen” (huishoudtaak) · om de 2 weken", added.summary)
        assertEquals(RoutineState.NEW, added.item.routineState(thursday))
        assertEquals(listOf("Lakens verversen"), dueRoutines(backlog.openItems(), thursday).map { it.text })

        val done = tools.registry.run(tools.completeBacklogItem, BacklogRefArgs(texts = listOf("lakens")), nl)
        assertEquals("Gedaan: “Lakens verversen”. Mooi zo! Volgende keer: om de 2 weken.", done.summary)
        val item = backlog.openItems().single()
        assertEquals("2026-10-22", item.lastDone)
        assertEquals(1, backlog.doneSince(0).first())

        assertEquals(RoutineState.FRESH, item.routineState(thursday.plusDays(5)))
        assertEquals(RoutineState.SOON, item.routineState(thursday.plusDays(12)))
        assertEquals(RoutineState.DUE, item.routineState(thursday.plusDays(14)))
        assertTrue(dueRoutines(listOf(item), thursday.plusDays(3)).isEmpty())
        assertEquals(1, dueRoutines(listOf(item), thursday.plusDays(20)).size)
    }

    @Test
    fun `a routine given a day shows on that day and goes back to its rhythm when done`() = runTest {
        add(AddToBacklogArgs("Ramen kuisen", Section.CHORE, When.DAY, "2026-10-24", intervalDays = 30))
        assertEquals(listOf("Ramen kuisen"), plannedOn(backlog.openItems(), LocalDate(2026, 10, 24)).map { it.text })
        assertTrue(dueRoutines(backlog.openItems(), thursday).isEmpty())
        tools.registry.run(tools.completeBacklogItem, BacklogRefArgs(texts = listOf("ramen")), nl)
        val item = backlog.openItems().single()
        assertEquals(Plan.UNPLANNED, item.plan)
        assertNull(item.date)
    }

    @Test
    fun `setting an interval turns something into a routine`() = runTest {
        add(AddToBacklogArgs("Plantjes water geven"))
        val out = tools.registry.run(tools.setRoutineInterval, SetRoutineIntervalArgs(texts = listOf("plantjes"), days = 7), nl)
        assertEquals("“Plantjes water geven” · elke week", out.summary)
        assertEquals(Section.CHORE, backlog.openItems().single().section)
    }

    @Test
    fun `don't forget holds urgent admin, close deadlines and due follow-ups`() = runTest {
        add(AddToBacklogArgs("Verzekering opzeggen", Section.ADMIN, urgent = true))
        add(AddToBacklogArgs("Belastingbrief", Section.ADMIN, When.DAY, "2026-10-24", finishBy = true))
        add(AddToBacklogArgs("Autokeuring", Section.APPOINTMENT, When.DAY, "2026-11-20", finishBy = true))
        add(AddToBacklogArgs("Garage bellen", Section.ADMIN))
        add(AddToBacklogArgs("Gras maaien", Section.CHORE, When.TODAY))

        val waiting = tools.registry.run(tools.waitForReply, WaitForReplyArgs(texts = listOf("garage")), nl)
        assertEquals("“Garage bellen” wacht op antwoord · opvolgen zondag", waiting.summary)

        val open = backlog.openItems()
        assertEquals(listOf("Verzekering opzeggen", "Belastingbrief"), dontForget(open, thursday).map { it.text })
        assertEquals(listOf("Verzekering opzeggen", "Belastingbrief", "Garage bellen"), dontForget(open, thursday.plusDays(3)).map { it.text })
        assertEquals(listOf("Gras maaien"), plannedToday(open, thursday).map { it.text })

        val listed = tools.registry.run(tools.listBacklog, ListBacklogArgs(BacklogView.DONT_FORGET), nl)
        assertEquals(
            "Verzekering opzeggen (administratie, in te plannen, dringend)\nBelastingbrief (administratie, klaar tegen zaterdag)",
            listed.summary,
        )

        val reply = tools.registry.run(tools.waitForReply, WaitForReplyArgs(texts = listOf("garage"), waiting = false), nl)
        assertEquals("Antwoord binnen: “Garage bellen” staat bij volgende", reply.summary)
        assertEquals(Plan.NEXT, backlog.openItems().first { it.text == "Garage bellen" }.plan)

        tools.registry.run(tools.markUrgent, MarkUrgentArgs(texts = listOf("verzekering"), urgent = false), nl)
        assertEquals(listOf("Belastingbrief"), dontForget(backlog.openItems(), thursday).map { it.text })
    }

    @Test
    fun `projects get steps, finishing a project finishes its steps, deleting takes them along`() = runTest {
        val step = tools.registry.run(tools.addProjectStep, AddProjectStepArgs(project = "IKEA kast", text = "Kast ophalen", `when` = When.DAY, date = "2026-10-24"), nl)
        assertEquals("Stap bij “IKEA kast”: “Kast ophalen”", step.summary)
        tools.registry.run(tools.addProjectStep, AddProjectStepArgs(project = "ikea", text = "Kast monteren"), nl)
        // The same step text in another project is a different step.
        tools.registry.run(tools.addProjectStep, AddProjectStepArgs(project = "Lampen toilet", text = "Kast ophalen"), nl)

        val open = backlog.openItems()
        val kast = open.single { it.text == "IKEA kast" }
        assertEquals(2, backlog.stepsOf(listOf(kast.id)).size)
        assertEquals(5, open.size)
        assertEquals(1, plannedOn(open, LocalDate(2026, 10, 24)).size)

        tools.registry.run(tools.completeBacklogItem, BacklogRefArgs(itemIds = listOf(kast.id)), nl)
        assertEquals(2, backlog.openItems().size)
        assertTrue(backlog.stepsOf(listOf(kast.id)).all { it.done })

        val lampen = backlog.openItems().single { it.text == "Lampen toilet" }
        val call = ToolCall.of(RemoveBacklogItemTool.NAME, BacklogRefArgs.serializer(), BacklogRefArgs(itemIds = listOf(lampen.id)))
        assertTrue(tools.registry.execute(call, nl) is ToolOutcome.NeedsConfirmation)
        tools.registry.execute(call, nl, confirmed = true)
        assertTrue(backlog.openItems().isEmpty())
    }

    @Test
    fun `moving sorts an item into the right list`() = runTest {
        add(AddToBacklogArgs("Zolder opruimen"))
        assertEquals("“Zolder opruimen” staat nu bij project", tools.registry.run(tools.moveBacklogItem, MoveBacklogArgs(texts = listOf("zolder"), section = Section.PROJECT), nl).summary)
    }

    @Test
    fun `day load without durations`() {
        assertEquals(DayLoad.FREE, dayLoad(0))
        assertEquals(DayLoad.LIGHT, dayLoad(2))
        assertEquals(DayLoad.BUSY, dayLoad(3))
    }
}
