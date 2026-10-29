package app.hearth.ai.whisper

import kotlin.math.exp
import kotlin.math.ln

/** Where Whisper's files are and how they run on this platform (ONNX Runtime on the tablet; nothing in the browser yet). */
interface WhisperRuntime {
    /** False where it can't run (Luister mee then uses the platform's own recognizer). */
    val available: Boolean
    /** On by itself on a tablet, the shared screen it is made for. */
    val onByDefault: Boolean get() = false
    suspend fun downloaded(): Boolean
    /** Fetches the model files once, checked; [progress] goes from 0 to 1. */
    suspend fun download(progress: (Float) -> Unit)
    suspend fun tokenizerJson(): String
    /** Opens the encoder and decoder to run them. */
    suspend fun open(): WhisperModel
    suspend fun remove()

    companion object {
        /** Whisper small, multilingual, int8 (onnx-community export), at a fixed revision so the checksums hold. */
        const val REPO = "https://huggingface.co/onnx-community/whisper-small/resolve/36050c46d777d46dc4b5f43f6d90574fc38f8732"

        /** The files, where they are in [REPO], how big, and their SHA-256 (≈ 250 MB in all). */
        val FILES = listOf(
            WhisperFile("tokenizer.json", "tokenizer.json", 2_480_466, "27fc476bfe7f17299480be2273fc0608e4d5a99aba2ab5dec5374b4482d1a566"),
            WhisperFile("encoder.onnx", "onnx/encoder_model_quantized.onnx", 92_326_160, "a43a83f3c5361cd591cfa7c36f14b43cf7cb22f47a415cc14a8d557be800fa92"),
            WhisperFile("decoder.onnx", "onnx/decoder_model_merged_quantized.onnx", 156_750_845, "ec07c3cbb64172c39791e26ee870a65ac22b458c36722bfe2776b3dbf741e0c9"),
        )
    }
}

/** No Whisper on this device: Luister mee uses the platform's own speech recognition. */
object NoWhisperRuntime : WhisperRuntime {
    override val available = false
    override suspend fun downloaded() = false
    override suspend fun download(progress: (Float) -> Unit) = error("No Whisper here")
    override suspend fun tokenizerJson() = error("No Whisper here")
    override suspend fun open() = error("No Whisper here")
    override suspend fun remove() {}
}

/** One of Whisper's files: kept as [name], fetched from [path] in [WhisperRuntime.REPO]. */
data class WhisperFile(val name: String, val path: String, val size: Long, val sha256: String)

/** The model, opened. */
interface WhisperModel {
    /** Hears one window: [features] are [LogMel.BANDS] × [LogMel.FRAMES] values. */
    suspend fun encode(features: FloatArray): HeardWindow
    fun close()
}

/** One encoded window, decoded a token at a time; the platform keeps the cache. */
interface HeardWindow {
    /** Feeds [ids] and gives the scores for the next token after each of the last [rows] of them. */
    suspend fun step(ids: IntArray, rows: Int = 1): Array<FloatArray>
    fun close()
}

/**
 * Writes down one stretch of speech (up to 30 s) with Whisper: greedy, in the given language, without timestamps, with
 * the household's own words and what was just said in front as a prompt (names and words spelled the way the family
 * spells them). Stretches with nobody speaking, and the model's well-known inventions on silence, give nothing; a
 * phrase that starts repeating itself is cut off.
 */
class Whisper(private val tokenizer: WhisperTokenizer, private val model: WhisperModel) {
    private val eot = tokenizer.special("<|endoftext|>")
    private val sot = tokenizer.special("<|startoftranscript|>")
    private val prev = tokenizer.special("<|startofprev|>")
    private val transcribe = tokenizer.special("<|transcribe|>")
    private val noTimestamps = tokenizer.special("<|notimestamps|>")
    private val noSpeech = tokenizer.specialOrNull("<|nocaptions|>") ?: tokenizer.specialOrNull("<|nospeech|>")
    private val space = tokenizer.encode(" ").firstOrNull()

    /** What was said, and how sure Whisper was (mean log-probability per token, chance of no speech). */
    data class Written(val text: String, val logProb: Float, val noSpeech: Float)

    suspend fun write(samples: FloatArray, language: String, prompt: String = ""): Written {
        val window = model.encode(LogMel.of(samples))
        try {
            val lang = tokenizer.specialOrNull("<|$language|>") ?: tokenizer.special("<|nl|>")
            val before = if (prompt.isBlank()) IntArray(0) else intArrayOf(prev) + tokenizer.encode(" " + prompt.trim()).takeLast(MAX_PROMPT)
            val start = before + intArrayOf(sot, lang, transcribe, noTimestamps)
            // The first step also gives the scores right after <|startoftranscript|>: the chance nobody spoke.
            val first = window.step(start, rows = 4)
            val noSpeechProb = noSpeech?.let { softmax(first[0])[it] } ?: 0f
            val out = ArrayList<Int>()
            var logProb = 0.0
            var scores = first.last()
            while (out.size < MAX_TOKENS) {
                suppress(scores, out.isEmpty())
                val next = argmax(scores)
                logProb += logSoftmaxAt(scores, next)
                if (next == eot) break
                out += next
                if (looping(out)) break
                scores = window.step(intArrayOf(next))[0]
            }
            val text = tokenizer.decode(trimLoop(out).toIntArray()).trim()
            val mean = if (out.isEmpty()) 0f else (logProb / (out.size + 1)).toFloat()
            return Written(text, mean, noSpeechProb)
        } finally {
            window.close()
        }
    }

    /** Only text tokens: no markers or timestamps, and not a space or the end straight away. */
    private fun suppress(scores: FloatArray, atStart: Boolean) {
        for (i in WhisperTokenizer.FIRST_SPECIAL + 1 until scores.size) scores[i] = Float.NEGATIVE_INFINITY
        for (i in SUPPRESS) if (i < scores.size) scores[i] = Float.NEGATIVE_INFINITY
        if (atStart) {
            scores[eot] = Float.NEGATIVE_INFINITY
            space?.let { scores[it] = Float.NEGATIVE_INFINITY }
        }
    }

    companion object {
        const val MAX_TOKENS = 224
        /** The prompt keeps its last tokens: the most recent words matter most. */
        const val MAX_PROMPT = 120

        /** Whisper's own list of tokens it never writes (from its generation config). */
        private val SUPPRESS = intArrayOf(
            1, 2, 7, 8, 9, 10, 14, 25, 26, 27, 28, 29, 31, 58, 59, 60, 61, 62, 63, 90, 91, 92, 93, 359, 503, 522, 542, 873, 893, 902, 918,
            922, 931, 1350, 1853, 1982, 2460, 2627, 3246, 3253, 3268, 3536, 3846, 3961, 4183, 4667, 6585, 6647, 7273, 9061, 9383, 10428,
            10929, 11938, 12033, 12331, 12562, 13793, 14157, 14635, 15265, 15618, 16553, 16604, 18362, 18956, 20075, 21675, 22520, 26130,
            26161, 26435, 28279, 29464, 31650, 32302, 32470, 36865, 42863, 47425, 49870, 50254, 50258, 50358, 50359, 50360, 50361, 50362,
        )

        fun argmax(scores: FloatArray): Int {
            var best = 0
            for (i in 1 until scores.size) if (scores[i] > scores[best]) best = i
            return best
        }

        fun softmax(scores: FloatArray): FloatArray {
            val top = scores.max()
            var sum = 0.0
            val e = DoubleArray(scores.size) { exp((scores[it] - top).toDouble()).also { v -> sum += v } }
            return FloatArray(scores.size) { (e[it] / sum).toFloat() }
        }

        private fun logSoftmaxAt(scores: FloatArray, i: Int): Double {
            val top = scores.max()
            var sum = 0.0
            for (s in scores) if (s != Float.NEGATIVE_INFINITY) sum += exp((s - top).toDouble())
            return (scores[i] - top) - ln(sum)
        }

        /** The same few tokens over and over at the end ("ja ja ja ja", a phrase said five times): a loop, not speech. */
        fun looping(tokens: List<Int>): Boolean = loopAt(tokens) != null

        private fun loopAt(tokens: List<Int>): Pair<Int, Int>? {
            for (n in 1..MAX_LOOP) {
                val times = if (n <= 2) 6 else 3
                if (tokens.size < n * times) continue
                val tail = tokens.subList(tokens.size - n, tokens.size)
                if ((1 until times).all { k -> tokens.subList(tokens.size - n * (k + 1), tokens.size - n * k) == tail }) return n to times
            }
            return null
        }

        /** A loop kept once. */
        fun trimLoop(tokens: List<Int>): List<Int> {
            val (n, times) = loopAt(tokens) ?: return tokens
            var end = tokens.size - n * (times - 1)
            // Further repeats before the ones that tripped it go too.
            while (end - 2 * n >= 0 && tokens.subList(end - 2 * n, end - n) == tokens.subList(end - n, end)) end -= n
            return tokens.subList(0, end)
        }

        private const val MAX_LOOP = 12
    }
}
