package app.hearth.core.tools.week

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.model.MeterValue
import app.hearth.core.model.Ease
import app.hearth.core.model.isHabit
import app.hearth.core.model.RuleCheck
import app.hearth.core.model.flowSuggestion
import app.hearth.core.model.strainSuggestion
import app.hearth.core.model.Win
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
import app.hearth.core.model.Topic
import app.hearth.core.model.isRoutine
import app.hearth.core.model.topic
import app.hearth.core.model.WeekLook
import app.hearth.core.model.glance
import app.hearth.core.model.title
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

// The Weekoverleg: a look back, then the household by topic (free time, goals, admin, big tasks, household, rhythms, shopping, menu, frictions, house rules).

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

/** Something that asks for attention, with its colour. */
@Serializable
data class AttentionItem(val item: BacklogItem, val ease: Ease)

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
    val frictions: List<BoardItem>,
    val wins: List<Win> = emptyList(),
    /** The two household meters: on unless switched off, with Hearth's suggestions and what was agreed so far. */
    val metersOn: Boolean = true,
    val suggestedFlow: Int = 4,
    val suggestedStrain: Int = 1,
    val meters: List<MeterValue> = emptyList(),
    /** House rules checked this meeting (worked / not yet), never who. */
    val ruleChecks: List<RuleCheck> = emptyList(),
    /** All habits with how hard they are (the family's rating; unrated ones too, to rate in the meeting). */
    val habits: List<BacklogItem> = emptyList(),
    /** Chores that slip and tasks that keep being postponed, with their colour: make them smaller or easier. */
    val needsAttention: List<AttentionItem> = emptyList(),
    /** What Luister mee heard in this meeting so far, line by line. */
    val transcript: List<String> = emptyList(),
    /** Each topic of the meeting in a few words, in the order the tiles show them. */
    val topics: List<TopicLine> = emptyList(),
    override val summary: String,
) : ToolResult

/** One tile of the weekoverleg: "Admin & afspraken: 3 open · 1 wacht op antwoord". */
@Serializable
data class TopicLine(val topic: Topic, val title: String, val glance: String)

/** Builds the agenda for the screen and for the family's AI alike. */
class WeekoverlegAgendaTool(
    private val week: WeekRepository,
    private val backlog: BacklogRepository,
    private val board: BoardRepository,
    private val shopping: ShoppingRepository,
    private val meeting: MeetingRepository,
    private val settings: SettingsRepository,
) : Tool<AgendaArgs, WeekAgenda> {
    override val name = NAME
    override val description =
        "The agenda for the weekly meeting (weekoverleg): what got done since last time and what went well, then " +
            "the household by topic (free time, goals, admin & appointments, big tasks, household, rhythms & routines by moment of the day, shopping, week menu, frictions & ideas, house rules), the two " +
            "household meters, and the conversation Luister mee heard (sort it into the right tools)."
    override val argsSerializer = AgendaArgs.serializer()
    override val resultSerializer = WeekAgenda.serializer()

    @OptIn(ExperimentalTime::class)
    override suspend fun run(args: AgendaArgs, ctx: ToolContext): WeekAgenda {
        val last = week.last()
        val since = last?.createdAt ?: ctx.today.plusDays(-7).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val open = backlog.openItems()
        val done = backlog.doneItemsSince(since)
        val boardWins = board.allItems().filter { (it.closedAt ?: 0) >= since && it.status != BoardStatus.OPEN }
        val meals = week.mealsBetween(ctx.today.toString(), ctx.today.plusDays(7).toString())
        val openShopping = shopping.openItems().size
        val deadlines = deadlinesThisWeek(open, ctx.today)
        val routines = dueRoutines(open, ctx.today)
        val frictions = board.allItems().filter { it.isWorkingPoint && it.isOpen }
        val goals = board.allItems().filter { it.kind == BoardKind.GOAL && it.isOpen }
        val wins = meeting.currentWins()
        val metersOn = metersOn(settings)
        val meters = meeting.currentMeters()
        val ruleChecks = meeting.currentRuleChecks()
        val habits = open.filter { it.isHabit }
        val attention = needingAttention(backlog, ctx.today).map { (item, ease) -> AttentionItem(item, ease) }
        val newFrictions = frictions.count { it.kind == BoardKind.FRICTION && it.createdAt >= since }
        val suggestedFlow = flowSuggestion(done.size, wins.size, attention.size + habits.count { it.ease == Ease.HARD })
        val suggestedStrain = strainSuggestion(newFrictions, ruleChecks.count { !it.worked })
        val transcript = meeting.currentTranscript().map { it.text }

        val look = WeekLook(open, board.allItems(), meals.size, openShopping, ctx.today)
        val topics = Topic.entries.map { TopicLine(it, it.title(ctx.lang), it.glance(look, ctx.lang)) }
        val summary = buildList {
            add(ctx.t("Terugblik: ${done.size} ${if (done.size == 1) "ding" else "dingen"} gedaan", "Look back: ${done.size} ${if (done.size == 1) "thing" else "things"} done") +
                (if (boardWins.isNotEmpty()) ctx.t(", ${boardWins.size} op het werkbord", ", ${boardWins.size} on the board") else "") +
                (if (wins.isNotEmpty()) ctx.t(". Wat ging goed: ", ". What went well: ") + wins.joinToString(", ") { it.text } else "") + ".")
            topics.forEach { t ->
                val extra = when (t.topic) {
                    Topic.ADMIN -> deadlines.takeIf { it.isNotEmpty() }?.let { ctx.t(" (klaar tegen: ", " (finish by: ") + it.joinToString(", ") { d -> d.text } + ")" }
                    Topic.HOME -> routines.filter { it.topic == Topic.HOME }.takeIf { it.isNotEmpty() }?.let { ctx.t(" (aan de beurt: ", " (due: ") + it.joinToString(", ") { r -> r.text } + ")" }
                    Topic.RHYTHM -> listOfNotNull(
                        open.filter { it.topic == Topic.RHYTHM && !it.isStep }.takeIf { it.isNotEmpty() }?.let { list ->
                            list.groupBy { it.anchor }.entries.sortedBy { it.key?.ordinal ?: Int.MAX_VALUE }.joinToString("; ") { (a, items) ->
                                (a?.title(ctx.lang) ?: ctx.t("Wanneer het past", "Whenever it fits")) + ": " + items.joinToString(", ") { h -> h.text + (h.ease?.let { e -> " – " + easeWord(e, ctx) } ?: "") }
                            }
                        },
                        attention.filter { it.item.isRoutine() }.takeIf { it.isNotEmpty() }?.let { ctx.t("vraagt aandacht: ", "asks for attention: ") + it.joinToString(", ") { a -> attentionText(a.item, a.ease, ctx) } },
                    ).takeIf { it.isNotEmpty() }?.joinToString("; ", " (", ")")
                    Topic.MENU -> meals.takeIf { it.isNotEmpty() }?.let { " (" + it.joinToString(", ") { m -> m.dish } + ")" }
                    Topic.GOALS -> goals.takeIf { it.isNotEmpty() }?.let { " (" + it.joinToString(", ") { g -> g.text + (g.nextStep?.let { n -> ctx.t(" – volgende stap: ", " – next step: ") + n } ?: "") } + ")" }
                    Topic.FRICTION -> frictions.takeIf { it.isNotEmpty() }?.let {
                        " (" + it.joinToString(", ") { f -> f.text + (f.direction?.let { d -> " → " + app.hearth.core.tools.board.directionWords(d, ctx) } ?: "") } + ")"
                    }
                    Topic.RULES -> ruleChecks.mapNotNull { c -> board.allItems().firstOrNull { it.id == c.ruleId }?.let { it.text + ": " + easeWord(c.ease, ctx) } }
                        .takeIf { it.isNotEmpty() }?.joinToString(", ", " (", ")")
                    else -> null
                }
                add("${t.title}: ${t.glance}${extra.orEmpty()}.")
            }
            if (metersOn) add(ctx.t("Meters (samen afspreken): hoe liep het, voorstel ", "Meters (agree together): how it ran, suggested ") + suggestedFlow +
                ctx.t("; wat schuurde, voorstel ", "; what rubbed, suggested ") + suggestedStrain + ".")
            if (transcript.isNotEmpty()) add(ctx.t("Gesprek (Luister mee):\n", "Conversation (Luister mee):\n") + transcript.joinToString("\n") { "- $it" })
        }.joinToString("\n")

        return WeekAgenda(since, last, done, boardWins, meals, openShopping, deadlines, routines, frictions, wins, metersOn, suggestedFlow, suggestedStrain, meters, ruleChecks, habits, attention, transcript, topics, summary)
    }

    companion object {
        const val NAME = "weekoverleg_agenda"
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
