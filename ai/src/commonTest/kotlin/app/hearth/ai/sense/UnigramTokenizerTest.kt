package app.hearth.ai.sense

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UnigramTokenizerTest {
    private val vocab = listOf("<s>" to 0f, "<pad>" to 0f, "</s>" to 0f, "<unk>" to 0f, "▁" to -2f, "▁de" to -1f, "▁was" to -3f, "▁wa" to -2f, "s" to -2f, "w" to -4f, "a" to -4f)

    @Test
    fun wordsSplitIntoTheirBestPieces() {
        val t = UnigramTokenizer(vocab)
        // "▁was" (-3) beats "▁wa" + "s" (-4); one space between words, whatever was typed.
        assertContentEquals(intArrayOf(0, 5, 6, 2), t.encode("  de   was "))
        // An unknown letter is <unk>, two in a row are one.
        assertContentEquals(intArrayOf(0, 7, 3, 2), t.encode("waxx"))
    }

    @Test
    fun theVocabIsReadFromTokenizerJson() {
        val json = """{"model":{"type":"Unigram","unk_id":3,"vocab":[["<s>",0.0],["a\"b",-1.5],["▁x",-2.25e1]]}}"""
        assertEquals(listOf("<s>" to 0f, "a\"b" to -1.5f, "▁x" to -22.5f), UnigramTokenizer.parseVocab(json))
    }
}
