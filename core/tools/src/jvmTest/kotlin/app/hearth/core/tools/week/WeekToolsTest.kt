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
import app.hearth.core.model.Topic
import app.hearth.core.model.WeekLook
import app.hearth.core.model.glance
import app.hearth.core.model.topic
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

/** Meals, topics and the weekly meeting's agenda. */
class WeekToolsTest {
    private val db = inMemoryHearthDatabase()
    private val shopping = ShoppingRepository(db)
    private val backlog = BacklogRepository(db)
    private val week = WeekRepository(db)
    private var tick = app.hearth.core.model.nowMillis()
    private val tools = HearthTools(shopping, BacklogRepository(db) { tick++ }, BoardRepository(db), StockRepository(db), week, app.hearth.core.data.settings.SettingsRepository(db), app.hearth.core.data.week.MeetingRepository(db))

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
    fun `every item has one topic, and each tile says it in a few calm words`() = runTest {
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Verzekering bellen", Section.ADMIN, urgent = true), nl)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Filmavond", Section.PROTECTED_TIME, When.DAY, "2026-10-23"), nl)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Zolder opruimen", Section.PROJECT), nl)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Fotoboek maken"), nl)
        val open = backlog.openItems()
        assertEquals(Topic.ADMIN, open.first { it.text == "Verzekering bellen" }.topic)
        assertEquals(null, open.first { it.text == "Fotoboek maken" }.topic)
        val look = WeekLook(open, emptyList(), mealsPlanned = 2, shoppingOpen = 0, today = LocalDate(2026, 10, 22))
        assertEquals("1 open · 1 dringend", Topic.ADMIN.glance(look, Lang.NL))
        assertEquals("Filmavond (morgen)", Topic.FREE_TIME.glance(look, Lang.NL))
        assertEquals("1 lopend", Topic.PROJECTS.glance(look, Lang.NL))
        assertEquals("2 van 7 avonden", Topic.MENU.glance(look, Lang.NL))
        assertEquals("The list is empty", Topic.SHOPPING.glance(look, Lang.EN))
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
            "Terugblik: 1 ding gedaan.\nVrije tijd: Nog niets gepland.\nAdmin & afspraken: 1 open (klaar tegen: Autokeuring).\n" +
                "Grote taken: Geen lopende projecten.\nHuishouden & gewoontes: Loopt rustig.\nBoodschappen: De lijst is leeg.\n" +
                "Weekmenu: 1 van 7 avonden (Pizza).\nFrictie & huisregels: 1 te bespreken (Rommel in de gang).\nMeters (samen afspreken): hoe liep het, voorstel 4; wat schuurde, voorstel 2.",
            agenda.summary,
        )

        assertTrue(huddleDue(week.last(), friday))
        assertEquals("Mooi weekoverleg! Jullie zijn klaar voor de week.", tools.registry.run(tools.finishWeekoverleg, app.hearth.core.tools.week.FinishArgs(), nl).summary)
        assertFalse(huddleDue(week.last(), friday.plusDays(3)))
        assertTrue(huddleDue(week.last(), friday.plusDays(6)))
    }

    @Test
    fun `recipes keep how we made them, their photo survives a rename, and planning brings the ingredients`() = runTest {
        val saved = tools.registry.run(
            tools.saveRecipe,
            SaveRecipeArgs("lasagne van oma", listOf("gehakt, tomatensaus en lasagnebladen", "kaas"), "45 minuten in de oven op 180 graden"),
            nl,
        )
        assertEquals("Recept bewaard: Lasagne van oma (4 ingrediënten)", saved.summary)
        val photo = kotlin.io.encoding.Base64.encode(byteArrayOf(1, 2, 3))
        tools.registry.run(tools.setRecipePhoto, SetRecipePhotoArgs(saved.recipe.key, photo), nl)
        assertTrue(week.recipe("Lasagne van oma")!!.hasPhoto)

        val renamed = tools.registry.run(tools.saveRecipe, SaveRecipeArgs("Lasagne", saved.recipe.ingredients, saved.recipe.notes, recipeKey = saved.recipe.key), nl)
        assertEquals("Recept bijgewerkt: Lasagne", renamed.summary)
        assertEquals(listOf<Byte>(1, 2, 3), week.photo(renamed.recipe.key)!!.toList())
        assertEquals(null, week.recipe("Lasagne van oma"))

        assertEquals("Maandag: Lasagne · 4 op de lijst", tools.registry.run(tools.planMeal, PlanMealArgs("lasagne", "2026-10-26"), nl).summary)
        assertEquals(listOf("Gehakt", "Tomatensaus", "Lasagnebladen", "Kaas"), shopping.openItems().map { it.name })
        // Planned recipes stay recipes, with their notes.
        val after = week.recipe("lasagne")!!
        assertTrue(after.isRecipe)
        assertEquals("45 minuten in de oven op 180 graden", after.notes)
        assertEquals("2026-10-26", after.lastUsedOn)

        assertEquals("Lasagne: gehakt, tomatensaus, lasagnebladen, kaas", tools.registry.run(tools.listRecipes, ListRecipesArgs("gehakt"), nl).summary)
        val call = ToolCall.of(RemoveRecipeTool.NAME, RecipeRefArgs.serializer(), RecipeRefArgs("Lasagne"))
        assertTrue(tools.registry.execute(call, nl) is ToolOutcome.NeedsConfirmation)
        tools.registry.execute(call, nl, confirmed = true)
        assertEquals(null, week.recipe("lasagne"))
        assertEquals(null, week.photo("lasagne"))
        assertEquals(1, week.mealsBetween("2026-10-01", "2026-11-30").size)
    }
}
