package app.hearth.ai.whisper

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/**
 * Whisper as the tablet runs it, against the reference (Python, ONNX Runtime): the same tokens for the same prompt,
 * the same words for real Dutch speech. Needs the model files (HEARTH_WHISPER_DIR) and, for speech, a folder of 16 kHz
 * WAV files with the reference's transcript next to each as .ref.txt (HEARTH_WHISPER_AUDIO); skipped without them.
 */
class WhisperTest {
    private val dir = System.getenv("HEARTH_WHISPER_DIR")?.let(::File)?.takeIf { File(it, "encoder_model_quantized.onnx").exists() }
    private val audio = System.getenv("HEARTH_WHISPER_AUDIO")?.let(::File)?.takeIf { it.isDirectory }

    @Test
    fun `the tokenizer gives the reference ids`() {
        dir ?: return
        val t = WhisperTokenizer.parse(File(dir, "tokenizer.json").readText())
        val cases = mapOf(
            " Mia, Jules, vaatwasser, wasmand." to intArrayOf(28545, 11, 508, 3473, 11, 2773, 267, 48309, 11, 390, 22396, 13),
            " Volgens Angel (2006) kunnen organisaties beter presteren." to intArrayOf(8911, 23212, 14902, 522, 7629, 21, 8, 18377, 15223, 267, 530, 45425, 659, 3120, 268, 13),
            " ça va? 's avonds 12u30 — één keer" to intArrayOf(2788, 2773, 30, 922, 82, 1305, 10166, 2272, 84, 3446, 3466, 39133, 31531),
        )
        for ((text, ids) in cases) {
            assertContentEquals(ids, t.encode(text), text)
            assertEquals(text, t.decode(ids))
        }
        assertEquals(50271, t.special("<|nl|>"))
    }

    @Test
    fun `real Dutch speech is written as the reference writes it`() = runTest(timeout = 10.minutes) {
        dir ?: return@runTest
        audio ?: return@runTest
        val whisper = Whisper(WhisperTokenizer.parse(File(dir, "tokenizer.json").readText()), OrtWhisper(dir))
        audio.listFiles { f -> f.name.endsWith(".wav") }!!.sorted().forEach { wav ->
            val want = File(wav.path.removeSuffix(".wav") + ".ref.txt").takeIf { it.exists() }?.readText()?.trim() ?: return@forEach
            val start = System.currentTimeMillis()
            val got = whisper.write(readWav(wav), "nl")
            println("${wav.name}: ${System.currentTimeMillis() - start} ms, logProb ${got.logProb}, noSpeech ${got.noSpeech}: ${got.text}")
            assertEquals(want, got.text, wav.name)
        }
    }

    companion object {
        /** A 16 kHz mono WAV (16-bit or 32-bit float) as samples. */
        fun readWav(file: File): FloatArray {
            val b = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            var at = 12
            var format = 1
            var bits = 16
            while (at + 8 <= b.limit()) {
                val id = String(ByteArray(4) { b.get(at + it) })
                val size = b.getInt(at + 4)
                if (id == "fmt ") { format = b.getShort(at + 8).toInt(); bits = b.getShort(at + 22).toInt() }
                if (id == "data") {
                    val n = size / (bits / 8)
                    return FloatArray(n) { i -> if (format == 3) b.getFloat(at + 8 + i * 4) else b.getShort(at + 8 + i * 2) / 32768f }
                }
                at += 8 + size + (size and 1)
            }
            error("No data in ${file.name}")
        }
    }
}
