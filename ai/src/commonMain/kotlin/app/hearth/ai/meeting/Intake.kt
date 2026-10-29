package app.hearth.ai.meeting

import app.hearth.ai.interpret.MeetingReader
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.Meal
import app.hearth.core.model.Topic
import app.hearth.core.model.WordFix
import app.hearth.core.model.WordFixes
import app.hearth.core.model.topic

/**
 * What the household already has, as Luister mee needs it: to recognise what the talk is about while it goes on, and
 * afterwards to change what exists instead of adding it twice. [words] are the household's own words ([WordFixes]).
 */
data class Household(
    val people: List<String> = emptyList(),
    /** Open tasks and routines (steps too: a routine's steps are named in talk). */
    val backlog: List<BacklogItem> = emptyList(),
    /** House rules, frictions, ideas and goals. */
    val board: List<BoardItem> = emptyList(),
    /** What is on the shopping list and not bought yet. */
    val shopping: List<String> = emptyList(),
    /** The meals planned from today. */
    val meals: List<Meal> = emptyList(),
    /** The dishes we know. */
    val dishes: List<String> = emptyList(),
    val words: List<WordFix> = emptyList(),
)

/** Something that exists already and can be named in talk: a routine, a task, a house rule, a goal, a dish. */
data class Known(val id: String, val text: String, val topic: Topic, val words: List<String>)

/** A stretch of a line Hearth recognises: a person, something the household has, or a word that belongs to a topic. */
data class Mark(val start: Int, val end: Int, val topic: Topic?, val known: Known? = null, val person: Boolean = false)

/** One line as Hearth reads it: with the household's words put right, what it recognises in it, and its topics (most first). */
data class LineIntake(val text: String, val marks: List<Mark>, val topics: List<Topic>) {
    val topic: Topic? get() = topics.firstOrNull()
}

/**
 * The first intake while the meeting goes on: no conclusions yet, only what each line is about. Names, things the
 * household already has (the evening routine, the house rule about shoes, the shopping list) and the words that belong
 * to a topic ("vaatwasser" is Huishouden, "gerechten" Weekmenu, "squash" Vrije tijd) are marked in their topic's colour,
 * and the topics light up. The full reading comes after the meeting ([MeetingProcessor]).
 */
class MeetingIntake(household: Household) {
    private val words = household.words
    private val people = household.people.map { it.trim() }.filter { it.length >= 2 }
    val known: List<Known> = knownOf(household)
    private val byWord: Map<String, List<Known>> = known.flatMap { k -> k.words.map { it to k } }.groupBy({ it.first }, { it.second })

    fun read(line: String): LineIntake {
        val text = WordFixes.apply(line, words)
        val marks = mutableListOf<Mark>()
        fun free(s: Int, e: Int) = marks.none { it.start < e && s < it.end }
        WORD.findAll(text).forEach { m ->
            val w = m.value
            if (people.any { it.equals(w, ignoreCase = true) }) { marks += Mark(m.range.first, m.range.last + 1, null, person = true); return@forEach }
            val key = w.lowercase()
            if (key.length < 4 || key in MeetingReader.STOP_WORDS) return@forEach
            // The thing the household has that this word names best (the most specific: the fewest words).
            val hit = byWord[key] ?: byWord.entries.firstOrNull { (k, _) -> MeetingReader.sameWord(k, key) }?.value
            hit?.minByOrNull { it.words.size }?.let { k -> marks += Mark(m.range.first, m.range.last + 1, k.topic, k) }
        }
        TOPIC_WORDS.forEach { (topic, regex) ->
            regex.findAll(text).forEach { m -> if (free(m.range.first, m.range.last + 1)) marks += Mark(m.range.first, m.range.last + 1, topic) }
        }
        val votes = mutableMapOf<Topic, Int>()
        marks.forEach { mk -> mk.topic?.let { votes[it] = (votes[it] ?: 0) + if (mk.known != null) 2 else 1 } }
        val topics = votes.entries.sortedWith(compareByDescending<Map.Entry<Topic, Int>> { it.value }.thenBy { it.key.ordinal }).map { it.key }
        return LineIntake(text, marks.sortedBy { it.start }, topics)
    }

    companion object {
        private val WORD = Regex("[\\p{L}\\p{N}'’]+")

        /** Everything the household has that talk can name, with the words that name it. */
        fun knownOf(h: Household): List<Known> {
            fun known(id: String, text: String, topic: Topic?) =
                topic?.let { t -> MeetingReader.words(text).distinct().takeIf { it.isNotEmpty() }?.let { Known(id, text, t, it) } }
            return h.backlog.filter { !it.done }.mapNotNull { known(it.id, it.text, it.topic ?: if (it.isStep) Topic.RHYTHM else null) } +
                h.board.filter { it.isOpen }.mapNotNull { b ->
                    known(b.id, b.text, when (b.kind) {
                        BoardKind.RULE -> Topic.RULES
                        BoardKind.FRICTION, BoardKind.IDEA -> Topic.FRICTION
                        BoardKind.GOAL -> Topic.GOALS
                    })
                } +
                h.shopping.mapNotNull { known("shopping:$it", it, Topic.SHOPPING) } +
                (h.meals.map { it.dish } + h.dishes).distinct().mapNotNull { known("dish:$it", it, Topic.MENU) }
        }

        private fun words(vararg w: String) = Regex(
            "(?<![\\p{L}])(?:" + w.joinToString("|") { p ->
                // "vaatwas*": the word and its compounds and forms; "vrije tijd": the words as said.
                if (p.endsWith("*")) Regex.escape(p.dropLast(1)) + "\\p{L}*" else p.split(" ").joinToString("\\s+") { Regex.escape(it) }
            } + ")(?![\\p{L}])",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Words that say which topic the talk is on, in Flemish and English. Only words that rarely mean something else:
         * "was" is also "it was", so it is "de was", "wasmand", "wasmachine".
         */
        val TOPIC_WORDS: List<Pair<Topic, Regex>> = listOf(
            Topic.ADMIN to words(
                "admin*", "administrati*", "bellen", "telefoontje*", "papieren", "belasting*", "factuur", "facturen", "rekening*", "verzekering*",
                "formulier*", "gemeente*", "afspraak", "afspraken", "tandarts*", "dokter*", "huisarts*", "kapper*", "bank", "mails", "e-mail*",
                "appointment*", "dentist", "doctor", "insurance", "taxes", "paperwork", "bills",
            ),
            Topic.PROJECTS to words(
                "herorden*", "herorganis*", "grote taak", "grote taken", "verbouw*", "renov*", "schilder*", "zolder*", "inricht*",
                "opbergruimte*", "project*", "big task*", "renovat*", "paint*", "attic",
            ),
            Topic.HOME to words(
                "huishoud*", "vaatwas*", "afwas*", "de was", "wasmand*", "wasmachine*", "wasgoed", "wassen", "droogkast*", "droogrek*", "strijk*",
                "plooien", "opruim*", "opgeruimd", "poets*", "kuisen", "opkuis*", "stofzuig*", "dweil*", "keuken*", "badkamer*", "handdoek*",
                "bed opgedekt", "bedden", "vuilbak*", "vuilzak*", "rommel*", "laundry", "dishes", "dishwasher", "kitchen", "bathroom", "tidy*",
                "vacuum*", "ironing", "chores",
            ),
            Topic.FREE_TIME to words(
                "vrije tijd", "squash*", "sport*", "tennis*", "fitness*", "zwemmen", "wandel*", "film*", "uitstap*", "uitje*", "hobby*",
                "muziek*", "gitaar*", "piano*", "ontspan*", "date", "free time", "relax*", "music", "movie*", "hobbies",
            ),
            Topic.MENU to words(
                "avondeten", "gerecht*", "koken", "kook*", "recept*", "maaltijd*", "weekmenu*", "menu", "ontbijt*", "middageten", "prep", "meal*",
                "dinner", "cook*", "recipe*",
            ),
            Topic.SHOPPING to words(
                "boodschap*", "winkel*", "supermarkt*", "colruyt", "delhaize", "aldi", "lidl", "ingrediënt*", "ingredient*", "groceries", "shopping",
            ),
            Topic.RHYTHM to words(
                "routine*", "gewoonte*", "ritme*", "ochtendritme*", "avondritme*", "elke dag", "elke avond", "elke ochtend", "dagelijks", "wekelijks",
                "slapengaan", "stretch*", "habit*", "every day", "every evening", "every morning", "daily", "weekly", "bedtime",
            ),
            Topic.RULES to words("huisregel*", "afgesproken", "voortaan", "vanaf nu", "we spreken af", "house rule*", "from now on", "we agreed"),
            Topic.FRICTION to words(
                "frustr*", "erger*", "stoort", "irritant*", "vervelend*", "lastig", "beu", "vicieuze cirkel", "mentale last", "mental load",
                "annoy*", "bother*",
            ),
            Topic.GOALS to words("doel*", "bewegen", "yoga", "pilates", "gezonder", "comfortzone", "goal*", "exercise", "healthier"),
        )
    }
}

/** A topic as it came up so far: in how many lines, which of the household's things were named, and its words. */
data class TopicSeen(val topic: Topic, val lines: Int, val known: List<Known>, val words: List<String>)

/** What the meeting has been about so far, in the order of every screen ("Waar het over gaat"). */
fun seen(lines: List<LineIntake>): List<TopicSeen> =
    Topic.entries.mapNotNull { topic ->
        val mine = lines.filter { topic in it.topics }
        if (mine.isEmpty()) return@mapNotNull null
        val marks = mine.flatMap { l -> l.marks.filter { it.topic == topic }.map { l to it } }
        TopicSeen(
            topic,
            mine.size,
            marks.mapNotNull { it.second.known }.distinctBy { it.id },
            marks.filter { it.second.known == null }.map { (l, m) -> l.text.substring(m.start, m.end).lowercase() }.distinct().take(6),
        )
    }
