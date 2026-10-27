package app.hearth.ai.route

import app.hearth.core.model.Section

/** Where the person said it should go, and what is left once the command words are gone. */
data class AreaHint(val section: Section, val text: String)

/**
 * Spoken commands that name a place: "voeg aan de huisregels toe dat we om 8 uur eten", "add to house
 * rules that we bring our plates", "zet fiets maken bij de projecten", "nieuw doel: samen gaan wandelen".
 * The command words never end up in the item: only "We eten om 8 uur".
 */
object AreaWords {
    // Longest names first, so "huisregels" wins over "huis".
    private val areas: List<Pair<Section, List<String>>> = listOf(
        Section.NOTE to listOf("huisregels", "huisregel", "huis regels", "huis regel", "regels", "house rules", "house rule", "houserules", "rules", "house"),
        Section.FRICTION to listOf("ergernissen", "fricties", "frictie", "irritaties", "frictions", "friction", "annoyances"),
        Section.GOAL to listOf("doelen", "doel", "goals", "goal"),
        Section.IDEA to listOf("ideeën", "ideeen", "idee", "verbeteringen", "ideas", "idea"),
        Section.PROJECT to listOf("grote taken", "grote taak", "projecten", "project", "big tasks", "projects"),
        Section.CHORE to listOf("huishoudtaken", "gewoontes", "gewoonte", "ritme", "routines", "routine", "habits", "chores"),
        Section.ADMIN to listOf("paperassen", "papierwerk", "administratie", "admin", "paperwork"),
        // "Afspraken" are appointments (the dentist), never house rules.
        Section.APPOINTMENT to listOf("afspraken", "appointments"),
        Section.OTHER to listOf("takenlijst", "taken", "backlog", "to do list", "to-do list", "todo list", "to do", "to-do", "tasks"),
    )

    private val area = areas.flatMap { it.second }.sortedByDescending { it.length }.joinToString("|") { name -> name.split(" ").joinToString("\\s+") { Regex.escape(it) } }
    private const val OWNER = "(?:de|het|onze|ons|mijn|the|our|my)\\s+"
    private const val NL_VERB = "(?:voeg|zet|schrijf|noteer|plaats|steek)"
    private const val EN_VERB = "(?:add|put|write(?:\\s+down)?|note(?:\\s+down)?|save)"
    private const val SOMETHING = "(?:(?:een|nog\\s+een|iets|a|an|another|one\\s+more|new)\\s+)?(?:(?:item|punt|ding|nieuwe?|thing|point|entry)\\s+)?"
    private const val LEAD = "(?:\\s*[:,\\-]\\s*|\\s+)(?:(dat)\\s+|that\\s+|om\\s+)?"

    private val rules = listOf(
        // Place first, then the item: "voeg (een item) aan de huisregels toe dat …", "add (an item) to house rules that …"
        Regex("^$NL_VERB\\s+(?:er\\s+)?$SOMETHING(?:toe\\s+)?(?:aan|bij|op|in|onder)\\s+(?:$OWNER)?($area)(?:\\s+toe)?$LEAD(.+)$", RegexOption.IGNORE_CASE),
        Regex("^$EN_VERB\\s+$SOMETHING(?:to|on|in|onto|under)\\s+(?:$OWNER)?($area)(?:\\s+list)?$LEAD(.+)$", RegexOption.IGNORE_CASE),
        // "nieuwe huisregel: …", "new goal that …"
        Regex("^(?:nieuwe?|extra|nog\\s+een|new|another)\\s+($area)$LEAD(.+)$", RegexOption.IGNORE_CASE),
    )

    // Item first, place last: "zet fiets maken bij de projecten", "add fixing the bike to projects".
    private val itemFirst = listOf(
        Regex("^$NL_VERB\\s+(.+?)\\s+(?:toe\\s+)?(?:aan|bij|op|in|onder)\\s+(?:$OWNER)?($area)(?:\\s+toe)?[.!]?$", RegexOption.IGNORE_CASE),
        Regex("^$EN_VERB\\s+(.+?)\\s+(?:to|on|in|onto|under)\\s+(?:$OWNER)?($area)(?:\\s+list)?[.!]?$", RegexOption.IGNORE_CASE),
    )

    fun extract(text: String): AreaHint? {
        val t = text.trim()
        for (rule in rules) {
            val m = rule.matchEntire(t) ?: continue
            val item = m.groupValues[3]
            return hint(m.groupValues[1], if (m.groupValues[2].isNotEmpty()) mainClause(item) else item)
        }
        for (rule in itemFirst) {
            val m = rule.matchEntire(t) ?: continue
            return hint(m.groupValues[2], m.groupValues[1])
        }
        return null
    }

    /**
     * After "dat" Dutch puts the verb last: "dat we onze borden naar de keuken brengen". As a rule on the
     * board it reads better as a sentence: "We brengen onze borden naar de keuken".
     */
    internal fun mainClause(clause: String): String {
        val words = clause.trim().trimEnd('.', '!').split(Regex("\\s+"))
        val first = words.first().lowercase()
        // "we …", or "de vaatwasser …" (an article and its noun).
        val subject = when {
            first in SUBJECTS -> 1
            first in ARTICLES && words.size >= 4 -> 2
            else -> return clause
        }
        if (words.size < subject + 2) return clause
        val verbAt = if (words[words.size - 2].lowercase() in MODALS) words.size - 2 else words.size - 1
        if (verbAt <= subject) return clause
        val rest = words.drop(subject).filterIndexed { i, _ -> i + subject != verbAt }
        return (words.take(subject) + words[verbAt] + rest).joinToString(" ").replaceFirstChar { it.uppercase() }
    }

    private val SUBJECTS = setOf("we", "wij", "ik", "je", "jij", "jullie", "iedereen", "niemand", "wie", "papa", "mama")
    private val ARTICLES = setOf("de", "het", "onze", "ons", "mijn")
    private val MODALS = setOf("moeten", "moet", "mogen", "mag", "kunnen", "kan", "willen", "wil", "gaan", "gaat", "zullen", "zal", "blijven", "blijft")

    private fun hint(place: String, item: String): AreaHint? {
        val name = place.lowercase().replace(Regex("\\s+"), " ")
        val section = areas.firstOrNull { (_, names) -> name in names }?.first ?: return null
        val clean = item.trim().trimEnd('.', '!').trim().replaceFirstChar { it.uppercase() }
        return if (clean.isEmpty()) null else AreaHint(section, clean)
    }
}
