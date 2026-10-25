package app.hearth.core.tools.week

import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.Recipe
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// A recipe from a link: most recipe sites describe the dish in schema.org JSON-LD (name, ingredients, steps,
// photo). Hearth reads that and saves it as one of our recipes. Nothing else from the page is kept.

/** Fetches a web page (and a photo) for an import. The tablet fetches directly; the web preview goes through the relay. */
interface PageFetcher {
    suspend fun page(url: String): String
    suspend fun image(url: String): ByteArray?
}

object NoPages : PageFetcher {
    override suspend fun page(url: String): String = throw IllegalStateException("No network")
    override suspend fun image(url: String): ByteArray? = null
}

/** What a recipe page says about its dish. */
data class PageRecipe(val name: String, val ingredients: List<String>, val steps: List<String>, val yield: String?, val image: String?)

private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }
private val LD_JSON = Regex("<script[^>]*application/ld\\+json[^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val OG_IMAGE = Regex("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

/** The recipe on a page, from its JSON-LD; null when the page has none. */
fun readRecipePage(html: String): PageRecipe? {
    val candidates = LD_JSON.findAll(html).mapNotNull { runCatching { lenient.parseToJsonElement(it.groupValues[1].trim()) }.getOrNull() }
    val recipe = candidates.flatMap { recipesIn(it) }.firstOrNull() ?: return null
    val name = recipe.text("name")?.let(::clean)?.takeIf { it.isNotBlank() } ?: return null
    val ingredients = (recipe["recipeIngredient"] ?: recipe["ingredients"]).strings().map(::clean).filter { it.isNotBlank() }
    val steps = instructions(recipe["recipeInstructions"]).map(::clean).filter { it.isNotBlank() }
    val yield = recipe["recipeYield"].strings().firstOrNull()?.let(::clean)
    val image = imageOf(recipe["image"]) ?: OG_IMAGE.find(html)?.groupValues?.get(1)
    return PageRecipe(name, ingredients, steps, yield, image)
}

private fun recipesIn(e: JsonElement): Sequence<JsonObject> = when (e) {
    is JsonArray -> e.asSequence().flatMap { recipesIn(it) }
    is JsonObject -> {
        val type = e["@type"]
        val isRecipe = type.strings().any { it.equals("Recipe", ignoreCase = true) }
        (if (isRecipe) sequenceOf(e) else emptySequence()) + (e["@graph"]?.let { recipesIn(it) } ?: emptySequence()) +
            (e["mainEntity"]?.let { recipesIn(it) } ?: emptySequence())
    }
    else -> emptySequence()
}

private fun JsonObject.text(key: String): String? = this[key].strings().firstOrNull()

private fun JsonElement?.strings(): List<String> = when (this) {
    is JsonPrimitive -> listOfNotNull(contentOrNull)
    is JsonArray -> flatMap { it.strings() }
    else -> emptyList()
}

/** A string, a list of strings, HowToSteps, or HowToSections with steps inside. */
private fun instructions(e: JsonElement?): List<String> = when (e) {
    is JsonPrimitive -> e.contentOrNull?.split(Regex("\\n+|(?<=\\.)\\s{2,}"))?.toList().orEmpty()
    is JsonArray -> e.flatMap { instructions(it) }
    is JsonObject -> e["itemListElement"]?.let { instructions(it) } ?: listOfNotNull(e.text("text") ?: e.text("name"))
    else -> emptyList()
}

private fun imageOf(e: JsonElement?): String? = when (e) {
    is JsonPrimitive -> e.contentOrNull
    is JsonArray -> e.firstNotNullOfOrNull { imageOf(it) }
    is JsonObject -> e.text("url") ?: e.text("contentUrl")
    else -> null
}

/** Text without HTML tags and entities, on one line. */
private fun clean(s: String): String = s
    .replace(Regex("<[^>]+>"), " ")
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "’").replace("&#039;", "’")
    .replace("&apos;", "’").replace("&lt;", "<").replace("&gt;", ">")
    .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { Char(it).toString() } ?: "" }
    .replace(Regex("\\s+"), " ")
    .trim()

@Serializable
data class ImportRecipeArgs(@Describe("Link to a recipe page, e.g. from a cooking site.") val url: String)

class ImportRecipeTool(private val week: WeekRepository, private val pages: PageFetcher) : Tool<ImportRecipeArgs, RecipeResult> {
    override val name = NAME
    override val description =
        "Save a recipe from a link: its name, ingredients (to buy when it's planned), the steps as how we make it, " +
            "and its photo. Works for most cooking sites."
    override val argsSerializer = ImportRecipeArgs.serializer()
    override val resultSerializer = RecipeResult.serializer()

    override suspend fun run(args: ImportRecipeArgs, ctx: ToolContext): RecipeResult {
        val url = args.url.trim().takeIf { Regex("^https?://\\S+$").matches(it) }
            ?: throw ToolException(ctx.t("Dat lijkt geen link.", "That doesn't look like a link."))
        val html = runCatching { pages.page(url) }.getOrElse {
            throw ToolException(ctx.t("Ik kon die pagina niet openen. Plak de ingrediënten gerust in de chat.", "I couldn't open that page. Feel free to paste the ingredients in the chat."))
        }
        val found = readRecipePage(html)
            ?: throw ToolException(ctx.t("Op die pagina vond ik geen recept. Plak de ingrediënten gerust in de chat.", "I found no recipe on that page. Feel free to paste the ingredients in the chat."))
        val notes = buildList {
            found.yield?.let { add(ctx.t("Voor $it", "Serves $it")) }
            found.steps.forEachIndexed { i, step -> add("${i + 1}. $step") }
            add(ctx.t("Bron: $url", "Source: $url"))
        }.joinToString("\n")
        val (recipe, _) = week.saveRecipe(found.name, found.ingredients.distinctBy { it.lowercase() }, notes)
        val photo = found.image?.let { runCatching { pages.image(it) }.getOrNull() }
        if (photo != null && photo.size in 1..MAX_PHOTO) week.setPhoto(recipe.key, photo)
        val saved: Recipe = week.recipe(recipe.name) ?: recipe
        return RecipeResult(
            saved,
            ctx.t("Recept bewaard: ${saved.name} (${saved.ingredients.size} ingrediënten). Plan het in het weekmenu wanneer je wilt.",
                "Recipe saved: ${saved.name} (${saved.ingredients.size} ingredients). Plan it on the week menu whenever you like."),
        )
    }

    companion object {
        const val NAME = "import_recipe"
        private const val MAX_PHOTO = 3_000_000
    }
}
