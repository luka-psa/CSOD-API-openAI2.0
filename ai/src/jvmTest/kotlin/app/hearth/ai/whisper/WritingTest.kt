package app.hearth.ai.whisper

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * A meeting written the way the tablet writes it: the recording in small blocks, cut at the pauses, each stretch
 * written by Whisper with the household's words in front. Real Dutch speech when HEARTH_WHISPER_DIR and
 * HEARTH_WHISPER_AUDIO are set (see [WhisperTest]); the filter on what Whisper invents always runs.
 */
class WritingTest {
    private val dir = System.getenv("HEARTH_WHISPER_DIR")?.let(::File)?.takeIf { File(it, "encoder_model_quantized.onnx").exists() }
    private val audio = System.getenv("HEARTH_WHISPER_AUDIO")?.let(::File)?.takeIf { it.isDirectory }

    private class Files(private val dir: File) : WhisperRuntime {
        override val available = true
        override suspend fun downloaded() = true
        override suspend fun download(progress: (Float) -> Unit) {}
        override suspend fun tokenizerJson() = File(dir, "tokenizer.json").readText()
        override suspend fun open(): WhisperModel = OrtWhisper(dir)
        override suspend fun remove() {}
    }

    @Test
    fun `what Whisper invents on silence is dropped`() {
        assertNull(Writing.heard(Whisper.Written("Ondertiteld door de Amara.org gemeenschap", -0.3f, 0.1f)))
        assertNull(Writing.heard(Whisper.Written("Bedankt voor het kijken!", -0.4f, 0.2f)))
        assertNull(Writing.heard(Whisper.Written("Ja.", -1.4f, 0.8f)))
        assertNull(Writing.heard(Whisper.Written("...", -0.2f, 0.1f)))
        assertEquals("Ja, dat doe ik.", Writing.heard(Whisper.Written("Ja, dat doe ik.", -0.3f, 0.2f)))
    }

    @Test
    fun `a recorded meeting is written stretch by stretch, at the moment it was said`() = runTest(timeout = 15.minutes) {
        dir ?: return@runTest
        audio ?: return@runTest
        val random = Random(3)
        fun hiss(ms: Int) = FloatArray(ms * 16) { ((random.nextDouble() - 0.5) * 0.002).toFloat() }
        // The clips at the level of people around a table (+12 dB), over a soft room hiss.
        val clips = (0..3).map { WhisperTest.readWav(File(audio, "fleurs$it.wav")) }
        val refs = (0..3).map { File(audio, "fleurs$it.ref.txt").readText().trim() }
        val starts = ArrayList<Long>()
        var recording = hiss(1_000)
        for (c in clips) {
            starts += recording.size / 16L
            recording += FloatArray(c.size) { 4 * c[it] + ((random.nextDouble() - 0.5) * 0.002).toFloat() } + hiss(2_500)
        }

        val writing = Writing(Files(dir), backgroundScope)
        writing.start()
        val lines = ArrayList<Pair<String, Long>>()
        val began = System.currentTimeMillis()
        val session = writing.session("nl", { listOf("Mia", "Jules", "vaatwasser") }) { text, at -> lines += text to at }
        var at = 0
        while (at < recording.size) {
            session.hear(recording.copyOfRange(at, minOf(recording.size, at + 1_600)))
            at += 1_600
        }
        session.end()
        writing.drained()
        val took = System.currentTimeMillis() - began
        println("${recording.size / 16} ms of recording written in $took ms: ${lines.joinToString("\n")}")

        assertEquals(4, lines.size, lines.toString())
        lines.forEachIndexed { i, (text, _) ->
            val want = words(refs[i])
            val got = words(text)
            val same = want.count { it in got }
            assertTrue(same * 10 >= want.size * 8, "stretch $i: '$text' vs '${refs[i]}'")
        }
        // Each line carries the moment it was said (within half a second of where the clip starts).
        val first = lines.first().second
        lines.forEachIndexed { i, (_, when_) -> assertTrue(kotlin.math.abs((when_ - first) - (starts[i] - starts[0])) < 500, "line $i at ${when_ - first}") }
    }

    private fun words(text: String) = Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase()).map { it.value }.toList()
}
