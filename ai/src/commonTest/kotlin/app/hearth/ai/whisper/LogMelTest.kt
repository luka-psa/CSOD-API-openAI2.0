package app.hearth.ai.whisper

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Whisper's ears: the same features as the reference extractor, and loops cut off. */
class LogMelTest {
    @Test
    fun `the mixed-radix FFT gives the plain DFT`() {
        val n = 400
        val re = DoubleArray(n) { sin(it * 0.37) + 0.5 * cos(it * 1.9) + if (it % 7 == 0) 0.3 else 0.0 }
        val im = DoubleArray(n)
        val wantRe = DoubleArray(n) { k -> (0 until n).sumOf { t -> re[t] * cos(2 * PI * k * t / n) } }
        val wantIm = DoubleArray(n) { k -> (0 until n).sumOf { t -> -re[t] * sin(2 * PI * k * t / n) } }
        Fft(n).transform(re, im)
        for (k in 0 until n) {
            assertTrue(abs(re[k] - wantRe[k]) < 1e-9 && abs(im[k] - wantIm[k]) < 1e-9, "bin $k")
        }
    }

    @Test
    fun `features match the reference extractor`() {
        // Three seconds: a 440 Hz tone, a rising chirp and a soft 3 kHz tone (values from the reference, numpy).
        val x = FloatArray(48_000) { i ->
            val t = i / 16_000.0
            (0.3 * sin(2 * PI * 440 * t) + 0.2 * sin(2 * PI * (200 + 600 * t) * t) + 0.05 * sin(2 * PI * 3000 * t)).toFloat()
        }
        val mel = LogMel.of(x)
        assertEquals(LogMel.BANDS * LogMel.FRAMES, mel.size)
        val want = listOf(
            0 to 0 to 1.0790175199508667f,
            10 to 50 to 1.2378143072128296f,
            11 to 120 to 1.3272794485092163f,
            25 to 200 to -0.5622779726982117f,
            79 to 299 to -0.36604708433151245f,
            30 to 300 to 0.1430072784423828f,
            5 to 2999 to -0.5622779726982117f,
        )
        for ((at, v) in want) assertEquals(v, mel[at.first * LogMel.FRAMES + at.second], 1e-4f, "band ${at.first}, frame ${at.second}")
        assertEquals(-129379.38, mel.sumOf { it.toDouble() }, 0.5)
        val floor = mel.min()
        assertEquals(5378, mel.count { it > floor + 1e-6f }, "frames above the floor")
    }

    @Test
    fun `a phrase said over and over is kept once`() {
        val loop = listOf(5, 6, 7) + List(6) { 9 }
        assertTrue(Whisper.looping(loop))
        assertEquals(listOf(5, 6, 7, 9), Whisper.trimLoop(loop))
        val phrase = listOf(1, 2) + List(3) { listOf(10, 11, 12) }.flatten()
        assertEquals(listOf(1, 2, 10, 11, 12), Whisper.trimLoop(phrase))
        assertTrue(!Whisper.looping(listOf(1, 2, 3, 1, 2, 3)))
    }
}
