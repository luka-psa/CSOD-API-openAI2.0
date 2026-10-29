package app.hearth.ai

import app.hearth.ai.meeting.Processed
import app.hearth.ai.sense.Reading
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A weekoverleg's conversation with everything Hearth made of it, as one JSON file, to check how well Luister mee
 * understood a real meeting and to make new examples to teach it: every line as heard and as read (the household's own
 * words put right, the words it recognised, its topics, the sentence model's reading, the interpreter's raw actions),
 * the rules' thoughts, the topics as the conversation went, what the full reading concluded, and the Voorstel as the
 * family left it (what they changed, ticked or added). It never leaves the tablet unless someone shares the file.
 */
object ConversationExport {
    const val FORMAT = "hearth-conversation/2"

    /** One line of the transcript, as kept. */
    data class Line(val id: String, val text: String, val at: Long, val sorted: Boolean)

    /** A row of the Voorstel as the family left it, with the lines (indexes) it rests on. */
    data class Row(val proposal: Orchestrator.Proposal, val lines: List<Int>, val checked: Boolean, val changed: Boolean, val added: Boolean)

    fun json(
        lines: List<Line>,
        /** The full reading of [lines]. */
        processed: Processed,
        /** The Voorstel, when it was open for these lines. */
        voorstel: List<Row>?,
        /** The sentence model, when smarter listening is ready (null: the rules alone). */
        sense: ((String) -> Reading?)?,
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
                    val read = processed.intake.getOrNull(i)
                    addJsonObject {
                        put("index", i)
                        put("id", line.id)
                        put("at", line.at)
                        put("sorted", line.sorted)
                        put("text", line.text)
                        if (read != null && read.text != line.text) put("read", read.text)
                        if (read != null && read.topics.isNotEmpty()) putJsonArray("topics") { read.topics.forEach { add(it.name) } }
                        if (read != null && read.marks.isNotEmpty()) putJsonArray("marks") {
                            read.marks.forEach { m ->
                                addJsonObject {
                                    put("words", read.text.substring(m.start, m.end))
                                    m.topic?.let { put("topic", it.name) }
                                    m.known?.let { put("known", it.text) }
                                    if (m.person) put("person", true)
                                }
                            }
                        }
                        sense?.invoke(read?.text ?: line.text)?.let { r -> putJsonObject("sense") { put("sense", r.sense.name); put("confidence", r.confidence.toDouble()) } }
                        processed.raw?.filter { it.first == i }?.takeIf { it.isNotEmpty() }?.let { acts ->
                            putJsonArray("interpreterActions") {
                                acts.forEach { (_, a) -> addJsonObject { put("name", a.name); putJsonObject("args") { a.args.forEach { (k, v) -> put(k, v) } } } }
                            }
                        }
                    }
                }
            }
            putJsonArray("thoughts") {
                processed.talk.forEach { t ->
                    addJsonObject {
                        put("lines", "${t.lines.first}..${t.lines.last}")
                        put("heard", t.heard)
                        put("stage", t.stage.name)
                        putJsonArray("rules") { t.proposals.forEach { addJsonObject { proposal(it) } } }
                    }
                }
            }
            putJsonArray("topics") {
                processed.parts.forEach { part -> addJsonObject { put("topic", part.topic?.name ?: "NONE"); putJsonArray("lines") { part.lines.forEach { add(it) } } } }
            }
            putJsonArray("found") {
                processed.found.forEach { f ->
                    addJsonObject {
                        proposal(f.proposal)
                        putJsonArray("lines") { f.lines.forEach { add(it) } }
                        put("by", if (f.byInterpreter) "interpreter" else "rules")
                    }
                }
            }
            if (voorstel != null) putJsonArray("voorstel") {
                voorstel.forEach { r ->
                    addJsonObject {
                        proposal(r.proposal)
                        putJsonArray("lines") { r.lines.forEach { add(it) } }
                        put("checked", r.checked); put("changed", r.changed); put("added", r.added)
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
        p.match?.let { m -> putJsonObject("existing") { put("id", m.id); put("text", m.text); put("update", m.update) } }
        putJsonArray("calls") { p.calls.forEach { c -> addJsonObject { put("name", c.name); put("arguments", c.arguments) } } }
    }

    private val PRETTY = Json { prettyPrint = true }
}
