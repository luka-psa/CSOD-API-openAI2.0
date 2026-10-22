package app.hearth.ai.route

import app.hearth.core.model.Lang
import app.hearth.core.model.Section

/**
 * Offline stand-in for the router model: sorts by keywords (Flemish and English). Rough but instant
 * and free; it only decides which inbox label to use, so a miss is cheap.
 */
object KeywordRouter {

    private val keywords: List<Pair<Section, List<String>>> = listOf(
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
        Section.PROTECTED_TIME to listOf("filmavond", "spelletjes", "uitstap", "vrije tijd", "rust", "movie night", "game night", "day off", "date night"),
        Section.FRICTION to listOf("ergerlijk", "altijd op", "nooit", "irritant", "lastig", "annoying", "always runs out", "never", "frustrat"),
        Section.NOTE to listOf("regel", "afspraak is", "we spreken af", "geen schermen", "rule", "we agreed", "no screens"),
    )

    fun section(text: String): Section {
        val t = " ${text.lowercase()} "
        return keywords.firstOrNull { (_, words) -> words.any { it in t } }?.first ?: Section.OTHER
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
