package app.hearth.ai.route

import app.hearth.core.model.Section
import app.hearth.core.tools.backlog.When
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

/** One thing the family concluded in the talk: the words it rests on, where it goes, and all that was said about it. */
data class Conclusion(val sentence: String, val place: Place, val heard: String)

/**
 * Stretches of talk that belong together, however long the pauses between them: "Mia gaat donderdag naar de
 * opvang" … "dus die moet gebracht worden" … "voor de rest ben ik vrij". [lines] are the indexes it was heard in.
 */
data class Thought(val heard: String, val lines: IntRange, val conclusions: List<Conclusion>)

/**
 * Sorts what was said in the weekoverleg. People pause mid-thought, so a pause is never where Hearth cuts: stretches
 * are joined into thoughts by how they sound ("dus …", "voor de rest …", a half sentence), and each thought gives
 * its conclusions (what, who, which day), not a label per breath. Rough on purpose: everything lands in a Voorstel
 * the family checks with one tap, and the transcript keeps what was said either way.
 */
object MeetingWords {
    /** The conversation as thoughts. [people] are the household's names; [times] (epoch millis per line) split long silences. */
    fun thoughts(lines: List<String>, today: LocalDate, people: Collection<String> = emptyList(), times: List<Long>? = null): List<Thought> {
        val groups = mutableListOf<Group>()
        lines.forEachIndexed { i, line ->
            line.split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { n, sentence ->
                val parts = clauses(sentence)
                val prev = groups.lastOrNull()
                val said = Spoken.clean(sentence)
                val quiet = n == 0 && times != null && i > 0 && times[i] - times[i - 1] > LONG_SILENCE_MS
                when {
                    prev == null || quiet || SWITCH.containsMatchIn(said) -> groups += Group(i, parts.toMutableList())
                    // "ja", "oké": heard, but nothing to add.
                    isAck(said) -> prev.extra(i, sentence)
                    // A half sentence and its rest: "deze week moet ik afspreken met" … "Anthony".
                    DANGLING_END.containsMatchIn(prev.parts.last().raw) || words(said).all { it in TIME_WORDS } -> {
                        prev.parts.last().raw += " " + parts.first().raw
                        prev.parts += parts.drop(1)
                        prev.end = i
                    }
                    continues(said) || fragment(said) -> {
                        prev.parts += listOf(Part(parts.first().raw, main = false)) + parts.drop(1)
                        prev.end = i
                    }
                    else -> groups += Group(i, parts.toMutableList())
                }
            }
        }
        return groups.map { g ->
            Thought(lines.subList(g.start, g.end + 1).joinToString(" ") { it.trim() }.trim(), g.start..g.end, conclude(g.parts, today, people))
        }
    }

    /** The best guess for one sentence; the family can change it in the Voorstel. */
    fun place(sentence: String, today: LocalDate): Place {
        AreaWords.extract(sentence)?.let { return placeOf(it.section) }
        if (MealWords.extract(sentence, today) != null) return Place.MEAL
        val t = Spoken.clean(sentence).lowercase()
        return when {
            RULE.containsMatchIn(t) -> Place.HOUSE_RULE
            WIN.containsMatchIn(t) -> Place.WIN
            IDEA.containsMatchIn(t) -> Place.IDEA
            FRICTION.containsMatchIn(t) -> Place.FRICTION
            AGENDA.containsMatchIn(t) || MEET.containsMatchIn(t) -> Place.EVENT
            FREE.containsMatchIn(t) -> Place.FREE_TIME
            SHOPPING.containsMatchIn(t) -> Place.SHOPPING
            else -> when (KeywordRouter.section(t)) {
                Section.PROTECTED_TIME -> Place.FREE_TIME
                Section.APPOINTMENT -> Place.EVENT
                Section.NOTE -> Place.HOUSE_RULE
                Section.FRICTION -> Place.FRICTION
                Section.GOAL -> Place.GOAL
                Section.IDEA -> Place.IDEA
                Section.SHOPPING, Section.STOCK -> Place.SHOPPING
                Section.PROJECT, Section.ADMIN, Section.CHORE, Section.RHYTHM, Section.HABIT -> Place.PLANNING
                Section.OTHER -> {
                    val day = TimeWords.extractAnywhere(t, today)
                    when {
                        // "donderdag gaat ze naar de opvang": somewhere to be on a day.
                        day != null && GOING.containsMatchIn(t) -> Place.EVENT
                        // "zaterdag moeten we de auto wassen": something to do on a day.
                        day != null && MODAL.containsMatchIn(t) -> Place.PLANNING
                        else -> Place.NONE
                    }
                }
            }
        }
    }

    /**
     * What an item says, from what was said: "deze week moet ik afspreken met Anthony" (the day already taken out)
     * → "Afspreken met Anthony"; "dat gaat naar de opvang" → "Naar de opvang". Names and nouns stay.
     */
    fun gist(text: String): String {
        var t = Spoken.clean(text).trim().trim(',', '.', '!', '?', ';').trim()
        repeat(3) { t = LEAD_TALK.replace(t, "").trim() }
        t = SUBJECT_VERB.replace(t, "").trim()
        t = VERB_SUBJECT.replace(t, "").trim()
        t = Regex("^(?:ook nog|nog|ook|still|also)\\s+", RegexOption.IGNORE_CASE).replace(t, "").trim()
        return t.trim(',').trim().ifBlank { text.trim() }.replaceFirstChar { it.uppercase() }
    }

    private class Part(var raw: String, val main: Boolean)

    private class Group(val start: Int, val parts: MutableList<Part>) {
        var end = start
        /** Heard and kept with the thought, but nothing to conclude from. */
        fun extra(i: Int, raw: String) {
            parts += Part(raw, main = false)
            end = i
        }
    }

    /** One sentence in clauses: "en dan", "oh ja" start a new point; "dus", "want", "maar" go on about this one. */
    private fun clauses(sentence: String): List<Part> = sentence.split(CONNECTORS).map { it.trim().trim(',', ';').trim() }.filter { it.isNotEmpty() }
        .flatMap { point -> point.split(SUBORDINATE).map { it.trim().trim(',').trim() }.filter { it.isNotEmpty() }.mapIndexed { n, c -> Part(c, main = n == 0) } }
        .ifEmpty { listOf(Part(sentence, main = true)) }

    private class Building(val sentence: String, val place: Place, val day: Pair<When, LocalDate?>?, val heard: StringBuilder)

    private fun conclude(parts: List<Part>, today: LocalDate, people: Collection<String>): List<Conclusion> {
        // "die gaat donderdag naar de opvang" is about the one person this thought names.
        val who = people.filter { name -> name.isNotBlank() && parts.any { word(name).containsMatchIn(it.raw) } }.singleOrNull()
        val out = mutableListOf<Building>()
        var before = ""
        for (part in parts) {
            val said = Spoken.clean(part.raw)
            val sentence = who?.let { name -> PRONOUN_SUBJECT.replace(said) { m -> name + m.groupValues[1] } } ?: said
            val place = if (said.isBlank() || isAck(said)) Place.NONE else place(sentence, today)
            val day = TimeWords.extractAnywhere(sentence, today)?.let { it.whenValue to it.date }
            val last = out.lastOrNull()
            val own = when {
                place == Place.NONE -> false
                last == null || part.main -> true
                // More about the same thing ("vrije tijd, gewoon bezoeken"), unless it names another day.
                place == Place.FREE_TIME || place == last.place -> day != null && day != last.day
                else -> true
            }
            when {
                own -> {
                    out += Building(sentence, place, day, StringBuilder((before + " " + part.raw).trim()))
                    before = ""
                }
                last != null -> last.heard.append(" ").append(part.raw)
                else -> before = (before + " " + part.raw).trim()
            }
        }
        return out.map { Conclusion(it.sentence, it.place, it.heard.toString().trim()) }
    }

    private fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}'’]+")).filter { it.isNotEmpty() }

    private fun word(w: String) = Regex("(?<![\\p{L}])${Regex.escape(w)}(?![\\p{L}])", RegexOption.IGNORE_CASE)

    private fun isAck(text: String) = words(text).all { it in ACKS }

    /** "dus …", "die …", "voor de rest …": goes on about what was just said. */
    private fun continues(said: String): Boolean = CONTINUATION.containsMatchIn(LEAD_ACKS.replace(said.lowercase(), ""))

    /** No one doing it and no verb that says so: "vrije tijd, gewoon bezoeken" belongs with what came before. */
    private fun fragment(said: String): Boolean = words(said).none { it in SUBJECTS || it in FINITE }

    private fun anyOf(words: List<String>) = Regex(
        "(?<![\\p{L}])(?:" + words.sortedByDescending { it.length }.joinToString("|") { it.trim().split(" ").joinToString("\\s+") { w -> Regex.escape(w) } } + ")(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )

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
        "\\s+(?:en dan|daarna|oh ja|o ja|en ook|ook nog|trouwens|daarnaast|and then|by the way|besides)\\s+",
        RegexOption.IGNORE_CASE,
    )
    private val SUBORDINATE = Regex("[,\\s]+(?=(?:dus|want|omdat|zodat|maar|so|because|but)\\s)", RegexOption.IGNORE_CASE)
    private const val LONG_SILENCE_MS = 120_000L

    /** A new point, said out loud. */
    private val SWITCH = Regex(
        "^(?:oh ja|o ja|iets anders|nog iets|volgende punt|ander punt|dan nog|verder nog|trouwens|by the way|another thing|next point|one more thing)(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )
    private val ACKS = setOf(
        "ja", "jaja", "jep", "nee", "neen", "oké", "okee", "ok", "okay", "goed", "prima", "top", "inderdaad", "juist", "klopt", "precies", "zeker",
        "allee", "bon", "zo", "mhm", "yes", "yeah", "no", "right", "sure", "fine", "great",
    )
    private val LEAD_ACKS = Regex("^(?:(?:ja|nee|neen|oké|okee|ok|okay|goed|allee|bon|yes|yeah|no|right)[,.!]?\\s+)+", RegexOption.IGNORE_CASE)
    private val CONTINUATION = Regex(
        "^(?:dat|die|dus|want|omdat|zodat|maar|en|of|dan|daar|daarom|daardoor|daarvoor|daarna|voor de rest|verder|anders|tenzij|behalve|hij|zij|hem|haar|" +
            "toch|ook|alleen|nadien|achteraf|erna|ervoor|zodra|terwijl|met|voor|naar|om|tegen|bij|van|op|zo|" +
            "so|because|but|and|or|then|there|he|she|him|her|also|otherwise|unless|with|for|to|that|which)(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )
    // Words a clause can't end on ("op", "aan", "voor" can: "we staan vroeg op").
    private val DANGLING_END = Regex(
        "(?<![\\p{L}])(?:met|en|de|een|naar|van|als|omdat|want|maar|of|te|mijn|onze|the|a|an|to|and|with|of|because|but|my|our)[,]?$",
        RegexOption.IGNORE_CASE,
    )
    /** "… zaterdag": a day said after a pause belongs to what came just before. */
    private val TIME_WORDS = setOf(
        "maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag", "vandaag", "vanavond", "morgen", "overmorgen", "weekend",
        "week", "deze", "volgende", "dit", "op", "tegen", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "today",
        "tonight", "tomorrow", "this", "next", "on", "by",
    )
    private val SUBJECTS = setOf(
        "ik", "je", "jij", "we", "wij", "jullie", "ze", "zij", "hij", "men", "iemand", "iedereen", "niemand", "i", "you", "they", "he", "she", "someone", "everyone",
    )
    private val FINITE = setOf(
        "is", "ben", "bent", "was", "waren", "heeft", "hebt", "heb", "moet", "moeten", "wil", "wilt", "willen", "kan", "kunt", "kunnen", "gaat",
        "ga", "gaan", "komt", "kom", "komen", "zal", "zult", "zullen", "mag", "mogen", "doet", "staat", "ligt", "zou", "zouden",
        "are", "am", "were", "has", "have", "need", "needs", "must", "will", "can", "should", "'s",
    )
    private val LEAD_TALK = Regex(
        "^(?:(?:ja|nee|neen|oké|okee|ok|okay|goed|allee|bon|dus|en|maar|want|omdat|dan|verder|voor de rest|trouwens|oh ja|o ja|" +
            "yes|yeah|no|so|and|but|then|anyway)(?![\\p{L}])[,.!]?\\s*)",
        RegexOption.IGNORE_CASE,
    )
    private const val PRONOUN = "(?:ik|we|wij|je|jij|jullie|ze|zij|hij|dat|die|er|men|i|we|you|they|he|she|it)"
    private const val VERB = "(?:moet|moeten|ga|gaat|gaan|wil|wilt|willen|zal|zullen|kan|kunt|kunnen|mag|mogen|zou|zouden|heb|hebt|heeft|hebben|ben|bent|is|zijn|" +
        "need to|needs to|have to|has to|must|will|should|can|am|are|'re|'m|'s|going to|'ll)"
    private val SUBJECT_VERB = Regex("^$PRONOUN\\s+$VERB(?![\\p{L}])\\s*", RegexOption.IGNORE_CASE)
    private val VERB_SUBJECT = Regex("^$VERB\\s+$PRONOUN(?![\\p{L}])\\s*", RegexOption.IGNORE_CASE)
    /** "die gaat …", "hij moet …": the person this thought is about. */
    private val PRONOUN_SUBJECT = Regex("^(?:dat|die|hij|zij)(\\s+(?:gaat|moet|heeft|komt|mag|wil|kan|is|zal))(?![\\p{L}])", RegexOption.IGNORE_CASE)

    private val RULE_LEAD = Regex(
        "^(?:.*?\\s)?(?:we spreken af dat|we hebben afgesproken dat|afgesproken dat|de afspraak is dat|vanaf nu|voortaan|from now on|we agreed that|let's agree that|the rule is that)\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val RULE = anyOf(listOf("we spreken af", "afgesproken", "afspraak is", "vanaf nu", "voortaan", "huisregel", "from now on", "we agreed", "let's agree", "house rule", "the rule is"))
    private val WIN = anyOf(
        listOf(
            "ging goed", "gingen goed", "liep goed", "fijn dat", "blij dat", "trots", "goed gedaan", "is gelukt", "zijn gelukt", "lukte", "top dat", "leuk dat",
            "went well", "glad that", "proud", "well done", "worked well", "great that", "nice that", "happy that",
        ),
    )
    private val IDEA = anyOf(
        listOf(
            "efficiënter", "efficienter", "slimmer", "handiger", "makkelijker als", "sneller als", "beter als we", "wat als we", "zouden we", "kunnen we niet",
            "misschien kunnen we", "idee", "more efficient", "easier if", "smarter", "what if we", "we could", "maybe we", "idea",
        ),
    )
    private val FRICTION = anyOf(
        listOf(
            "erger me", "ergert", "stoort", "frustrerend", "frustreert", "frustratie", "gefrustreerd", "irritant", "ergerlijk", "lastig dat", "altijd weer",
            "nooit", "ambetant", "beu", "op mijn zenuwen", "keel uit", "zot van", "kwaad", "frustrating", "frustrated", "annoying", "annoys", "annoyed",
            "bugs me", "drives me", "always", "never",
        ),
    )
    /** Things on the calendar later: leave, trips, birthdays, parties. */
    private val AGENDA = anyOf(
        listOf(
            "verlof", "vakantie", "op reis", "weekendje", "citytrip", "uitstap", "verjaardag", "jarig", "feest", "trouw", "communie", "kamp", "vrij nemen", "vrijaf",
            "holiday", "vacation", "on leave", "annual leave", "trip to", "birthday", "party", "wedding", "camp",
        ),
    )
    /** Seeing someone or being somewhere: "afspreken met Anthony", "naar de opvang", "oma bezoeken". */
    private val MEET = anyOf(
        listOf(
            "afspreken met", "afspraak met", "afspreken bij", "op bezoek", "bezoeken", "langsgaan", "langs gaan", "langskomen", "langs komen", "op visite",
            "opvang", "kinderopvang", "crèche", "creche", "onthaalmoeder", "onthaalouder", "zwemles", "muziekschool", "academie", "kinderfeestje",
            "meet up", "meet with", "meeting with", "visit", "visiting", "daycare", "playdate", "swimming lesson",
        ),
    )
    /** Going somewhere, said with a day: "donderdag gaat ze naar de opvang", "zaterdag Mia ophalen". */
    private val GOING = anyOf(listOf("naar de", "naar het", "naar", "ophalen", "oppikken", "afhalen", "brengen", "afzetten", "drop off", "go to", "going to"))
    private val MODAL = anyOf(
        listOf(
            "moet", "moeten", "ga", "gaat", "gaan", "wil", "willen", "zal", "zullen", "zou", "zouden", "kan", "kunnen", "mag", "mogen",
            "need to", "needs to", "have to", "has to", "must", "will", "should", "going to",
        ),
    )
    private val FREE = anyOf(
        listOf(
            "vrije tijd", "uitrusten", "rust nemen", "niks plannen", "niets plannen", "niks doen", "vrij houden", "uitslapen", "iets leuks", "date", "filmavond",
            "spelletjesavond", "zijn we vrij", "we zijn vrij", "hebben we vrij", "we hebben vrij", "free time", "get some rest", "take a rest", "nothing planned",
            "sleep in", "day off",
            "movie night", "something fun", "we're free",
        ),
    )
    private val SHOPPING = anyOf(listOf("kopen", "halen", "op de lijst", "boodschappen", "we need to buy", "buy", "pick up", "groceries"))
}
