package app.hearth.ai.meeting

import app.hearth.ai.Orchestrator
import app.hearth.ai.interpret.Action
import app.hearth.ai.interpret.MeetingReader
import app.hearth.ai.route.MeetingWords
import app.hearth.ai.route.Place
import app.hearth.ai.sense.Reading
import app.hearth.core.model.Topic
import app.hearth.core.tools.ToolContext

/** A line of the conversation as kept: its id, what was heard, and when (epoch millis). */
data class HeardLine(val id: String, val text: String, val at: Long)

/** One conclusion of the whole conversation and the lines it rests on (indexes into the conversation). */
data class Finding(val proposal: Orchestrator.Proposal, val lines: List<Int>, val byInterpreter: Boolean)

/** A topic as the conversation went: every line about it, also when it came back later. [topic] null: no topic heard. */
data class TopicPart(val topic: Topic?, val lines: List<Int>)

/**
 * The whole conversation read after the meeting: each line with the household's words put right and what it is
 * about ([intake]), the rules' thoughts over it ([talk]), the topics as they went ([parts]), what was concluded
 * ([found]), and the interpreter's raw actions by line (null when it was not used).
 */
data class Processed(
    val intake: List<LineIntake>,
    val talk: List<Orchestrator.Talk>,
    val parts: List<TopicPart>,
    val found: List<Finding>,
    val raw: List<Pair<Int, Action>>?,
) {
    /** Thoughts that concluded nothing, joined when they follow each other: just talk, kept and shown once. */
    val quiet: List<Orchestrator.Talk>
        get() {
            val used = found.flatMap { it.lines }.toSet()
            return talk.filter { t -> t.lines.none { it in used } }.fold(mutableListOf()) { out, t ->
                val prev = out.lastOrNull()
                if (prev != null && prev.lines.last + 1 == t.lines.first) out[out.lastIndex] = prev.copy(heard = prev.heard + " " + t.heard, lines = prev.lines.first..t.lines.last)
                else out += t
                out
            }
        }
}

/**
 * Reads a whole weekoverleg at once, after it ended, on the tablet (no network, no cloud model):
 * 1. every line with the household's own words put right ([Household.words]);
 * 2. the topic of every thought ([MeetingIntake]), so a topic that comes back later is read together with what was said
 *    about it first, and talk without a topic stays with the thought before it;
 * 3. per topic, the interpreter reads its lines as a whole when it is on: sentence by sentence, the way it learned, in
 *    windows of as many words as the model takes, so none of a long stretch falls off; otherwise the rules' conclusions
 *    count ([Orchestrator.listen]);
 * 4. conclusions that rest on words nobody said, on too few words to mean something, or (the interpreter's) on talk
 *    that doesn't say so ([MeetingWords.backs]: an idea offered, a friction that rubs, a plan going somewhere) are
 *    dropped;
 * 5. each conclusion is held against what the household has ([Matcher]): a change to that item, or "there already";
 * 6. the same conclusion said twice counts once, the last time it was said (a change of plan replaces what came before).
 * Nothing is written: the result becomes the Voorstel the family checks.
 */
class MeetingProcessor(private val orchestrator: Orchestrator) {
    suspend fun process(
        lines: List<HeardLine>,
        household: Household,
        ctx: ToolContext,
        sense: ((String) -> Reading?)? = null,
        /** The interpreter, one reading of a few lines (oldest first); null: the rules alone. */
        interpret: (suspend (List<String>) -> List<Action>)? = null,
        /** How far it is: readings done of all readings. */
        progress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Processed {
        val intake = MeetingIntake(household).let { reader -> lines.map { reader.read(it.text) } }
        val text = intake.map { it.text }
        val talk = orchestrator.listen(text, ctx, household.people, lines.map { it.at }, sense)
        val parts = parts(talk, intake)
        val raw = if (interpret != null) mutableListOf<Pair<Int, Action>>() else null
        val found = mutableListOf<Finding>()
        if (interpret == null) {
            progress(0, 1)
            talk.forEach { t -> t.proposals.forEach { p -> found += Finding(p, t.lines.toList(), byInterpreter = false) } }
            progress(1, 1)
        } else {
            val said = text.flatMapIndexed { i, t -> sentences(t).map { Said(i, it) } }
            val windows = parts.flatMap { part -> part.lines.toSet().let { lines -> windows(said.filter { it.line in lines }) } }
            progress(0, windows.size)
            windows.forEachIndexed { n, window ->
                interpret(window.map { it.text }).forEach { action ->
                    val words = action.args.values.flatMap(MeetingReader::words)
                    val near = window.filter { s -> MeetingReader.words(s.text).any { w -> words.any { MeetingReader.sameWord(it, w) } } }
                    // The sentence it came from: the one sharing most of its words (the latest on a tie).
                    val home = near.asReversed().maxByOrNull { s -> MeetingReader.words(s.text).count { w -> words.any { MeetingReader.sameWord(it, w) } } } ?: window.last()
                    raw!! += home.line to action
                    val p = orchestrator.proposalOf(action, home.text, ctx) ?: return@forEach
                    // The model writes its own words: only what the talk itself says counts.
                    if (near.any { MeetingWords.backs(p.place, it.text, ctx.today) }) found += Finding(p, listOf(home.line), byInterpreter = true)
                }
                progress(n + 1, windows.size)
            }
        }
        val kept = found.filter { f -> meaningful(f.proposal, f.lines.joinToString(" ") { text[it] }, f.lines.flatMap { intake[it].topics }.toSet(), household.people) }
            .map { f -> f.copy(proposal = Matcher.match(f.proposal, household)) }
        return Processed(intake, talk, parts, once(kept).sortedWith(compareBy({ it.proposal.topic?.ordinal ?: Topic.entries.size }, { it.lines.min() })), raw)
    }

    /**
     * The topics as the conversation went. Each thought gets the topic its lines and conclusions name most; a thought
     * without one stays with the topic before it (it goes on about that), the first ones with the topic after them.
     */
    private fun parts(talk: List<Orchestrator.Talk>, intake: List<LineIntake>): List<TopicPart> {
        val named = talk.map { t ->
            val votes = mutableMapOf<Topic, Int>()
            t.lines.forEach { i -> intake[i].topics.forEachIndexed { n, topic -> votes[topic] = (votes[topic] ?: 0) + if (n == 0) 2 else 1 } }
            t.proposals.forEach { p -> p.topic?.let { votes[it] = (votes[it] ?: 0) + 2 } }
            votes.maxWithOrNull(compareBy<Map.Entry<Topic, Int>> { it.value }.thenByDescending { it.key.ordinal })?.key
        }
        val filled = named.toMutableList()
        for (i in filled.indices) if (filled[i] == null && i > 0) filled[i] = filled[i - 1]
        val first = filled.firstOrNull { it != null }
        for (i in filled.indices) if (filled[i] == null) filled[i] = first
        return talk.indices.groupBy { filled[it] }.map { (topic, ts) -> TopicPart(topic, ts.flatMap { talk[it].lines }.sorted()) }
            .sortedBy { it.lines.first() }
    }

    /** A sentence of the conversation and the line it was said in. */
    private class Said(val line: Int, val text: String) {
        val words = count(text)
    }

    /**
     * A line as the sentences it holds (Whisper writes up to half a minute of talk as one line; the interpreter learned
     * on sentences). A long run-on sentence is cut at its commas, at most [LONG] words a piece.
     */
    private fun sentences(line: String): List<String> = line.split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }.flatMap { s ->
        if (count(s) <= LONG) return@flatMap listOf(s)
        val out = mutableListOf<String>()
        var now = ""
        s.split(AFTER_COMMA).flatMap { p -> p.split(SPACE).chunked(LONG).map { it.joinToString(" ") } }.forEach { p ->
            if (now.isNotEmpty() && count(now) + count(p) > LONG) {
                out += now
                now = p
            } else now = if (now.isEmpty()) p else "$now $p"
        }
        if (now.isNotEmpty()) out += now
        out
    }

    /**
     * A topic's sentences in windows the interpreter reads at once: as many as fit [WINDOW_WORDS] (the model's window,
     * so it never drops the first ones), overlapping a sentence or two so a question and its answer are read together.
     */
    private fun windows(said: List<Said>): List<List<Said>> {
        val out = mutableListOf<List<Said>>()
        var start = 0
        while (start < said.size) {
            var end = start
            var words = 0
            while (end < said.size && end - start < WINDOW && (end == start || words + said[end].words <= WINDOW_WORDS)) words += said[end++].words
            out += said.subList(start, end)
            if (end >= said.size) break
            var back = end
            var kept = 0
            while (back - 1 > start && end - (back - 1) <= OVERLAP && kept + said[back - 1].words <= OVERLAP_WORDS) kept += said[--back].words
            start = back
        }
        return out
    }

    /**
     * Whether a conclusion says something: enough words of its own (a thing to buy, a dish, a day off is enough; a
     * task, a rule or a goal needs two), not a whole sentence, words that were said and not made up ("Guisen" from "wil
     * dat jij"), and a place that fits what the talk was about ([topics] of its lines): a meal needs talk about food, a
     * goal is not a remark about the kitchen, what went well is said with a word for it.
     */
    private fun meaningful(p: Orchestrator.Proposal, said: String, topics: Set<Topic>, people: List<String>): Boolean {
        if (p.place == Place.NONE || p.calls.isEmpty()) return false
        // "Een belangrijke punten": words that say nothing about what it is don't count.
        val words = Matcher.contentWords(p.text).filter { it !in VAGUE }
        if (words.size < (if (p.place in SHORT_OK) 1 else 2) || words.size > (if (p.place in OWN_WORDS) 12 else 8)) return false
        val heard = Matcher.contentWords(said) + people.map { it.lowercase() }
        val made = words.count { w -> heard.none { Matcher.sameRoot(it, w) } }
        if (made > (if (words.size >= 4) 1 else 0)) return false
        if (p.place == Place.SHOPPING && p.text.split(',').any { Matcher.contentWords(it).size > 3 }) return false
        if (p.place == Place.WIN && !GOOD.containsMatchIn(said)) return false
        // "ik denk …", "of zo", "misschien": still thinking out loud, not something to put somewhere.
        if (p.place !in OWN_WORDS && p.place != Place.WIN && TALKY.containsMatchIn(p.text)) return false
        val fits = FITS[p.place]
        return fits == null || topics.isEmpty() || topics.any { it in fits }
    }

    /** The same conclusion twice (the same place and words, or the same item it changes) counts once: the last time. */
    private fun once(found: List<Finding>): List<Finding> {
        val out = mutableListOf<Finding>()
        found.sortedBy { it.lines.max() }.forEach { f ->
            val words = Matcher.contentWords(f.proposal.text)
            val twin = out.indexOfFirst { o ->
                o.proposal.place == f.proposal.place && (
                    (o.proposal.match != null && o.proposal.match.id == f.proposal.match?.id) ||
                        Matcher.overlap(words, Matcher.contentWords(o.proposal.text)) >= 0.8f && Matcher.overlap(Matcher.contentWords(o.proposal.text), words) >= 0.8f
                    )
            }
            if (twin < 0) out += f
            else out[twin] = f.copy(lines = (out[twin].lines + f.lines).distinct().sorted())
        }
        return out
    }

    private companion object {
        /** Sentences per interpreter reading (as in training), and the words they may hold: within the model's window. */
        const val WINDOW = 16
        const val WINDOW_WORDS = 130
        /** Sentences read again at the start of the next window, if they are short. */
        const val OVERLAP = 2
        const val OVERLAP_WORDS = 30
        /** The longest piece of a run-on sentence. */
        const val LONG = 35
        val SENTENCE_END = Regex("(?<=[.!?])\\s+")
        val AFTER_COMMA = Regex("(?<=[,;])\\s+")
        val SPACE = Regex("\\s+")
        fun count(text: String) = text.split(SPACE).count { it.isNotEmpty() }
        /** Words that don't say what something is about. */
        val VAGUE = setOf(
            "punt", "punten", "puntje", "puntjes", "ding", "dingen", "dingetje", "dingetjes", "zaak", "zaken", "iets", "kwestie", "onderwerp",
            "onderwerpen", "belangrijk", "belangrijke", "soort", "manier", "beetje", "overleg", "gesprek", "agenda", "vergadering",
            "thing", "things", "stuff", "point", "points", "important", "something", "topic", "kind",
        )
        val SHORT_OK = setOf(Place.SHOPPING, Place.MEAL, Place.FREE_TIME, Place.EVENT)
        /** Said in the family's own words, so a little longer. */
        val OWN_WORDS = setOf(Place.FRICTION, Place.IDEA, Place.HOUSE_RULE)
        /** The topics a place can come from; the rest can come from any talk. */
        val FITS = mapOf(
            Place.MEAL to setOf(Topic.MENU, Topic.SHOPPING),
            Place.SHOPPING to setOf(Topic.SHOPPING, Topic.MENU),
            Place.GOAL to setOf(Topic.GOALS, Topic.FREE_TIME, Topic.RHYTHM),
            Place.FREE_TIME to setOf(Topic.FREE_TIME, Topic.GOALS),
        )
        val TALKY = Regex(
            "(?<![\\p{L}])(?:ik denk|denk ik|ik vind|vind ik|weet ik|weet niet|of zo|ofzo|enzo|misschien|ja|nee|oké|okee|allee|sorry|dinges|" +
                "i think|i guess|or something|maybe|yeah|okay)(?![\\p{L}])",
            RegexOption.IGNORE_CASE,
        )
        /** What went well is said with a word for it. */
        val GOOD = Regex(
            "(?<![\\p{L}])(?:goed|fijn|leuk|tof|blij|trots|gelukt|lukte|top|deugd|nice|super|geweldig|plezant|well|great|good|glad|proud)(?![\\p{L}])",
            RegexOption.IGNORE_CASE,
        )
    }
}
