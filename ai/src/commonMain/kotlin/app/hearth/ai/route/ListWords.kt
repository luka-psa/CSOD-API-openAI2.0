package app.hearth.ai.route

/** A pasted list: an optional heading ("Paklijst babysit", "Boodschappen") and its lines, bullets removed. */
data class ListHint(val heading: String?, val items: List<String>)

/** What a pasted list is for, read from its heading. */
enum class ListKind { SHOPPING, RECIPE, CHECKLIST, NONE }

/**
 * A list pasted into the capture bar (from WhatsApp, notes, an e-mail): one item per line, with or
 * without bullets. A heading says where it goes; without one every line is sorted on its own.
 */
object ListWords {
    private val bullet = Regex("^\\s*(?:[-*•·–—▪◦]|\\d{1,2}[.)]|\\[\\s?[xX]?\\s?])\\s*")
    private val shopping = Regex("^(?:boodschappen(?:lijst(?:je)?)?|winkellijst(?:je)?|shopping(?:\\s+list)?|supermarkt|naar de winkel)\\b", RegexOption.IGNORE_CASE)
    private val recipe = Regex("^(?:recept|recipe)\\b[:\\s]*", RegexOption.IGNORE_CASE)

    fun extract(text: String): ListHint? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null
        val first = lines.first()
        val rest = lines.drop(1)
        val restBulleted = rest.count { bullet.containsMatchIn(it) } * 2 >= rest.size
        val heading = when {
            first.endsWith(":") -> first.dropLast(1).trim()
            !bullet.containsMatchIn(first) && restBulleted -> first
            shopping.containsMatchIn(first) || recipe.containsMatchIn(first) -> first
            else -> null
        }
        val items = (if (heading != null) rest else lines).map { bullet.replace(it, "").trim().trimEnd(',', ';') }.filter { it.isNotEmpty() }
        if (items.isEmpty()) return null
        return ListHint(heading?.takeIf { it.isNotEmpty() }, items)
    }

    fun kind(heading: String?): ListKind = when {
        heading == null -> ListKind.NONE
        shopping.containsMatchIn(heading) -> ListKind.SHOPPING
        recipe.containsMatchIn(heading) -> ListKind.RECIPE
        else -> ListKind.CHECKLIST
    }

    /** "Recept lasagne" → "Lasagne". */
    fun recipeName(heading: String): String = recipe.replace(heading, "").trim().replaceFirstChar { it.uppercase() }
}
