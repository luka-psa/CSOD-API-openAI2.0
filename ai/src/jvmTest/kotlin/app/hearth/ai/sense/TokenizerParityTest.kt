package app.hearth.ai.sense

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Same ids as the model's own tokenizer, sentence by sentence. Runs where the model is at hand (HEARTH_SENSE_DIR). */
class TokenizerParityTest {
    @Test
    fun sameIdsAsTheReferenceTokenizer() {
        val dir = System.getenv("HEARTH_SENSE_DIR")?.let(::File)?.takeIf { File(it, "tokenizer.json").exists() } ?: return
        val tokenizer = UnigramTokenizer(UnigramTokenizer.parseVocab(File(dir, "tokenizer.json").readText()))
        val cases = File(dir, "ids.tsv").readLines().filter { it.isNotBlank() }.map { it.split('\t') }
        val wrong = cases.filter { (text, ids) -> tokenizer.encode(text).joinToString(" ") != ids }
        assertEquals(emptyList(), wrong.map { it[0] }, "${wrong.size} of ${cases.size} differ")
    }
}
