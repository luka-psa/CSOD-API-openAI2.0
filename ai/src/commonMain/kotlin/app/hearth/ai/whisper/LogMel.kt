package app.hearth.ai.whisper

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * What Whisper hears: 16 kHz mono speech as 80 log-mel bands over one 30-second window (3000 frames of 10 ms), the
 * same numbers as the reference feature extractor (Hann window of 400, hop 160, Slaney mel scale, log10, the top 8
 * decades kept, scaled to about -1..1). Shorter speech is padded with silence; only the frames that hear sound are
 * computed.
 */
object LogMel {
    const val RATE = 16_000
    const val BANDS = 80
    const val FRAMES = 3000
    const val SAMPLES = 480_000
    private const val N_FFT = 400
    private const val HOP = 160
    private const val BINS = N_FFT / 2 + 1

    // Slaney's mel scale: linear below 1 kHz, logarithmic above (declared before the filters that use it).
    private const val F_SP = 200.0 / 3
    private const val MIN_LOG_HZ = 1000.0
    private const val MIN_LOG_MEL = MIN_LOG_HZ / F_SP
    private val LOG_STEP = ln(6.4) / 27.0

    private val window = DoubleArray(N_FFT) { 0.5 - 0.5 * cos(2 * PI * it / N_FFT) }
    private val filters: Array<DoubleArray> = melFilters()

    /** [samples] (at most 30 s, -1..1) as [BANDS] × [FRAMES] values, band by band. */
    fun of(samples: FloatArray): FloatArray {
        val n = min(samples.size, SAMPLES)
        // The frames that reach sound; the rest of the window is silence (and its spectrum zero).
        val heard = min(FRAMES, (n + N_FFT / 2) / HOP + 1)
        val mel = DoubleArray(BANDS * FRAMES)
        val fft = Fft(N_FFT)
        val re = DoubleArray(N_FFT)
        val im = DoubleArray(N_FFT)
        val power = DoubleArray(BINS)
        for (f in 0 until heard) {
            val start = f * HOP - N_FFT / 2
            for (k in 0 until N_FFT) {
                re[k] = sample(samples, n, start + k) * window[k]
                im[k] = 0.0
            }
            fft.transform(re, im)
            for (b in 0 until BINS) power[b] = re[b] * re[b] + im[b] * im[b]
            for (m in 0 until BANDS) {
                val w = filters[m]
                var sum = 0.0
                for (b in 0 until BINS) sum += w[b] * power[b]
                mel[m * FRAMES + f] = sum
            }
        }
        var top = Double.NEGATIVE_INFINITY
        for (i in mel.indices) {
            mel[i] = log10(max(mel[i], 1e-10))
            if (mel[i] > top) top = mel[i]
        }
        val floor = top - 8.0
        return FloatArray(mel.size) { ((max(mel[it], floor) + 4.0) / 4.0).toFloat() }
    }

    /** The waveform padded to 30 s with silence, then mirrored at both ends (as the reference does). */
    private fun sample(samples: FloatArray, n: Int, at: Int): Double {
        val i = when {
            at < 0 -> -at
            at >= SAMPLES -> 2 * (SAMPLES - 1) - at
            else -> at
        }
        return if (i < n) samples[i].toDouble() else 0.0
    }

    /** Triangular filters on the Slaney mel scale, area-normalised (librosa's defaults for 16 kHz, 400 points, 80 bands). */
    private fun melFilters(): Array<DoubleArray> {
        val fftFreqs = DoubleArray(BINS) { it * (RATE / 2.0) / (BINS - 1) }
        val lo = hzToMel(0.0)
        val hi = hzToMel(RATE / 2.0)
        val melF = DoubleArray(BANDS + 2) { melToHz(lo + (hi - lo) * it / (BANDS + 1)) }
        return Array(BANDS) { m ->
            val norm = 2.0 / (melF[m + 2] - melF[m])
            DoubleArray(BINS) { b ->
                val lower = (fftFreqs[b] - melF[m]) / (melF[m + 1] - melF[m])
                val upper = (melF[m + 2] - fftFreqs[b]) / (melF[m + 2] - melF[m + 1])
                max(0.0, min(lower, upper)) * norm
            }
        }
    }

    private fun hzToMel(f: Double) = if (f >= MIN_LOG_HZ) MIN_LOG_MEL + ln(f / MIN_LOG_HZ) / LOG_STEP else f / F_SP
    private fun melToHz(m: Double) = if (m >= MIN_LOG_MEL) MIN_LOG_HZ * exp(LOG_STEP * (m - MIN_LOG_MEL)) else F_SP * m
}

/**
 * A complex FFT for any length made of small factors (400 = 2·2·2·2·5·5), by mixed-radix decimation in time: each
 * level splits into p interleaved parts and joins them with p-point butterflies. Plain arrays, no allocation per call
 * beyond two work buffers.
 */
internal class Fft(private val n: Int) {
    private val cosT = DoubleArray(n) { cos(2 * PI * it / n) }
    private val sinT = DoubleArray(n) { -sin(2 * PI * it / n) }
    private val outRe = DoubleArray(n)
    private val outIm = DoubleArray(n)
    private val tRe = DoubleArray(8)
    private val tIm = DoubleArray(8)

    init {
        var m = n
        for (p in intArrayOf(2, 3, 5, 7)) while (m % p == 0) m /= p
        require(m == 1) { "FFT length $n has a factor larger than 7" }
    }

    /** The DFT of [re] + i·[im], in place. */
    fun transform(re: DoubleArray, im: DoubleArray) {
        step(re, im, 0, 1, n, 0)
        outRe.copyInto(re)
        outIm.copyInto(im)
    }

    /** The DFT of the [len] values at [offset], [offset] + [stride], … into out[[at] until [at] + [len]]. */
    private fun step(re: DoubleArray, im: DoubleArray, offset: Int, stride: Int, len: Int, at: Int) {
        if (len == 1) {
            outRe[at] = re[offset]
            outIm[at] = im[offset]
            return
        }
        val p = when { len % 2 == 0 -> 2; len % 3 == 0 -> 3; len % 5 == 0 -> 5; else -> 7 }
        val m = len / p
        for (q in 0 until p) step(re, im, offset + q * stride, stride * p, m, at + q * m)
        // Butterflies: X[k + r·m] = Σ_q W_len^(q·(k + r·m)) · Y_q[k].
        val unit = n / len
        for (k in 0 until m) {
            for (q in 0 until p) {
                tRe[q] = outRe[at + q * m + k]
                tIm[q] = outIm[at + q * m + k]
            }
            for (r in 0 until p) {
                var sr = 0.0
                var si = 0.0
                val idx = k + r * m
                for (q in 0 until p) {
                    val e = (q * idx % len) * unit
                    val c = cosT[e]
                    val s = sinT[e]
                    sr += tRe[q] * c - tIm[q] * s
                    si += tRe[q] * s + tIm[q] * c
                }
                outRe[at + idx] = sr
                outIm[at + idx] = si
            }
        }
    }
}
