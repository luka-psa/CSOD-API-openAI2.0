package app.hearth.ai.route

/**
 * Reads a recipe from how someone tells it: "We maakten lasagne met gehakt, tomatensaus en kaas. 45 minuten
 * in de oven." gives the dish, what to buy, and the rest as notes. Good enough to prefill; people can edit.
 */
data class RecipeHint(val name: String?, val ingredients: List<String>, val notes: String?)

object RecipeWords {
    private val opener = Regex(
        "^(?:recept\\s*:\\s*|recipe\\s*:\\s*)?(?:(?:vandaag\\s+|gisteren\\s+)?(?:we|wij|ik)\\s+(?:maakten|maakte|hebben|heb|aten|kookten|kookte)\\s+|" +
            "(?:vandaag|gisteren|vanavond)\\s+(?:maakten|maakte|hebben|heb|aten|kookten|kookte)\\s+(?:we|wij|ik)\\s+|" +
            "(?:today\\s+|yesterday\\s+)?(?:we|i)\\s+(?:made|cooked|had)\\s+)?",
        RegexOption.IGNORE_CASE,
    )
    private val with = Regex("^(.+?)\\s+(?:met|with)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val listed = Regex("(?:ingrediënten|ingredienten|nodig|ingredients|you need)\\s*:\\s*([^.\\n]+)", RegexOption.IGNORE_CASE)
    private val tail = Regex("\\s+(?:gemaakt|gegeten|gekookt)$", RegexOption.IGNORE_CASE)
    private val article = Regex("^(?:een|de|het|a|an|the|onze|our)\\s+", RegexOption.IGNORE_CASE)

    fun parse(text: String): RecipeHint {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return RecipeHint(null, emptyList(), null)
        val sentences = trimmed.split(Regex("(?<=[.!?])\\s+|\\n+")).map { it.trim().trimEnd('.', '!') }.filter { it.isNotEmpty() }
        val first = sentences.first().replace(opener, "").trim()

        var name: String? = null
        var ingredients = emptyList<String>()
        val explicit = listed.find(trimmed)
        val headline = first.replace(listed, "").trim().trimEnd(',', ':')
        val withMatch = with.matchEntire(headline)
        if (withMatch != null) {
            name = withMatch.groupValues[1]
            ingredients = split(withMatch.groupValues[2])
        } else if (headline.contains(":")) {
            name = headline.substringBefore(":")
            ingredients = split(headline.substringAfter(":"))
        } else if (headline.isNotEmpty() && headline.split(" ").size <= 6) {
            name = headline
        }
        if (explicit != null) ingredients = (ingredients + split(explicit.groupValues[1])).distinctBy { it.lowercase() }
        name = name?.replace(tail, "")?.replace(article, "")?.trim()?.ifEmpty { null }?.replaceFirstChar { it.uppercase() }

        val notes = sentences.drop(1).filterNot { listed.containsMatchIn(it) }.joinToString(". ").ifEmpty { null }
        return RecipeHint(name, ingredients, notes)
    }

    /** "gehakt, tomatensaus en kaas" → [gehakt, tomatensaus, kaas]; drops "wat" and "een beetje". */
    private fun split(text: String): List<String> =
        text.split(Regex("\\s*(?:,|;|\\ben\\b|\\band\\b|\\+)\\s*"))
            .map { it.trim().replace(Regex("^(?:wat|een beetje|een|some|a little|a)\\s+", RegexOption.IGNORE_CASE), "") }
            .filter { it.isNotEmpty() && it.split(" ").size <= 4 }
}
