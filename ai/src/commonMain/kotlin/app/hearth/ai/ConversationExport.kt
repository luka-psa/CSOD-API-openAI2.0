package app.hearth.ai

import app.hearth.ai.interpret.Action
import app.hearth.ai.sense.Reading
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A weekoverleg's conversation with everything Hearth made of it, as one JSON file, to check how well Luister mee
 * understood a real meeting: every line as heard (with its time and the sentence model's reading), every thought
 * with its stage and the rules' conclusions, what the interpreter read per line (its raw actions and the suggestions
 * they became), and what the family changed or added. It never leaves the tablet unless someone shares the file.
 */
object ConversationExport {
    const val FORMAT = "hearth-conversation/1"

    /** One line of the transcript, as kept. */
    data class Line(val id: String, val text: String, val at: Long, val sorted: Boolean)

    /** A suggestion as the family left it on the stage: changed in its sheet and/or added. */
    data class Shown(val proposal: Orchestrator.Proposal, val added: Boolean)

    fun json(
        lines: List<Line>,
        /** The rules' (and, when on, the sentence model's) thoughts over [lines]; their ranges index [lines]. */
        thoughts: List<Orchestrator.Talk>,
        /** The interpreter's raw actions, by index in [lines] (null: it was not on, or has not read these lines). */
        interpreterActions: List<Pair<Int, Action>>?,
        /** What those actions became, by line id. */
        interpreterProposals: Map<String, List<Orchestrator.Proposal>>?,
        /** The sentence model, when smarter listening is ready (null: the rules alone). */
        sense: ((String) -> Reading?)?,
        /** What the family changed or added on the stage, by the id of a thought's first line. */
        family: Map<String, List<Shown>>,
        /** Where it was exported: language, day, which models were on. */
        context: Map<String, String>,
        exportedAt: String,
    ): String {
        val doc = buildJsonObject {
            put("format", FORMAT)
            put("exportedAt", exportedAt)
            putJsonObject("context") { context.forEach { (k, v) -> put(k, v) } }
            putJsonArray("lines") {
                lines.forEachIndexed { i, line ->
                    addJsonObject {
                        put("index", i)
                        put("id", line.id)
                        put("at", line.at)
                        put("sorted", line.sorted)
                        put("text", line.text)
                        sense?.invoke(line.text)?.let { r -> putJsonObject("sense") { put("sense", r.sense.name); put("confidence", r.confidence.toDouble()) } }
                        interpreterActions?.filter { it.first == i }?.takeIf { it.isNotEmpty() }?.let { acts ->
                            putJsonArray("interpreterActions") {
                                acts.forEach { (_, a) -> addJsonObject { put("name", a.name); putJsonObject("args") { a.args.forEach { (k, v) -> put(k, v) } } } }
                            }
                        }
                    }
                }
            }
            putJsonArray("thoughts") {
                thoughts.forEach { t ->
                    val mine = lines.slice(t.lines)
                    addJsonObject {
                        put("lines", "${t.lines.first}..${t.lines.last}")
                        put("heard", t.heard)
                        put("stage", t.stage.name)
                        putJsonArray("rules") { t.proposals.forEach { addJsonObject { proposal(it) } } }
                        if (interpreterProposals != null) {
                            putJsonArray("interpreter") { mine.flatMap { interpreterProposals[it.id].orEmpty() }.forEach { addJsonObject { proposal(it) } } }
                        }
                        family[mine.firstOrNull()?.id]?.let { shown ->
                            putJsonArray("family") { shown.forEach { s -> addJsonObject { proposal(s.proposal); put("added", s.added) } } }
                        }
                    }
                }
            }
        }
        return PRETTY.encodeToString(JsonObject.serializer(), doc)
    }

    private fun JsonObjectBuilder.proposal(p: Orchestrator.Proposal) {
        put("text", p.text)
        put("place", p.place.name)
        p.topic?.let { put("topic", it.name) }
        p.date?.let { put("date", it.toString()) }
        if (p.next) put("next", true)
        put("sentence", p.sentence)
        if (p.heard != p.sentence) put("heard", p.heard)
        putJsonArray("calls") { p.calls.forEach { c -> addJsonObject { put("name", c.name); put("arguments", c.arguments) } } }
    }

    private val PRETTY = Json { prettyPrint = true }
}

