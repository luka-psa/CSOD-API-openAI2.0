package app.hearth.ai.meeting

import app.hearth.ai.Orchestrator
import app.hearth.ai.interpret.Action
import app.hearth.ai.interpret.MeetingReader
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
 * 3. per topic, the interpreter reads its lines as a whole (in windows that fit the model) when it is on; otherwise the
 *    rules' conclusions count ([Orchestrator.listen]);
 * 4. conclusions that rest on words nobody said, or on too few words to mean something, are dropped;
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
            val windows = parts.flatMap { part -> windows(part.lines).map { part to it } }
            progress(0, windows.size)
            windows.forEachIndexed { n, (_, window) ->
                interpret(window.map { text[it] }).forEach { action ->
                    val line = home(action, window, text)
                    raw!! += line to action
                    orchestrator.proposalOf(action, text[line], ctx)?.let { found += Finding(it, listOf(line), byInterpreter = true) }
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

    /** A topic's lines in windows the interpreter can read at once, overlapping a little so nothing falls between two. */
    private fun windows(lines: List<Int>): List<List<Int>> {
        if (lines.size <= WINDOW) return listOf(lines)
        val out = mutableListOf<List<Int>>()
        var start = 0
        while (start < lines.size) {
            out += lines.subList(start, minOf(lines.size, start + WINDOW))
            if (start + WINDOW >= lines.size) break
            start += WINDOW - OVERLAP
        }
        return out
    }

    /** The line an action came from: the one that shares most of its words (the latest on a tie). */
    private fun home(a: Action, window: List<Int>, text: List<String>): Int {
        val said = a.args.values.flatMap(MeetingReader::words).toSet()
        return window.maxWith(compareBy<Int> { i -> MeetingReader.words(text[i]).count { w -> said.any { MeetingReader.sameWord(it, w) } } }.thenBy { it })
    }

    /**
     * Whether a conclusion says something: enough words of its own (a thing to buy, a dish, a day off is enough; a
     * task, a rule or a goal needs two), not a whole sentence, words that were said and not made up ("Guisen" from "wil
     * dat jij"), and a place that fits what the talk was about ([topics] of its lines): a meal needs talk about food, a
     * goal is not a remark about the kitchen, what went well is said with a word for it.
     */
    private fun meaningful(p: Orchestrator.Proposal, said: String, topics: Set<Topic>, people: List<String>): Boolean {
        if (p.place == Place.NONE || p.calls.isEmpty()) return false
        val words = Matcher.contentWords(p.text)
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
        /** Lines per interpreter reading: a topic's stretch of talk, within the model's window. */
        const val WINDOW = 10
        const val OVERLAP = 2
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
