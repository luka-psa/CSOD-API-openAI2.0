package app.hearth.ai.route

import app.hearth.core.model.GoalScope
import app.hearth.core.model.Lang
import app.hearth.core.model.Section

/**
 * Offline stand-in for the router model: sorts by keywords (Flemish and English). Rough but instant
 * and free; it only decides which section label a backlog item gets, so a miss is cheap.
 */
object KeywordRouter {

    // House rules first: "geen schermen na 20u is de afspraak" is an agreement, not an appointment.
    private val keywords: List<Pair<Section, List<String>>> = listOf(
        Section.NOTE to listOf(
            "huisregel", "regel", "afspraak is", "is de afspraak", "we spreken af", "afgesproken", "geen schermen",
            "house rule", "rule", "we agreed", "agreement", "no screens",
        ),
        // Clear frustration words before anything else: "ik erger me aan de rommel in de gang".
        Section.FRICTION to listOf(
            "ik erger me", "erger me", "ergernis", "stoort me", "het stoort", "frictie", "frustrerend", "irritant", "ergerlijk",
            "annoys me", "bugs me", "frustrating", "i hate that", "friction",
        ),
        Section.APPOINTMENT to listOf(
            "tandarts", "dokter", "huisarts", "osteopaat", "kine", "kinesist", "kapper", "afspraak", "autokeuring", "keuring",
            "oogarts", "dierenarts", "dentist", "doctor", "appointment", "hairdresser", "vet", "inspection", "check-up", "checkup",
        ),
        Section.ADMIN to listOf(
            "belasting", "belastingbrief", "verzekering", "formulier", "factuur", "facturen", "papieren", "bank", "mutualiteit",
            "aangifte", "contract", "tax", "insurance", "form", "invoice", "paperwork", "bills", "renew",
        ),
        Section.PROJECT to listOf(
            "ophangen", "schilderen", "verven", "monteren", "installeren", "uitkuisen", "verbouwen", "repareren", "herstellen",
            "kast", "lamp", "lampen", "plank", "ikea", "gamma", "brico", "hang", "paint", "assemble", "install", "fix", "repair",
            "renovate", "shelf", "clear out",
        ),
        Section.CHORE to listOf(
            "onkruid", "gras", "maaien", "lakens", "ramen", "kuisen", "stofzuigen", "dweilen", "strijken", "afwas", "was ",
            "badkamer", "weeds", "mow", "lawn", "sheets", "windows", "vacuum", "mop", "ironing", "laundry", "bathroom",
        ),
        Section.RHYTHM to listOf("routine", "ritueel", "ochtend", "avond", "bedtijd", "morning", "evening", "bedtime"),
        Section.GOAL to listOf(
            "ons doel", "mijn doel", "doel is", "ik wil graag", "we willen graag", "ik wil", "we willen", "zou graag",
            "our goal", "my goal", "goal is", "i want to", "we want to", "we'd like to", "i'd like to",
        ),
        Section.PROTECTED_TIME to listOf("filmavond", "spelletjes", "uitstap", "vrije tijd", "rust", "movie night", "game night", "day off", "date night"),
        Section.FRICTION to listOf("altijd op", "nooit", "lastig", "annoying", "always runs out", "never", "frustrat"),
    )

    /** "huisregel: …", "doel: …", "frictie: …" say it outright. */
    private val prefixes = listOf(
        Section.NOTE to listOf("huisregel", "regel", "afspraak", "house rule", "rule"),
        Section.FRICTION to listOf("frictie", "ergernis", "friction"),
        Section.GOAL to listOf("doel", "goal"),
    )

    fun section(text: String): Section {
        val start = text.trim().lowercase()
        prefixes.firstOrNull { (_, words) -> words.any { start.startsWith("$it:") } }?.let { return it.first }
        val t = " ${text.lowercase()} "
        return keywords.firstOrNull { (_, words) -> words.any { it in t } }?.first ?: Section.OTHER
    }

    /** "samen", "met z’n twee" → the couple; "ik wil" → personal; otherwise the family. */
    fun goalScope(text: String): GoalScope {
        val t = " ${text.lowercase()} "
        return when {
            listOf("samen", "met z’n twee", "met z'n twee", "als koppel", "together", "as a couple", "the two of us").any { it in t } -> GoalScope.COUPLE
            listOf(" ik ", " mijn ", " i ", " my ", "i'd").any { it in t } -> GoalScope.PERSONAL
            else -> GoalScope.FAMILY
        }
    }

    fun isQuestion(text: String): Boolean {
        val t = text.trim().lowercase()
        return t.endsWith("?") || QUESTION_WORDS.any { t.startsWith("$it ") }
    }

    /** Best guess at the language when no rule or model told us. */
    fun language(text: String, fallback: Lang): Lang {
        val words = text.lowercase().split(Regex("[^\\p{L}']+")).toSet()
        val nl = words.count { it in DUTCH }
        val en = words.count { it in ENGLISH }
        return when {
            nl > en -> Lang.NL
            en > nl -> Lang.EN
            else -> fallback
        }
    }

    private val QUESTION_WORDS = listOf("wat", "wie", "wanneer", "waar", "hoe", "what", "who", "when", "where", "how", "is", "are", "do", "can")
    private val DUTCH = setOf("de", "het", "een", "en", "naar", "moet", "nog", "voor", "met", "bij", "van", "is", "op", "we", "ik", "niet")
    private val ENGLISH = setOf("the", "a", "and", "to", "need", "for", "with", "at", "of", "on", "we", "i", "not", "go")
}

