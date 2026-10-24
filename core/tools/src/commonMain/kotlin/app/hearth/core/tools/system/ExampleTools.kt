package app.hearth.core.tools.system

import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.model.Ease
import app.hearth.core.model.GoalScope
import app.hearth.core.model.Section
import app.hearth.core.model.StockState
import app.hearth.core.tools.backlog.When
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolResult
import app.hearth.core.tools.backlog.AddProjectStepArgs
import app.hearth.core.tools.backlog.AddToBacklogArgs
import app.hearth.core.tools.backlog.PlanBacklogArgs
import app.hearth.core.tools.backlog.RateHabitArgs
import app.hearth.core.tools.backlog.SmallStepArgs
import app.hearth.core.tools.backlog.WaitForReplyArgs
import app.hearth.core.tools.board.AddGoalArgs
import app.hearth.core.tools.board.AddHouseRuleArgs
import app.hearth.core.tools.board.AddIdeaArgs
import app.hearth.core.tools.board.AddMemberArgs
import app.hearth.core.tools.board.LogFrictionArgs
import app.hearth.core.tools.food.AddShoppingItemArgs
import app.hearth.core.tools.food.AddStockItemArgs
import app.hearth.core.tools.food.SetStockStateArgs
import app.hearth.core.tools.week.AddWinArgs
import app.hearth.core.tools.week.PlanMealArgs
import app.hearth.core.tools.week.SaveRecipeArgs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

// Tablet-only: one or two examples in every section, so a new household (or the web preview) sees how it works.

@Serializable
data class LoadExamplesResult(val added: Int, override val summary: String) : ToolResult

/** Adds examples through the same tools the family uses; existing items stay as they are. */
class LoadExamplesTool(
    private val tools: HearthTools,
    private val settings: SettingsRepository,
) : Tool<NoArgs, LoadExamplesResult> {
    override val name = NAME
    override val description = "Fill every section with one or two examples (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = NoArgs.serializer()
    override val resultSerializer = LoadExamplesResult.serializer()

    override suspend fun run(args: NoArgs, ctx: ToolContext): LoadExamplesResult {
        val e = if (ctx.lang == app.hearth.core.model.Lang.NL) NL else EN
        val day = { n: Int -> ctx.today.plus(n, DateTimeUnit.DAY).toString() }
        var added = 0
        suspend fun step(block: suspend () -> Unit) { if (runCatching { block() }.isSuccess) added++ }
        suspend fun task(text: String, section: Section, w: When = When.UNPLANNED, date: String? = null, finishBy: Boolean = false, interval: Int? = null) =
            step { tools.addToBacklog.run(AddToBacklogArgs(text, section, w, date, finishBy, intervalDays = interval), ctx) }

        e.members.forEach { step { tools.addMember.run(AddMemberArgs(it), ctx) } }

        // Ritme & gewoontes: chores come back; habits are rated, never ticked off.
        task(e.chores[0], Section.CHORE, interval = 14)
        task(e.chores[1], Section.CHORE, interval = 4)
        task(e.habits[0], Section.HABIT, interval = 1)
        task(e.habits[1], Section.HABIT, interval = 1)
        step { tools.rateHabit.run(RateHabitArgs(texts = listOf(e.habits[0]), ease = Ease.EASY), ctx) }
        step { tools.rateHabit.run(RateHabitArgs(texts = listOf(e.habits[1]), ease = Ease.HARD), ctx) }
        step { tools.setSmallStep.run(SmallStepArgs(texts = listOf(e.habits[1]), step = e.smallStep), ctx) }

        // Admin: a deadline, and one waiting for a reply.
        task(e.admin[0], Section.ADMIN, When.DAY, day(10), finishBy = true)
        task(e.admin[1], Section.ADMIN)
        step { tools.waitForReply.run(WaitForReplyArgs(texts = listOf(e.admin[1]), followUp = day(3)), ctx) }
        task(e.appointment, Section.APPOINTMENT, When.DAY, day(3))

        // Projecten: one with steps, one that was pushed back twice (amber).
        step { tools.addProjectStep.run(AddProjectStepArgs(project = e.project, text = e.projectSteps[0], `when` = When.TOMORROW), ctx) }
        step { tools.addProjectStep.run(AddProjectStepArgs(project = e.project, text = e.projectSteps[1]), ctx) }
        task(e.pushed, Section.PROJECT, When.DAY, day(1))
        listOf(2, 4).forEach { n -> step { tools.planBacklogItem.run(PlanBacklogArgs(texts = listOf(e.pushed), `when` = When.DAY, date = day(n)), ctx) } }

        // Vrije tijd and something still to sort (it waits in Taken → Te sorteren).
        task(e.free, Section.PROTECTED_TIME, When.DAY, day(daysUntilFriday(ctx.today)))
        task(e.toSort, Section.OTHER)

        // Werkbord.
        e.rules.forEach { step { tools.addHouseRule.run(AddHouseRuleArgs(it), ctx) } }
        step { tools.logFriction.run(LogFrictionArgs(e.friction, e.members[1]), ctx) }
        step { tools.addGoal.run(AddGoalArgs(e.goal, GoalScope.COUPLE, nextStep = e.goalStep), ctx) }
        step { tools.addIdea.run(AddIdeaArgs(e.idea, e.members[0]), ctx) }

        // Boodschappen and Voorraad.
        e.shopping.forEach { (name, shop) -> step { tools.addShoppingItem.run(AddShoppingItemArgs(name, shop = shop), ctx) } }
        step { tools.addStockItem.run(AddStockItemArgs(e.stockLow, e.fridge), ctx) }
        step { tools.setStockState.run(SetStockStateArgs(names = listOf(e.stockLow), state = StockState.LOW), ctx) }

        // Weekmenu and a recipe.
        e.meals.forEachIndexed { i, (dish, ingredients) ->
            step { tools.planMeal.run(PlanMealArgs(dish, day(i), ingredients, toShopping = false), ctx) }
        }
        step { tools.saveRecipe.run(SaveRecipeArgs(e.recipe, e.recipeIngredients, e.recipeNotes), ctx) }

        // Weekoverleg: something that went well.
        step { tools.addWin.run(AddWinArgs(e.win, e.members[0]), ctx) }

        settings.put(LOADED, "true")
        return LoadExamplesResult(added, ctx.t("Voorbeelden staan klaar. Pas ze aan of haal ze weg wanneer je wilt.", "Examples are in. Change or remove them whenever you like."))
    }

    private fun daysUntilFriday(today: LocalDate): Int = ((4 - today.dayOfWeek.ordinal) + 7) % 7

    private class Examples(
        val members: List<String>,
        val chores: List<String>,
        val habits: List<String>,
        val smallStep: String,
        val admin: List<String>,
        val appointment: String,
        val project: String,
        val projectSteps: List<String>,
        val pushed: String,
        val free: String,
        val toSort: String,
        val rules: List<String>,
        val friction: String,
        val goal: String,
        val goalStep: String,
        val idea: String,
        val shopping: List<Pair<String, String?>>,
        val stockLow: String,
        val fridge: String,
        val meals: List<Pair<String, List<String>>>,
        val recipe: String,
        val recipeIngredients: List<String>,
        val recipeNotes: String,
        val win: String,
    )

    companion object {
        const val NAME = "load_examples"

        /** Set once examples were loaded, so the web preview fills itself only the first time. */
        const val LOADED = "examples.loaded"

        private val NL = Examples(
            members = listOf("Sam", "Noor"),
            chores = listOf("Lakens verversen", "Planten water geven"),
            habits = listOf("Na het eten de tafel afruimen", "Elke avond de tassen klaarzetten"),
            smallStep = "Alleen de boekentassen bij de deur",
            admin = listOf("Belastingaangifte invullen", "Verzekering bellen over de fiets"),
            appointment = "Tandarts voor Mia",
            project = "IKEA kast",
            projectSteps = listOf("Kast ophalen", "Gereedschap klaarleggen"),
            pushed = "Fiets herstellen",
            free = "Filmavond",
            toSort = "Fotoboek van de vakantie maken",
            rules = listOf("Schoenen uit in de gang", "Geen schermen aan tafel"),
            friction = "De vaatwasser is nooit leeg",
            goal = "Elke maand een avondje met z’n twee",
            goalStep = "Oppas vragen",
            idea = "De was op zondag, dan is maandag rustig",
            shopping = listOf("Brood" to null, "Bananen" to null, "Paracetamol" to "Apotheek"),
            stockLow = "Eieren",
            fridge = "Koelkast",
            meals = listOf(
                "Spaghetti bolognese" to listOf("Gehakt", "Tomatensaus", "Spaghetti"),
                "Curry met kip" to listOf("Kip", "Kokosmelk", "Rijst"),
                "Soep met brood" to listOf("Soepgroenten"),
            ),
            recipe = "Lasagne van oma",
            recipeIngredients = listOf("Gehakt", "Tomatensaus", "Lasagnebladen", "Kaas"),
            recipeNotes = "Saus een uur laten sudderen. 40 minuten in de oven op 180°.",
            win = "Elke avond samen gegeten",
        )

        private val EN = Examples(
            members = listOf("Sam", "Noor"),
            chores = listOf("Change the sheets", "Water the plants"),
            habits = listOf("Clear the table after dinner", "Get the bags ready in the evening"),
            smallStep = "Just the school bags by the door",
            admin = listOf("Do the tax return", "Call the insurance about the bike"),
            appointment = "Dentist for Mia",
            project = "IKEA wardrobe",
            projectSteps = listOf("Pick up the wardrobe", "Lay out the tools"),
            pushed = "Fix the bike",
            free = "Movie night",
            toSort = "Make a photo book of the holiday",
            rules = listOf("Shoes off in the hallway", "No screens at the table"),
            friction = "The dishwasher is never emptied",
            goal = "A night out for the two of us every month",
            goalStep = "Ask a babysitter",
            idea = "Laundry on Sunday, so Monday is calm",
            shopping = listOf("Bread" to null, "Bananas" to null, "Paracetamol" to "Pharmacy"),
            stockLow = "Eggs",
            fridge = "Fridge",
            meals = listOf(
                "Spaghetti bolognese" to listOf("Minced meat", "Tomato sauce", "Spaghetti"),
                "Chicken curry" to listOf("Chicken", "Coconut milk", "Rice"),
                "Soup and bread" to listOf("Soup vegetables"),
            ),
            recipe = "Grandma’s lasagne",
            recipeIngredients = listOf("Minced meat", "Tomato sauce", "Lasagne sheets", "Cheese"),
            recipeNotes = "Let the sauce simmer for an hour. 40 minutes in the oven at 180°.",
            win = "Had dinner together every night",
        )
    }
}
