package app.hearth.core.tools.week

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.huddleDue
import app.hearth.core.model.oudjes
import app.hearth.core.model.plusDays
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolSource
import app.hearth.core.tools.backlog.AddToBacklogArgs
import app.hearth.core.tools.backlog.BacklogRefArgs
import app.hearth.core.tools.backlog.When
import app.hearth.core.tools.board.LogFrictionArgs
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Meals, oudjes and the weekly meeting's agenda. */
class WeekToolsTest {
    private val db = inMemoryHearthDatabase()
    private val shopping = ShoppingRepository(db)
    private val backlog = BacklogRepository(db)
    private val week = WeekRepository(db)
    private var tick = app.hearth.core.model.nowMillis()
    private val tools = HearthTools(shopping, BacklogRepository(db) { tick++ }, BoardRepository(db), StockRepository(db), week)

    // Friday 23 October 2026.
    private val friday = LocalDate(2026, 10, 23)
    private val nl = ToolContext(ToolSource.CHAT, Lang.NL, today = friday)

    @BeforeTest
    fun setUp() = runTest { shopping.ensureDefaultShops(Lang.NL) }

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `a meal puts its ingredients on the list, and comes back with them next time`() = runTest {
        val first = tools.registry.run(tools.planMeal, PlanMealArgs("spaghetti bolognese", "2026-10-26", listOf("gehakt, tomaten en spaghetti")), nl)
        assertEquals("Maandag: Spaghetti bolognese · 3 op de lijst", first.summary)
        assertEquals(listOf("Gehakt", "Tomaten", "Spaghetti"), shopping.openItems().map { it.name })

        // Same day again replaces; same dish later reuses its ingredients.
        tools.registry.run(tools.planMeal, PlanMealArgs("Frietjes", "2026-10-26", toShopping = false), nl)
        val again = tools.registry.run(tools.planMeal, PlanMealArgs("Spaghetti Bolognese", "2026-10-29"), nl)
        assertEquals(listOf("gehakt", "tomaten", "spaghetti"), again.meal.ingredients.map { it.lowercase() })
        assertEquals(3, shopping.openItems().size)

        assertEquals(
            "Maandag: Frietjes\nDonderdag: Spaghetti Bolognese",
            tools.registry.run(tools.listMealPlan, ListMealPlanArgs(), nl).summary,
        )
        val call = ToolCall.of(RemoveMealTool.NAME, RemoveMealArgs.serializer(), RemoveMealArgs(date = "2026-10-26"))
        assertTrue(tools.registry.execute(call, nl) is ToolOutcome.NeedsConfirmation)
        tools.registry.execute(call, nl, confirmed = true)
        assertEquals(1, week.mealsBetween("2026-10-01", "2026-11-30").size)
    }

    @Test
    fun `oudjes are the longest open, unplanned, not routines`() = runTest {
        listOf("Zolder opruimen", "Fotoboek maken", "Kast herschilderen", "Belastingbrief").forEach {
            tools.registry.run(tools.addToBacklog, AddToBacklogArgs(it, Section.PROJECT), nl)
        }
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Lakens", Section.CHORE, intervalDays = 14), nl)
        tools.registry.run(tools.planBacklogItem, app.hearth.core.tools.backlog.PlanBacklogArgs(texts = listOf("fotoboek"), `when` = When.NEXT), nl)
        val open = backlog.openItems()
        assertEquals(listOf("Zolder opruimen", "Kast herschilderen", "Belastingbrief"), oudjes(open).map { it.text })
        assertEquals(listOf("Belastingbrief"), oudjes(open, count = 3, skip = 2).map { it.text })
        assertEquals(Plan.NEXT, open.first { it.text == "Fotoboek maken" }.plan)
    }

    @Test
    fun `agenda covers the week, finishing records the meeting`() = runTest {
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Autokeuring", Section.APPOINTMENT, When.DAY, "2026-10-27", finishBy = true), nl)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Tandarts bellen", Section.APPOINTMENT), nl)
        tools.registry.run(tools.completeBacklogItem, BacklogRefArgs(texts = listOf("tandarts")), nl)
        tools.registry.run(tools.logFriction, LogFrictionArgs("Rommel in de gang"), nl)
        tools.registry.run(tools.planMeal, PlanMealArgs("Pizza", toShopping = false), nl)

        val agenda = tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), nl)
        assertEquals(listOf("Tandarts bellen"), agenda.done.map { it.text })
        assertEquals(listOf("Autokeuring"), agenda.deadlines.map { it.text })
        assertEquals(listOf("Rommel in de gang"), agenda.frictions.map { it.text })
        assertEquals(
            "1. Terugblik: 1 dingen gedaan.\n2. Maaltijden: 1 van 7 dagen gepland (Pizza).\n3. Boodschappen: 0 op de lijst.\n" +
                "4. Deze week: deadlines Autokeuring.\n5. Oudjes: geen.\n6. Werkbord: Rommel in de gang.",
            agenda.summary,
        )

        assertTrue(huddleDue(week.last(), friday))
        assertEquals("Mooi weekoverleg! Jullie zijn klaar voor de week.", tools.registry.run(tools.finishWeekoverleg, AgendaArgs(), nl).summary)
        assertFalse(huddleDue(week.last(), friday.plusDays(3)))
        assertTrue(huddleDue(week.last(), friday.plusDays(6)))
    }
}
