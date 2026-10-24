package app.hearth.ai.route

import app.hearth.core.model.Section
import kotlinx.datetime.LocalDate

/** Where a sentence from the weekoverleg goes. */
enum class Place {
    /** A task or project with or without a day (Taken). */
    PLANNING,
    /** An appointment on a day; with Google Calendar on, it shows there too. */
    EVENT,
    /** Protected time: rest, fun, nothing planned. */
    FREE_TIME,
    SHOPPING,
    MEAL,
    HOUSE_RULE,
    FRICTION,
    /** A way to make it easier or quicker. */
    IDEA,
    GOAL,
    /** Wat ging goed. */
    WIN,
    /** Just talk: kept in the transcript, nothing added. */
    NONE,
}

/**
 * Sorts what was said in the weekoverleg, sentence by sentence. Rough on purpose: everything lands in a
 * Voorstel the family checks with one tap, and the transcript keeps what was said either way.
 */
object MeetingWords {
    /** Stretches of talk, split into sentences and at "en dan", "verder", "oh ja"… */
    fun sentences(lines: List<String>): List<String> = lines
        .flatMap { it.split(SENTENCE_END) }
        .flatMap { it.split(CONNECTORS) }
        .map { it.trim().trim(',', ';').trim() }
        .filter { it.split(Regex("\\s+")).size >= 2 }

    /** The best guess for one sentence; the family can change it in the Voorstel. */
    fun place(sentence: String, today: LocalDate): Place {
        AreaWords.extract(sentence)?.let { return placeOf(it.section) }
        if (MealWords.extract(sentence, today) != null) return Place.MEAL
        val t = " ${sentence.lowercase()} "
        return when {
            RULE.any { it in t } -> Place.HOUSE_RULE
            WIN.any { it in t } -> Place.WIN
            IDEA.any { it in t } -> Place.IDEA
            FRICTION.any { it in t } -> Place.FRICTION
            AGENDA.any { it in t } -> Place.EVENT
            FREE.any { it in t } -> Place.FREE_TIME
            SHOPPING.any { it in t } -> Place.SHOPPING
            else -> when (KeywordRouter.section(sentence)) {
                Section.PROTECTED_TIME -> Place.FREE_TIME
                Section.APPOINTMENT -> Place.EVENT
                Section.NOTE -> Place.HOUSE_RULE
                Section.FRICTION -> Place.FRICTION
                Section.GOAL -> Place.GOAL
                Section.IDEA -> Place.IDEA
                Section.SHOPPING, Section.STOCK -> Place.SHOPPING
                Section.PROJECT, Section.ADMIN, Section.CHORE, Section.RHYTHM, Section.HABIT -> Place.PLANNING
                Section.OTHER -> Place.NONE
            }
        }
    }

    fun placeOf(section: Section): Place = when (section) {
        Section.NOTE -> Place.HOUSE_RULE
        Section.FRICTION -> Place.FRICTION
        Section.GOAL -> Place.GOAL
        Section.IDEA -> Place.IDEA
        Section.APPOINTMENT -> Place.EVENT
        Section.PROTECTED_TIME -> Place.FREE_TIME
        Section.SHOPPING, Section.STOCK -> Place.SHOPPING
        else -> Place.PLANNING
    }

    /** "we spreken af dat …", "vanaf nu …": the agreement itself, as a sentence. */
    fun ruleText(sentence: String): String {
        val m = RULE_LEAD.find(sentence) ?: return sentence.trim().replaceFirstChar { it.uppercase() }
        val rest = sentence.substring(m.range.last + 1).trim().trim(',').trim()
        if (rest.isEmpty()) return sentence.trim().replaceFirstChar { it.uppercase() }
        return (if (m.value.trimEnd().endsWith("dat")) AreaWords.mainClause(rest) else rest).replaceFirstChar { it.uppercase() }
    }

    private val SENTENCE_END = Regex("(?<=[.!?])\\s+")
    private val CONNECTORS = Regex(
        "\\s+(?:en dan|daarna|verder|oh ja|o ja|en ook|ook nog|trouwens|daarnaast|and then|also|by the way|besides)\\s+",
        RegexOption.IGNORE_CASE,
    )
    private val RULE_LEAD = Regex(
        "^(?:.*?\\s)?(?:we spreken af dat|we hebben afgesproken dat|afgesproken dat|de afspraak is dat|vanaf nu|voortaan|from now on|we agreed that|let's agree that|the rule is that)\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val RULE = listOf("we spreken af", "afgesproken", "afspraak is", "vanaf nu", "voortaan", "huisregel", "from now on", "we agreed", "let's agree", "house rule", "the rule is")
    private val WIN = listOf(
        "ging goed", "gingen goed", "liep goed", "fijn dat", "blij dat", "trots", "goed gedaan", "is gelukt", "zijn gelukt", "lukte", "top dat", "leuk dat",
        "went well", "glad that", "proud", "well done", "worked well", "great that", "nice that", "happy that",
    )
    private val IDEA = listOf(
        "efficiënter", "efficienter", "slimmer", "handiger", "makkelijker als", "sneller als", "beter als we", "wat als we", "zouden we", "kunnen we niet",
        "misschien kunnen we", "idee", "more efficient", "easier if", "smarter", "what if we", "we could", "maybe we", "idea",
    )
    private val FRICTION = listOf(
        "erger me", "ergert", "stoort", "frustr", "irritant", "ergerlijk", "lastig dat", "altijd weer", "nooit",
        "annoy", "bugs me", "drives me", "always", "never",
    )
    /** Things on the calendar later: leave, trips, birthdays, parties. */
    private val AGENDA = listOf(
        "verlof", "vakantie", "op reis", "weekendje", "citytrip", "uitstap", "verjaardag", "jarig", "feest", "trouw", "communie", "kamp",
        "holiday", "vacation", "on leave", "annual leave", "trip to", "birthday", "party", "wedding", "camp",
    )
    private val FREE = listOf(
        "vrije tijd", "uitrusten", "rust nemen", "niks plannen", "niets plannen", "niks doen", "vrij houden", "uitslapen", "iets leuks", "date", "filmavond",
        "spelletjesavond", "free time", "rest", "nothing planned", "sleep in", "day off", "movie night", "something fun",
    )
    private val SHOPPING = listOf(" kopen ", " halen ", "op de lijst", "boodschappen", "we need to buy", " buy ", "pick up", "groceries")
}
