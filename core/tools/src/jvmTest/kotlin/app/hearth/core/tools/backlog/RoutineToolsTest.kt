package app.hearth.core.tools.backlog

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.Anchor
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.model.Topic
import app.hearth.core.model.doneOn
import app.hearth.core.model.isRoutineStep
import app.hearth.core.model.topic
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Routines with steps: the evening sequence and the bag for the opvang. */
class RoutineToolsTest {
    private val db = inMemoryHearthDatabase()
    private val backlog = BacklogRepository(db)
    private val tools = HearthTools(ShoppingRepository(db), backlog, BoardRepository(db), StockRepository(db), WeekRepository(db), SettingsRepository(db), MeetingRepository(db))
    private val today = LocalDate(2026, 10, 25)
    private val ctx = ToolContext(ToolSource.UI, Lang.NL, today = today)

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `steps can change place, and new ones go last`() = runTest {
        val routine = tools.registry.run(tools.addRoutine, AddRoutineArgs("Avondroutine", listOf("Boodschappen", "Ada naar bed doen", "Avond eten"), Anchor.EVENING), ctx).item
        assertEquals(
            "“Avond eten” is nu stap 2 van “Avondroutine”: Boodschappen → Avond eten → Ada naar bed doen",
            tools.registry.run(tools.moveStep, MoveStepArgs(step = "avond eten", parent = "avondroutine", position = 2), ctx).summary,
        )
        tools.registry.run(tools.addRoutine, AddRoutineArgs("Avondroutine", listOf("Keuken opkuisen")), ctx)
        assertEquals(listOf("Boodschappen", "Avond eten", "Ada naar bed doen", "Keuken opkuisen"), backlog.stepsOf(listOf(routine.id)).map { it.text })
        // By id too, and beyond the end means last.
        val first = backlog.stepsOf(listOf(routine.id)).first()
        tools.registry.run(tools.moveStep, MoveStepArgs(stepId = first.id, position = 99), ctx)
        assertEquals("Boodschappen", backlog.stepsOf(listOf(routine.id)).last().text)
        val unknown = runCatching { tools.registry.run(tools.moveStep, MoveStepArgs(step = "koken", position = 1), ctx) }
        assertEquals("Bij welke routine hoort die stap?", unknown.exceptionOrNull()?.message)
    }

    @Test
    fun `a routine keeps its steps in order, ticked per day`() = runTest {
        val made = tools.registry.run(tools.addRoutine, AddRoutineArgs("tas voor de opvang", listOf("luiers", "flesjes", "reservekleren"), Anchor.MORNING), ctx)
        assertEquals("Routine “Tas voor de opvang” (ochtend): Luiers → Flesjes → Reservekleren", made.summary)
        val routine = made.item
        assertEquals(1, routine.intervalDays)
        val steps = backlog.openItems().filter { it.parentId == routine.id }
        assertEquals(listOf("Luiers", "Flesjes", "Reservekleren"), steps.map { it.text })
        assertTrue(steps.all { it.isRoutineStep && it.topic == null })
        assertEquals(Topic.RHYTHM, routine.topic)

        // Ticking a step logs today; it stays in the routine for tomorrow.
        tools.registry.run(tools.completeBacklogItem, BacklogRefArgs(itemIds = listOf(steps[0].id)), ctx)
        val luiers = backlog.openItems().first { it.id == steps[0].id }
        assertTrue(luiers.doneOn(today))
        assertFalse(luiers.doneOn(LocalDate(2026, 10, 26)))

        // All steps ticked: the routine is done for today too.
        tools.registry.run(tools.completeBacklogItem, BacklogRefArgs(itemIds = steps.drop(1).map { it.id }), ctx)
        assertTrue(backlog.openItems().first { it.id == routine.id }.doneOn(today))

        // The routine's steps never show up as loose items.
        val all = tools.registry.run(tools.listBacklog, ListBacklogArgs(BacklogView.ALL), ctx).items
        assertEquals(listOf("Tas voor de opvang"), all.map { it.text })
    }

    @Test
    fun `a checklist without a moment is not daily, it waits until it's needed`() = runTest {
        val bag = tools.registry.run(tools.addRoutine, AddRoutineArgs("Paklijst babysit", listOf("Luiers", "Slaapzak")), ctx).item
        assertEquals(null, bag.intervalDays)
        assertEquals(null, bag.anchor)
        // Not in today's rhythm: it's used when the babysit comes.
        assertTrue(app.hearth.core.model.dueRoutines(backlog.openItems(), today).none { it.id == bag.id })
    }
}
