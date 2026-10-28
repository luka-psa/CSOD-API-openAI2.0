package app.hearth.ai.route

import app.hearth.ai.sense.Reading
import app.hearth.ai.sense.Sense
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
    /** A routine: a moment of the day, roughly every N days, or fixed days ("elke donderdag"). */
    RHYTHM,
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
 * What a clause does in the conversation. Only what is decided becomes something: a plain plan ("donderdag moet de auto
 * naar de garage"), an agreement ("we spreken af dat …"), or a suggestion the other one says yes to.
 */
enum class Move {
    /** "ja", "dat kan ik doen", "mij ook": says yes to what was just said. */
    AGREE,
    /** "zouden we …", "misschien kan jij …", "wat als we …": an option, not yet decided. */
    PROPOSAL,
    /** "ik weet niet of dat lukt", "maar dan …", "nee": not this option. */
    DOUBT,
    /** "hoe was jouw week?", "wat staat er op de planning?". */
    QUESTION,
    /** "ik was moe", "ik heb de was niet opgevolgd", "fijn dat we samen aten": about the week that was. */
    LOOK_BACK,
    /** "we spreken af dat …", "vanaf nu …": decided together. */
    DECISION,
    /** Anything else: a plan, a fact, a complaint, or just talk. */
    STATEMENT,
}

/** Where a thought is in the conversation; while it concludes nothing, the stage shows what Hearth understood. */
enum class Stage {
    /** Bijpraten: how was your week. */
    TALK,
    /** Looking back: what went well, what we didn't do so well. */
    LOOK_BACK,
    /** What's next: days, plans, who does what. */
    PLANNING,
    /** Something that rubs. */
    CONCERN,
    /** Options on the table, nothing decided yet. */
    BRAINSTORM,
    /** Decided or agreed. */
    DECISION,
}

/**
 * Stretches of talk that belong together, however long the pauses between them: "Mia gaat donderdag naar de
 * opvang" … "dus die moet gebracht worden" … "voor de rest ben ik vrij". [lines] are the indexes it was heard in.
 */
data class Thought(val heard: String, val lines: IntRange, val conclusions: List<Conclusion>, val stage: Stage = Stage.TALK)

/**
 * Sorts what was said in the weekoverleg. People pause mid-thought, so a pause is never where Hearth cuts: stretches
 * are joined into thoughts by how they sound ("dus …", "voor de rest …", a half sentence), and each thought gives
 * its conclusions (what, who, which day), not a label per breath. Rough on purpose: everything lands in a Voorstel
 * the family checks with one tap, and the transcript keeps what was said either way.
 */
object MeetingWords {
    /** The conversation as thoughts. [people] are the household's names; [times] (epoch millis per line) split long silences. */
    fun thoughts(
        lines: List<String>,
        today: LocalDate,
        people: Collection<String> = emptyList(),
        times: List<Long>? = null,
        /** The sentence model's reading of a clause, when smarter listening is on (null: the rules alone). */
        sense: ((String) -> Reading?)? = null,
    ): List<Thought> {
        val groups = mutableListOf<Group>()
        fun moveOf(said: String) = read(said, said, today, sense).first
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
                    // "dat kan ik doen", "mij ook": a yes to what was just said, part of it.
                    moveOf(said) == Move.AGREE -> prev.extra(i, sentence)
                    // While options are on the table, the next option or doubt is part of the same brainstorm.
                    prev.parts.any { moveOf(Spoken.clean(it.raw)) == Move.PROPOSAL } && moveOf(said).let { it == Move.PROPOSAL || it == Move.DOUBT } -> {
                        prev.parts += parts
                        prev.end = i
                    }
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
        // "kan jij Mia dan ophalen": "dan" is the last day said before.
        var lastDay: String? = null
        return groups.map { g ->
            val (conclusions, stage) = conclude(g.parts, today, people, lastDay, sense)
            g.parts.forEach { p -> DAY_WORD.findAll(p.raw).lastOrNull()?.let { lastDay = it.value } }
            Thought(lines.subList(g.start, g.end + 1).joinToString(" ") { it.trim() }.trim(), g.start..g.end, conclusions, stage)
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

    private class Building(val sentence: String, val place: Place, val day: Pair<When, LocalDate?>?, val heard: StringBuilder, val agreed: Boolean = false)

    /**
     * A thought's conclusions, by what each clause does: plans and agreements count; questions, looking back on
     * ourselves and doubts are talk; an option counts once the other says yes (and only if no agreement follows that
     * replaces it); a single "misschien kunnen we …" on its own is an idea for the Werkbord. Looking back keeps what
     * went well and what rubs, never who did what.
     */
    private fun conclude(parts: List<Part>, today: LocalDate, people: Collection<String>, lastDay: String?, sense: ((String) -> Reading?)?): Pair<List<Conclusion>, Stage> {
        // "die gaat donderdag naar de opvang" is about the one person this thought names.
        val who = people.filter { name -> name.isNotBlank() && parts.any { word(name).containsMatchIn(it.raw) } }.singleOrNull()
        val out = mutableListOf<Building>()
        var before = ""
        var pending: Pair<String, StringBuilder>? = null
        val moves = mutableListOf<Move>()
        var options = 0
        var lookingBack = false
        var lone: Pair<String, StringBuilder>? = null
        // One idea, said and left there: for the Werkbord, decided later like a friction.
        fun park() {
            pending?.takeIf { options == 1 && out.none { it.agreed } && IDEA.containsMatchIn(it.first) }?.let { idea ->
                out += Building(idea.first, Place.IDEA, null, idea.second)
                lone = idea
            }
            pending = null
        }
        for (part in parts) {
            val said = Spoken.clean(part.raw)
            val sentence = who?.let { name -> PRONOUN_SUBJECT.replace(said) { m -> name + m.groupValues[1] } } ?: said
            val (read, forced) = if (said.isBlank()) Move.AGREE to null else read(said, sentence, today, sense)
            var move = read
            // "oké dus dan elke avond tien minuten": while options are on the table, a plain sentence is another option.
            if (move == Move.STATEMENT && options > 0 && out.none { it.place == Place.HOUSE_RULE } && REFINE.containsMatchIn(LEAD_ACKS.replace(said.lowercase(), ""))) move = Move.PROPOSAL
            if (move == Move.LOOK_BACK) lookingBack = true
            moves += move
            fun hear() = (out.lastOrNull()?.heard ?: pending?.second)?.append(" ")?.append(part.raw) ?: run { before = (before + " " + part.raw).trim() }
            when (move) {
                Move.PROPOSAL -> {
                    options++
                    pending = sentence to StringBuilder((before + " " + part.raw).trim())
                    before = ""
                }
                Move.AGREE -> {
                    val (option, heard) = pending ?: run { hear(); continue }
                    heard.append(" ").append(part.raw)
                    val text = withDay(option(option), lastDay)
                    val place = place(text, today).let { if (it == Place.NONE || it == Place.IDEA) Place.PLANNING else it }
                    out += Building(text, place, TimeWords.extractAnywhere(text, today)?.let { it.whenValue to it.date }, heard, agreed = true)
                    pending = null
                }
                Move.DOUBT -> {
                    pending?.second?.append(" ")?.append(part.raw) ?: hear()
                    pending = null
                }
                Move.DECISION -> {
                    // The agreement replaces the options agreed on the way to it.
                    out.removeAll { it.agreed }
                    pending = null
                    out += Building(sentence, Place.HOUSE_RULE, null, StringBuilder((before + " " + part.raw).trim()))
                    before = ""
                }
                Move.QUESTION -> hear()
                Move.LOOK_BACK -> {
                    // What went well, or what rubs; "ik heb de was niet opgevolgd" is said, not written down.
                    val place = forced ?: when {
                        WIN.containsMatchIn(said) && !NOT_GOOD.containsMatchIn(said) -> Place.WIN
                        FRICTION.containsMatchIn(said) && !SELF.containsMatchIn(said) -> Place.FRICTION
                        else -> Place.NONE
                    }
                    if (place == Place.NONE) hear()
                    else {
                        out += Building(about(sentence), place, null, StringBuilder((before + " " + part.raw).trim()))
                        before = ""
                    }
                }
                Move.STATEMENT -> {
                    var place = forced ?: if (isAck(said)) Place.NONE else place(sentence, today)
                    // Something to do needs a day, a "moeten", or to read as an item ("de verzekering bellen"); the
                    // model, when sure, has read that already.
                    if (forced == null && place in TASK_PLACES && !forward(said, today)) place = Place.NONE
                    if (forced == null && lookingBack && place in TASK_PLACES && !MODAL.containsMatchIn(said)) place = Place.NONE
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
                            // On to something else: an option still open is settled first, so a "ja" is about this.
                            park()
                            val text = if (place == Place.FRICTION || place == Place.WIN) about(sentence) else sentence
                            out += Building(text, place, day, StringBuilder((before + " " + part.raw).trim()))
                            before = ""
                        }
                        else -> hear()
                    }
                }
            }
        }
        park()
        val stage = when {
            out.any { it.agreed || it.place == Place.HOUSE_RULE } -> Stage.DECISION
            options > 0 && lone == null -> Stage.BRAINSTORM
            out.any { it.place == Place.FRICTION } -> Stage.CONCERN
            lookingBack || out.any { it.place == Place.WIN } -> Stage.LOOK_BACK
            out.isNotEmpty() || Move.QUESTION in moves && PLAN_TALK.containsMatchIn(parts.joinToString(" ") { it.raw }) -> Stage.PLANNING
            else -> Stage.TALK
        }
        return out.map { Conclusion(it.sentence, it.place, it.heard.toString().trim()) } to stage
    }

    /**
     * A clause's move and, when the sentence model decided it, its place (null: the rules choose). The rules keep what
     * they are precise at (a yes, an option, a doubt, a question; free time, a meal, shopping; what went well); the model
     * takes the rest when it is sure, like "ik ben het zat dat de badkamer nooit gepoetst wordt" (no rule knows "zat").
     */
    private fun read(said: String, sentence: String, today: LocalDate, sense: ((String) -> Reading?)?): Pair<Move, Place?> {
        val rules = move(said)
        if (sense == null || rules in TRUSTED_MOVES) return rules to null
        if (rules == Move.STATEMENT && place(sentence, today) in TRUSTED_PLACES) return rules to null
        if (rules == Move.LOOK_BACK && WIN.containsMatchIn(said) && !NOT_GOOD.containsMatchIn(said)) return rules to null
        val r = sense(said)?.takeIf { it.confidence >= SURE } ?: return rules to null
        return when (r.sense) {
            Sense.AGREE -> Move.AGREE to null
            Sense.PROPOSAL -> Move.PROPOSAL to null
            Sense.DOUBT -> Move.DOUBT to null
            Sense.QUESTION -> Move.QUESTION to null
            Sense.RULE -> Move.DECISION to null
            Sense.TALK -> Move.STATEMENT to Place.NONE
            Sense.WIN -> Move.LOOK_BACK to Place.WIN
            Sense.FRICTION -> Move.STATEMENT to Place.FRICTION
            Sense.TASK -> Move.STATEMENT to Place.PLANNING
            Sense.EVENT -> Move.STATEMENT to Place.EVENT
            Sense.FREE_TIME -> Move.STATEMENT to Place.FREE_TIME
            Sense.ROUTINE -> Move.STATEMENT to Place.RHYTHM
            // "de was doen" is a chore, not shopping: the model only gets shopping when something is bought.
            Sense.SHOPPING -> if (choreTalk(said)) rules to null else Move.STATEMENT to Place.SHOPPING
            Sense.MEAL -> Move.STATEMENT to Place.MEAL
            Sense.GOAL -> Move.STATEMENT to Place.GOAL
        }
    }

    private val TRUSTED_MOVES = setOf(Move.AGREE, Move.PROPOSAL, Move.DOUBT, Move.QUESTION)
    private val TRUSTED_PLACES = setOf(Place.FREE_TIME, Place.MEAL, Place.SHOPPING)
    /** How sure the model must be to overrule the rules (measured: 0.4 reads best). */
    private const val SURE = 0.4f

    /** What a clause does: says yes, offers an option, doubts, asks, looks back, decides, or states. */
    fun move(said: String): Move {
        val full = said.lowercase().trim()
        val t = LEAD_ACKS.replace(full, "").trim()
        val w = words(t)
        if (w.isEmpty()) return if (words(full).any { it in NO }) Move.DOUBT else Move.AGREE
        if (isAck(t)) return if (w.any { it in NO }) Move.DOUBT else Move.AGREE
        if (w.size <= 8 && AGREE.containsMatchIn(t)) return Move.AGREE
        // The meeting talking about itself ("we gaan naar de weekplanning kijken") is agenda talk, like a question.
        if (META.containsMatchIn(t) && META_VERB.containsMatchIn(t)) return Move.QUESTION
        if (TENTATIVE.containsMatchIn(t)) return Move.PROPOSAL
        if (RULE.containsMatchIn(t)) return Move.DECISION
        if (DOUBT.containsMatchIn(t)) return Move.DOUBT
        if (full.endsWith("?") || QUESTION.containsMatchIn(t)) return Move.QUESTION
        if ((PAST.containsMatchIn(t) || REFLECT.containsMatchIn(t)) && !MODAL.containsMatchIn(t)) return Move.LOOK_BACK
        return Move.STATEMENT
    }

    /** "misschien kan jij Mia dan ophalen" → "Mia dan ophalen": the option itself, once agreed. */
    private fun option(sentence: String): String {
        var t = sentence.trim()
        repeat(3) { t = LEAD_TALK.replace(t, "").trim(); t = OPTION_LEAD.replace(t, "").trim() }
        return t.ifBlank { sentence.trim() }
    }

    /** "dan" means the last day said: "Mia dan ophalen" after "donderdag" → "Mia donderdag ophalen". */
    private fun withDay(text: String, lastDay: String?): String =
        if (lastDay == null || DAY_WORD.containsMatchIn(text)) text else THEN.replaceFirst(text, lastDay)

    /** "fijn dat we samen aten", "wat ik niet leuk vind is dat de keuken vol staat" → what it is about, as a sentence. */
    private fun about(sentence: String): String {
        val m = ABOUT_THAT.find(sentence) ?: return sentence.trim().replaceFirstChar { it.uppercase() }
        val rest = sentence.substring(m.range.last + 1).trim().trim(',', '.').trim()
        if (words(rest).size < 3) return sentence.trim().replaceFirstChar { it.uppercase() }
        return AreaWords.mainClause(rest).replaceFirstChar { it.uppercase() }
    }

    /** A plan has somewhere to go: a day, a moment, a "moeten", or it reads as an item ("de verzekering bellen"). */
    private fun forward(said: String, today: LocalDate): Boolean {
        val t = LEAD_TALK.replace(said.lowercase(), "").trim()
        val w = words(t)
        return MODAL.containsMatchIn(t) || TimeWords.extractAnywhere(t, today) != null || RHYTHM_WORDS.containsMatchIn(t) ||
            (w.none { it in SUBJECTS || it in FINITE || it in PRONOUN_WORDS } && w.lastOrNull()?.endsWith("en") == true)
    }

    private fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}'’]+")).filter { it.isNotEmpty() }

    private fun word(w: String) = Regex("(?<![\\p{L}])${Regex.escape(w)}(?![\\p{L}])", RegexOption.IGNORE_CASE)

    private fun isAck(text: String) = words(text).all { it in ACKS }

    private val TASK_PLACES = setOf(Place.PLANNING, Place.EVENT, Place.FREE_TIME, Place.RHYTHM)

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
        if (!m.value.trimEnd().endsWith("dat")) return rest.replaceFirstChar { it.uppercase() }
        // "dat we elke avond de keuken opruimen": what we do, as the rule says it ("Elke avond de keuken opruimen");
        // moving "opruimen" forward would break it ("we ruimen … op").
        val words = rest.split(Regex("\\s+"))
        if (words.size >= 3 && words.first().lowercase() in setOf("we", "wij") && words.last().lowercase().endsWith("en")) {
            return words.drop(1).joinToString(" ").replaceFirstChar { it.uppercase() }
        }
        return AreaWords.mainClause(rest).replaceFirstChar { it.uppercase() }
    }

    private val SENTENCE_END = Regex("(?<=[.!?])\\s+")
    private val CONNECTORS = Regex(
        "\\s+(?:en dan|daarna|oh ja|o ja|en ook|ook nog|trouwens|daarnaast|iets anders|nog iets|and then|by the way|besides|another thing)\\s+",
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
        "ja", "jaja", "jep", "nee", "neen", "mja", "hmm", "hm", "mm", "mmm", "euh", "uhm", "oké", "okee", "ok", "okay", "goed", "prima", "top", "inderdaad", "juist", "klopt", "precies", "zeker",
        "allee", "bon", "zo", "mhm", "yes", "yeah", "no", "right", "sure", "fine", "great",
    )
    private val LEAD_ACKS = Regex("^(?:(?:ja|nee|neen|oké|okee|ok|okay|goed|allee|bon|mja|hmm|hm|euh|yes|yeah|no|right)[,.!]?\\s+)+", RegexOption.IGNORE_CASE)
    private val NO = setOf("nee", "neen", "no")
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
        "^(?:.*?\\s)?(?:we spreken af dat|spreken we af dat|we hebben afgesproken dat|afgesproken dat|de afspraak is dat|vanaf nu|voortaan|from now on|we agreed that|let's agree that|the rule is that)\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val RULE = anyOf(listOf("we spreken af", "spreken we af", "afgesproken dat", "we hebben afgesproken", "afspraak is", "vanaf nu", "voortaan", "huisregel", "from now on", "we agreed", "let's agree", "house rule", "the rule is"))

    /** A yes to what was just said. Short: "dat kan ik doen", "lijkt me haalbaar", "mij ook", "afgesproken". */
    private val AGREE = Regex(
        "^(?:afgesproken|akkoord|deal|top|prima|goed zo|doen we|is goed|dat is goed|klinkt goed|goed idee|mee eens|zeker)[.!]?$|" +
            "(?<![\\p{L}])(?:kan ik (?:wel )?doen|doe ik|dat doen we|dat lijkt me|lijkt me (?:wel )?(?:goed|haalbaar|prima|oké|een goed idee)|klinkt goed|" +
            "(?:mij|ik) ook|herken ik|mee eens|goed idee|dat was (?:fijn|leuk|tof)|sounds good|i can do that|me too|agreed|good idea)(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )
    /** An option: offered, not decided. */
    private val TENTATIVE = Regex(
        "^(?:of\\s+)?(?:misschien|zouden we|zou je|zou jij|kunnen we|kan jij|kun jij|kan je|kun je|zullen we|wat als|wat denk je van|wat vind je van|stel dat|" +
            "maybe|what if|how about|could we|could you|can you|shall we|should we)(?![\\p{L}])|(?<![\\p{L}])(?:of misschien|of anders)(?![\\p{L}])|" +
            // "… naar de weekplanning misschien": a doubt at the end takes the plan back to an option.
            "(?<![\\p{L}])(?:misschien|denk ik|of zo|of niet|maybe|i guess|or not)\\s*[.?!]*$",
        RegexOption.IGNORE_CASE,
    )
    /** The words before the option itself. */
    private val OPTION_LEAD = Regex(
        "^(?:of|misschien|anders|dat|stel dat|wat als|wat denk je van|wat vind je van|zouden we|zou je|zou jij|kunnen we|kan jij|kun jij|kan je|kun je|zullen we|" +
            "niet|gewoon|eens|maybe|what if|how about|could we|could you|can you|shall we|should we)(?![\\p{L}])[,]?\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val DOUBT = Regex(
        "^(?:maar|but)\\s+(?:dan|als|wat als|then|what if|if)(?![\\p{L}])|(?<![\\p{L}])(?:ik weet niet of|ik weet het niet|weet ik niet|ik weet niet|ik twijfel|niet zeker|lukt dat wel|gaat dat lukken|dat gaat niet|liever niet|" +
            "i'm not sure|not sure|i doubt|rather not)(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )
    private val QUESTION = Regex(
        // A question word straight before its subject is a clause, not a question: "waar ik vond dat …".
        "^(?:(?:hoe|wanneer|wie|waar|hoeveel|waarom)(?!\\s+(?:ik|we|wij|jij|je|ze|zij|hij|het|de|dat)(?![\\p{L}]))|welke|wat (?:staat|doen|is er|hebben|moeten|gaan|eten|vind jij|denk jij|zeg jij)|heb je|hebben we|moeten we nog|" +
            "how|when|who|where|which|why|what (?:is|do|are|did))(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )
    /** About the meeting itself: "vandaag gaan we kijken naar de weekplanning". Talk, never a plan. */
    private val META = Regex(
        "(?<![\\p{L}])(?:weekplanning|week planning|weekoverleg|week overleg|het overleg|dit overleg|de agenda|vergadering|meeting|the planning|this week's plan)(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )
    private val META_VERB = Regex(
        "(?<![\\p{L}])(?:kijken|bekijken|bespreken|overlopen|doornemen|beginnen|starten|look|go through|start|discuss)(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )
    /** About the week that was: past tense ("de was" is the laundry). */
    private val PAST = Regex(
        "(?<![\\p{L}])(?<!de )(?:was|waren|vond|vonden|had|hadden|deed|deden|ging|gingen|kwam|kwamen|liep|liepen|lukte|gelukt|gedaan|gegeten|geweest|opgevolgd|" +
            "vergeten|gisteren|vorige week|was|were|did|went|had|forgot|yesterday|last week)(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )
    /** Looking at ourselves: "ik denk dat ik het uitstel", "had ik moeten". Said, not written down. */
    private val REFLECT = Regex(
        "(?<![\\p{L}])(?:ik denk dat ik|ik stel|stel ik|ik vergeet|mijn fout|had ik moeten|heb ik niet|ik heb .* niet|i think i|my fault|i should have)(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )
    private val SELF = Regex("(?<![\\p{L}])(?:ik|mijn|i|my)(?![\\p{L}])", RegexOption.IGNORE_CASE)
    private val NOT_GOOD = Regex("(?<![\\p{L}])(?:niet|nie|geen|not|never)\\s+(?:\\p{L}+\\s+)?(?:goed|fijn|leuk|well|good)", RegexOption.IGNORE_CASE)
    /** "… vind is dat", "fijn dat", "blij dat": what follows is what it's about. */
    private val ABOUT_THAT = Regex(
        "^.*?(?:goed|fijn|leuk|blij|trots|jammer|vervelend|stoort|ergert|vind|vond|is|was|glad|happy|annoying|bothers me)\\s+(?:het\\s+)?(?:me\\s+|mij\\s+)?(?:dat|that)\\s+",
        RegexOption.IGNORE_CASE,
    )
    private val PLAN_TALK = anyOf(listOf("planning", "volgende week", "deze week", "next week", "this week", "plan"))
    private val RHYTHM_WORDS = anyOf(listOf("elke", "iedere", "om de", "every", "each"))
    private val PRONOUN_WORDS = setOf("dat", "die", "het", "dit", "er", "that", "it")
    private val DAY_WORD = anyOf(
        listOf(
            "maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag", "vandaag", "vanavond", "morgen", "overmorgen",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "today", "tonight", "tomorrow",
        ),
    )
    /** "dus dan elke avond …": narrows the option on the table. */
    private val REFINE = Regex("^(?:dus|dan|zo|so|then)(?![\\p{L}])", RegexOption.IGNORE_CASE)
    private val THEN = Regex("(?<![\\p{L}])(?:dan|then)(?![\\p{L}])", RegexOption.IGNORE_CASE)
    private val WIN = anyOf(
        listOf(
            "ging goed", "gingen goed", "liep goed", "goed ging", "goed gingen", "goed liep", "goed gelukt", "fijn dat", "goed dat", "was fijn", "was leuk", "deugd", "blij dat", "trots", "goed gedaan", "is gelukt", "zijn gelukt", "lukte", "top dat", "leuk dat",
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
            "erger me", "ergert", "stoort", "niet leuk", "niet zo leuk", "niet fijn", "jammer dat", "vervelend", "frustrerend", "frustreert", "frustratie", "gefrustreerd", "irritant", "ergerlijk", "lastig dat", "altijd weer",
            "nooit", "ambetant", "moeilijk ging", "ging moeilijk", "minder goed", "blijft altijd", "blijft liggen", "blijven liggen", "beu", "op mijn zenuwen", "keel uit", "zot van", "kwaad", "frustrating", "frustrated", "annoying", "annoys", "annoyed",
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
    /** "de was doen", "strijken", "stofzuigen": household work, not something to buy (unless it says buy). */
    fun choreTalk(text: String): Boolean = CHORE_TALK.containsMatchIn(text) && !SHOPPING.containsMatchIn(text)

    private val CHORE_TALK = anyOf(
        listOf(
            "de was", "was doen", "wassen", "strijken", "plooien", "opruimen", "opkuisen", "poetsen", "stofzuigen", "dweilen", "afwas",
            "vaatwasser", "koken", "afruimen", "bed opmaken", "laundry", "ironing", "tidy", "clean", "vacuum", "dishes",
        ),
    )
    private val SHOPPING = anyOf(listOf("kopen", "halen", "op de lijst", "boodschappen", "we need to buy", "buy", "pick up", "groceries"))
}
