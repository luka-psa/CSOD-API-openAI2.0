package app.hearth.core.tools.week

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.BoardStatus
import app.hearth.core.model.Huddle
import app.hearth.core.model.Meal
import app.hearth.core.model.Recipe
import app.hearth.core.model.dayLabel
import app.hearth.core.model.deadlinesThisWeek
import app.hearth.core.model.dueRoutines
import app.hearth.core.model.oudjes
import app.hearth.core.model.plusDays
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolResult
import app.hearth.core.tools.food.AddShoppingItemArgs
import app.hearth.core.tools.food.AddShoppingItemTool
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

// The Weekoverleg: meals for the week, a look back, what's on, oudjes, frictions.

private fun parseDate(date: String?, ctx: ToolContext): LocalDate =
    date?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() ?: throw ToolException(ctx.t("Welke dag? (bv. 2026-10-24)", "Which day? (e.g. 2026-10-24)")) }
        ?: ctx.today

/** "spaghetti, gehakt en tomaten" → [spaghetti, gehakt, tomaten]. */
fun splitIngredients(text: String): List<String> =
    text.split(Regex("\\s*(?:,|;|\\n|\\ben\\b|\\band\\b|\\+)\\s*")).map { it.trim() }.filter { it.isNotEmpty() }

// ---------- plan_meal ----------

@Serializable
data class PlanMealArgs(
    @Describe("The dish, e.g. 'Spaghetti bolognese'.")
    val dish: String,
    @Describe("Day as yyyy-MM-dd. Default: today.")
    val date: String? = null,
    @Describe("Ingredients to buy. Leave empty to reuse the ones from last time we had this dish.")
    val ingredients: List<String> = emptyList(),
    @Describe("Put the ingredients on the shopping list (default true).")
    val toShopping: Boolean = true,
)

@Serializable
data class MealResult(val meal: Meal, override val summary: String) : ToolResult

class PlanMealTool(private val week: WeekRepository, private val addShopping: AddShoppingItemTool) : Tool<PlanMealArgs, MealResult> {
    override val name = NAME
    override val description =
        "Plan what we eat on a day (one meal per day; planning it again replaces it). Its ingredients go onto the " +
            "shopping list; a dish we had before brings its ingredients along."
    override val argsSerializer = PlanMealArgs.serializer()
    override val resultSerializer = MealResult.serializer()

    override suspend fun run(args: PlanMealArgs, ctx: ToolContext): MealResult {
        val dish = args.dish.trim().replaceFirstChar { it.uppercase() }.ifEmpty { throw ToolException(ctx.t("Wat eten we?", "What are we eating?")) }
        val day = parseDate(args.date, ctx)
        val ingredients = args.ingredients.flatMap(::splitIngredients).ifEmpty { week.earlierDish(dish)?.ingredients.orEmpty() }
        val meal = week.setMeal(day.toString(), dish, ingredients)
        if (args.toShopping) ingredients.forEach { addShopping.run(AddShoppingItemArgs(it), ctx) }
        val label = dayLabel(day, ctx.today, ctx.lang)
        val extra = if (args.toShopping && ingredients.isNotEmpty()) ctx.t(" · ${ingredients.size} op de lijst", " · ${ingredients.size} on the list") else ""
        return MealResult(meal, ctx.t("${label.replaceFirstChar { it.uppercase() }}: $dish$extra", "${label.replaceFirstChar { it.uppercase() }}: $dish$extra"))
    }

    companion object {
        const val NAME = "plan_meal"
    }
}

// ---------- list_meal_plan ----------

@Serializable
data class ListMealPlanArgs(
    @Describe("First day as yyyy-MM-dd. Default: today.")
    val from: String? = null,
    @Describe("How many days (default 7).")
    val days: Int = 7,
)

@Serializable
data class MealsResult(val meals: List<Meal>, override val summary: String) : ToolResult

class ListMealPlanTool(private val week: WeekRepository) : Tool<ListMealPlanArgs, MealsResult> {
    override val name = NAME
    override val description = "Show the meal plan for the coming days."
    override val argsSerializer = ListMealPlanArgs.serializer()
    override val resultSerializer = MealsResult.serializer()

    override suspend fun run(args: ListMealPlanArgs, ctx: ToolContext): MealsResult {
        val from = parseDate(args.from, ctx)
        val meals = week.mealsBetween(from.toString(), from.plusDays(args.days.coerceIn(1, 31)).toString())
        val summary = if (meals.isEmpty()) ctx.t("Nog niets gepland om te eten.", "No meals planned yet.")
        else meals.joinToString("\n") { m ->
            val day = runCatching { LocalDate.parse(m.date) }.getOrNull()?.let { dayLabel(it, ctx.today, ctx.lang) } ?: m.date
            "${day.replaceFirstChar { it.uppercase() }}: ${m.dish}"
        }
        return MealsResult(meals, summary)
    }

    companion object {
        const val NAME = "list_meal_plan"
    }
}

// ---------- remove_meal (destructive) ----------

@Serializable
data class RemoveMealArgs(
    @Describe("Id of the meal.")
    val mealId: String? = null,
    @Describe("Or the day as yyyy-MM-dd.")
    val date: String? = null,
)

class RemoveMealTool(private val week: WeekRepository) : Tool<RemoveMealArgs, MealsResult> {
    override val name = NAME
    override val description = "Take a meal off the plan (its shopping items stay on the list)."
    override val destructive = true
    override val argsSerializer = RemoveMealArgs.serializer()
    override val resultSerializer = MealsResult.serializer()

    override fun confirmQuestion(args: RemoveMealArgs, ctx: ToolContext) =
        ctx.t("Deze maaltijd van het plan halen?", "Take this meal off the plan?")

    override suspend fun run(args: RemoveMealArgs, ctx: ToolContext): MealsResult {
        val meal = args.date?.let { week.mealOn(it.trim()) }
            ?: args.mealId?.let { id -> week.mealsBetween("0000-01-01", "9999-12-31").firstOrNull { it.id == id } }
            ?: throw ToolException(ctx.t("Die maaltijd vind ik niet.", "I can't find that meal."))
        week.deleteMeals(listOf(meal.id))
        return MealsResult(listOf(meal), ctx.t("Van het plan: ${meal.dish}", "Off the plan: ${meal.dish}"))
    }

    companion object {
        const val NAME = "remove_meal"
    }
}

// ---------- weekoverleg_agenda ----------

@Serializable
class AgendaArgs

@Serializable
data class WeekAgenda(
    val since: Long,
    val lastHuddle: Huddle?,
    val done: List<BacklogItem>,
    val boardWins: List<BoardItem>,
    val meals: List<Meal>,
    val openShopping: Int,
    val deadlines: List<BacklogItem>,
    val dueRoutines: List<BacklogItem>,
    val oudjes: List<BacklogItem>,
    val frictions: List<BoardItem>,
    override val summary: String,
) : ToolResult

/** Builds the agenda for the screen and for the family's AI alike. */
class WeekoverlegAgendaTool(
    private val week: WeekRepository,
    private val backlog: BacklogRepository,
    private val board: BoardRepository,
    private val shopping: ShoppingRepository,
) : Tool<AgendaArgs, WeekAgenda> {
    override val name = NAME
    override val description =
        "The agenda for the weekly meeting (weekoverleg): what got done since last time, meals for the week, the " +
            "shopping list, deadlines and routines this week, oudjes (longest open, pick a few) and open frictions."
    override val argsSerializer = AgendaArgs.serializer()
    override val resultSerializer = WeekAgenda.serializer()

    @OptIn(ExperimentalTime::class)
    override suspend fun run(args: AgendaArgs, ctx: ToolContext): WeekAgenda {
        val last = week.last()
        val since = last?.createdAt ?: ctx.today.plusDays(-7).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val open = backlog.openItems()
        val done = backlog.doneItemsSince(since)
        val wins = board.allItems().filter { (it.closedAt ?: 0) >= since && it.status != BoardStatus.OPEN }
        val meals = week.mealsBetween(ctx.today.toString(), ctx.today.plusDays(7).toString())
        val openShopping = shopping.openItems().size
        val deadlines = deadlinesThisWeek(open, ctx.today)
        val routines = dueRoutines(open, ctx.today)
        val old = oudjes(open)
        val frictions = board.allItems().filter { it.kind == BoardKind.FRICTION && it.isOpen }

        val summary = buildList {
            add(ctx.t("1. Terugblik: ${done.size} dingen gedaan", "1. Look back: ${done.size} things done") +
                (if (wins.isNotEmpty()) ctx.t(", ${wins.size} op het werkbord", ", ${wins.size} on the board") else "") + ".")
            add(ctx.t("2. Maaltijden: ${meals.size} van 7 dagen gepland", "2. Meals: ${meals.size} of 7 days planned") +
                (if (meals.isNotEmpty()) " (" + meals.joinToString(", ") { it.dish } + ")" else "") + ".")
            add(ctx.t("3. Boodschappen: $openShopping op de lijst.", "3. Shopping: $openShopping on the list."))
            add(ctx.t("4. Deze week: ", "4. This week: ") +
                listOfNotNull(
                    deadlines.takeIf { it.isNotEmpty() }?.let { ctx.t("deadlines ", "deadlines ") + it.joinToString(", ") { d -> d.text } },
                    routines.takeIf { it.isNotEmpty() }?.let { ctx.t("aan de beurt ", "due ") + it.joinToString(", ") { r -> r.text } },
                ).joinToString("; ").ifEmpty { ctx.t("rustig", "calm") } + ".")
            add(ctx.t("5. Oudjes: ", "5. Oudjes: ") + old.joinToString(", ") { it.text }.ifEmpty { ctx.t("geen", "none") } + ".")
            add(ctx.t("6. Werkbord: ", "6. Board: ") + frictions.joinToString(", ") { it.text }.ifEmpty { ctx.t("geen frictie", "no frictions") } + ".")
        }.joinToString("\n")

        return WeekAgenda(since, last, done, wins, meals, openShopping, deadlines, routines, old, frictions, summary)
    }

    companion object {
        const val NAME = "weekoverleg_agenda"
    }
}

// ---------- finish_weekoverleg ----------

@Serializable
data class HuddleResult(val huddle: Huddle, override val summary: String) : ToolResult

class FinishWeekoverlegTool(private val week: WeekRepository) : Tool<AgendaArgs, HuddleResult> {
    override val name = NAME
    override val description = "Mark this week's meeting as held, so next week's look back starts from today."
    override val argsSerializer = AgendaArgs.serializer()
    override val resultSerializer = HuddleResult.serializer()

    override suspend fun run(args: AgendaArgs, ctx: ToolContext): HuddleResult {
        val huddle = week.recordHuddle(ctx.today.toString())
        return HuddleResult(huddle, ctx.t("Mooi weekoverleg! Jullie zijn klaar voor de week.", "Nice weekly meeting! You're set for the week."))
    }

    companion object {
        const val NAME = "finish_weekoverleg"
    }
}

// ---------- recipes ----------

@Serializable
data class SaveRecipeArgs(
    @Describe("The dish, e.g. 'Lasagne van oma'.")
    val name: String,
    @Describe("What to buy for it, e.g. ['gehakt', 'tomatensaus', 'lasagnebladen'].")
    val ingredients: List<String> = emptyList(),
    @Describe("How we made it, in our own words (steps, oven time, tips).")
    val notes: String? = null,
    @Describe("When editing: the recipe's current key (its old name), so a rename keeps the photo.")
    val recipeKey: String? = null,
)

@Serializable
data class RecipeResult(val recipe: Recipe, override val summary: String) : ToolResult

class SaveRecipeTool(private val week: WeekRepository) : Tool<SaveRecipeArgs, RecipeResult> {
    override val name = NAME
    override val description =
        "Save a recipe the family cooked: name, ingredients to buy, and how they made it. It can then be put " +
            "on the week menu with plan_meal, which puts its ingredients on the shopping list."
    override val argsSerializer = SaveRecipeArgs.serializer()
    override val resultSerializer = RecipeResult.serializer()

    override suspend fun run(args: SaveRecipeArgs, ctx: ToolContext): RecipeResult {
        val name = args.name.trim().replaceFirstChar { it.uppercase() }.ifEmpty { throw ToolException(ctx.t("Hoe heet het gerecht?", "What's the dish called?")) }
        val ingredients = args.ingredients.flatMap(::splitIngredients).distinctBy { it.lowercase() }
        val (recipe, added) = week.saveRecipe(name, ingredients, args.notes, args.recipeKey)
        val summary = if (added) ctx.t("Recept bewaard: ${recipe.name} (${ingredients.size} ingrediënten)", "Recipe saved: ${recipe.name} (${ingredients.size} ingredients)")
        else ctx.t("Recept bijgewerkt: ${recipe.name}", "Recipe updated: ${recipe.name}")
        return RecipeResult(recipe, summary)
    }

    companion object {
        const val NAME = "save_recipe"
    }
}

@Serializable
data class ListRecipesArgs(@Describe("Optional search word, e.g. 'pasta'.") val query: String? = null)

@Serializable
data class RecipesResult(val recipes: List<Recipe>, override val summary: String) : ToolResult

class ListRecipesTool(private val week: WeekRepository) : Tool<ListRecipesArgs, RecipesResult> {
    override val name = NAME
    override val description = "Show the family's recipes (and dishes they had before), with ingredients."
    override val argsSerializer = ListRecipesArgs.serializer()
    override val resultSerializer = RecipesResult.serializer()

    override suspend fun run(args: ListRecipesArgs, ctx: ToolContext): RecipesResult {
        val q = args.query?.trim()?.lowercase().orEmpty()
        val recipes = week.allRecipes().filter { q.isEmpty() || it.name.lowercase().contains(q) || it.ingredients.any { i -> i.lowercase().contains(q) } }
        val summary = if (recipes.isEmpty()) ctx.t("Nog geen recepten.", "No recipes yet.")
        else recipes.joinToString("\n") { r -> r.name + (if (r.ingredients.isNotEmpty()) ": " + r.ingredients.joinToString(", ") else "") }
        return RecipesResult(recipes, summary)
    }

    companion object {
        const val NAME = "list_recipes"
    }
}

@Serializable
data class RecipeRefArgs(@Describe("The recipe's name or key.") val recipe: String)

class RemoveRecipeTool(private val week: WeekRepository) : Tool<RecipeRefArgs, RecipeResult> {
    override val name = NAME
    override val description = "Delete a recipe (meals already on the menu stay)."
    override val destructive = true
    override val argsSerializer = RecipeRefArgs.serializer()
    override val resultSerializer = RecipeResult.serializer()

    override fun confirmQuestion(args: RecipeRefArgs, ctx: ToolContext) =
        ctx.t("Recept “${args.recipe}” verwijderen?", "Delete the recipe “${args.recipe}”?")

    override suspend fun run(args: RecipeRefArgs, ctx: ToolContext): RecipeResult {
        val recipe = week.recipe(args.recipe) ?: throw ToolException(ctx.t("Dat recept vind ik niet.", "I can't find that recipe."))
        week.deleteRecipe(recipe.key)
        return RecipeResult(recipe, ctx.t("Recept verwijderd: ${recipe.name}", "Recipe deleted: ${recipe.name}"))
    }

    companion object {
        const val NAME = "remove_recipe"
    }
}

@Serializable
data class SetRecipePhotoArgs(
    @Describe("The recipe's key.")
    val recipeKey: String,
    @Describe("JPEG as base64, or empty to remove the photo.")
    val jpegBase64: String = "",
)

/** The tablet's camera or photo picker; photos are too big for an AI chat, so this is tablet-only. */
class SetRecipePhotoTool(private val week: WeekRepository) : Tool<SetRecipePhotoArgs, RecipeResult> {
    override val name = NAME
    override val description = "Set or remove a recipe's photo (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = SetRecipePhotoArgs.serializer()
    override val resultSerializer = RecipeResult.serializer()

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    override suspend fun run(args: SetRecipePhotoArgs, ctx: ToolContext): RecipeResult {
        val recipe = week.recipe(args.recipeKey) ?: throw ToolException(ctx.t("Dat recept vind ik niet.", "I can't find that recipe."))
        val bytes = args.jpegBase64.takeIf { it.isNotBlank() }?.let { kotlin.io.encoding.Base64.decode(it) }
        if (bytes != null && bytes.size > MAX_BYTES) throw ToolException(ctx.t("Die foto is te groot.", "That photo is too big."))
        week.setPhoto(recipe.key, bytes)
        return RecipeResult(recipe.copy(hasPhoto = bytes != null), if (bytes != null) ctx.t("Foto bij ${recipe.name}", "Photo added to ${recipe.name}") else ctx.t("Foto weg bij ${recipe.name}", "Photo removed from ${recipe.name}"))
    }

    companion object {
        const val NAME = "set_recipe_photo"
        const val MAX_BYTES = 1_500_000
    }
}
