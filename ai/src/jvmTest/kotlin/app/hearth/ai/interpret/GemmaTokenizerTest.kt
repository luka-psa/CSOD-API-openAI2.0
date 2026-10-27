package app.hearth.ai.interpret

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The same ids as the reference tokenizer, and back to the same text. Runs where the model is at hand (HEARTH_INTERPRETER_DIR). */
class GemmaTokenizerTest {
    @Test
    fun sameIdsAsTheReference() {
        val dir = System.getenv("HEARTH_INTERPRETER_DIR")?.let(::File)?.takeIf { File(it, "ids.jsonl").exists() } ?: return
        val t = GemmaTokenizer.parse(File(dir, "tokenizer.json").readText())
        val cases = File(dir, "ids.jsonl").readLines().filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }
        val wrong = cases.filter { c ->
            val text = c["text"]!!.jsonPrimitive.content
            val ids = c["ids"]!!.jsonArray.map { it.jsonPrimitive.int }
            val got = t.encode(text).toList()
            // A literal "▁" decodes to a space, as in the reference; the ids are what matter.
            got != ids || ("▁" !in text && t.decode(got.toIntArray()) != text)
        }.map { it["text"]!!.jsonPrimitive.content.takeLast(80) }
        assertEquals(emptyList(), wrong, "${wrong.size} of ${cases.size} differ")
    }
}
