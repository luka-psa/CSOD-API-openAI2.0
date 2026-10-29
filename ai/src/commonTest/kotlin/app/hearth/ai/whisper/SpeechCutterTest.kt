package app.hearth.ai.whisper

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The recording cut at the pauses people make: nothing mid-word, nothing lost, no silence for the model. */
class SpeechCutterTest {
    private val random = Random(7)

    /** "Speech": a voiced tone in bursts of [words] ms with short gaps; quiet: a soft room hiss. */
    private fun talk(ms: Int): FloatArray {
        val n = ms * 16
        return FloatArray(n) { i ->
            val inWord = (i / 16) % 400 < 340
            val tone = if (inWord) 0.2 * sin(2 * PI * 180 * i / 16_000.0) + 0.1 * sin(2 * PI * 720 * i / 16_000.0) else 0.0
            (tone + quietSample()).toFloat()
        }
    }

    private fun quiet(ms: Int) = FloatArray(ms * 16) { quietSample().toFloat() }
    private fun quietSample() = (random.nextDouble() - 0.5) * 0.002

    private fun cut(vararg parts: FloatArray, step: Int = 1_600): List<SpeechChunk> {
        val cutter = SpeechCutter()
        val all = parts.reduce { a, b -> a + b }
        val out = ArrayList<SpeechChunk>()
        var at = 0
        while (at < all.size) {
            out += cutter.feed(all.copyOfRange(at, minOf(all.size, at + step)))
            at += step
        }
        return out + cutter.flush()
    }

    @Test
    fun `a stretch ends at a pause once it is long enough, a long pause ends it anyway`() {
        val chunks = cut(quiet(3_000), talk(8_000), quiet(1_000), talk(3_000), quiet(3_000), talk(2_000), quiet(500))
        // 8 s of talk then a pause: one stretch. 3 s then a long pause: one. The last 2 s at the end: one.
        assertEquals(3, chunks.size, chunks.map { it.startMs to it.durationMs }.toString())
        assertTrue(chunks[0].startMs in 2_400L..3_000L, "starts just before the first word: ${chunks[0].startMs}")
        assertTrue(chunks[0].durationMs in 8_000L..9_200L, "${chunks[0].durationMs}")
        assertTrue(chunks[1].startMs in 11_400L..12_000L, "${chunks[1].startMs}")
        assertTrue(chunks[2].startMs in 17_400L..18_000L, "${chunks[2].startMs}")
    }

    @Test
    fun `a short pause in a short stretch keeps it together`() {
        val chunks = cut(quiet(2_000), talk(2_500), quiet(800), talk(4_000), quiet(2_500))
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].durationMs in 7_000L..8_400L, "${chunks[0].durationMs}")
    }

    @Test
    fun `talk without a pause is cut before 30 s at its quietest moment, and nothing is lost`() {
        val chunks = cut(quiet(1_000), talk(50_000), quiet(2_500))
        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.durationMs <= SpeechCutter.MAX_MS }, chunks.map { it.durationMs }.toString())
        // The second stretch starts where the first one ended.
        assertEquals(chunks[0].startMs + chunks[0].durationMs, chunks[1].startMs)
    }

    @Test
    fun `silence and a cough are not stretches`() {
        assertEquals(0, cut(quiet(10_000)).size)
        assertEquals(0, cut(quiet(3_000), talk(200), quiet(3_000)).size)
    }
}
