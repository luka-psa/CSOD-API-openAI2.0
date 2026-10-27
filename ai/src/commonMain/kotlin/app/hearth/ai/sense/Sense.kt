package app.hearth.ai.sense

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.exp
import kotlin.math.sqrt

/** What a sentence does in the weekoverleg, or where it belongs: what the sentence model tells apart. */
enum class Sense { AGREE, PROPOSAL, DOUBT, QUESTION, TALK, WIN, FRICTION, RULE, TASK, EVENT, FREE_TIME, ROUTINE, SHOPPING, MEAL, GOAL }

/** The model's reading of one sentence and how sure it is (0–1). */
data class Reading(val sense: Sense, val confidence: Float)

/** Runs the model: token ids in, its last hidden state out ([ids].size × dimension floats, row by row). */
fun interface SentenceEncoder {
    suspend fun hidden(ids: IntArray): FloatArray
}

/**
 * Where the model lives on this device: the tablet keeps the files in its own storage, the browser in its cache. Both
 * download them once, from the model's public home, only when the family switches smarter listening on.
 */
interface SenseRuntime {
    /** False where the model can't run (then the rules carry on alone). */
    val available: Boolean
    /** The files are here already. */
    suspend fun downloaded(): Boolean
    /** Fetches the model and its tokenizer; [progress] goes from 0 to 1. */
    suspend fun download(progress: (Float) -> Unit)
    /** The tokenizer's `tokenizer.json`. */
    suspend fun tokenizerJson(): String
    /** Opens the model to run it. */
    suspend fun open(): SentenceEncoder
    /** Frees the space again. */
    suspend fun remove()

    companion object {
        /** multilingual-e5-small, quantized (≈ 112 MB) and its tokenizer (≈ 16 MB). */
        const val MODEL_URL = "https://huggingface.co/Xenova/multilingual-e5-small/resolve/main/onnx/model_quantized.onnx"
        const val TOKENIZER_URL = "https://huggingface.co/Xenova/multilingual-e5-small/resolve/main/tokenizer.json"
    }
}

/** No model on this device: the rules alone. */
object NoSenseRuntime : SenseRuntime {
    override val available = false
    override suspend fun downloaded() = false
    override suspend fun download(progress: (Float) -> Unit) = error("No sentence model here")
    override suspend fun tokenizerJson() = error("No sentence model here")
    override suspend fun open() = error("No sentence model here")
    override suspend fun remove() {}
}

/**
 * The sentence model on top of its encoder: a sentence becomes a meaning (mean of the hidden state, unit length), and a
 * small classifier trained on [SENSE_EXAMPLES] when it loads tells what that meaning is.
 */
class SenseModel(private val tokenizer: UnigramTokenizer, private val encoder: SentenceEncoder, examples: List<Pair<Sense, String>> = SENSE_EXAMPLES) {
    private val examples = examples
    private val labels = Sense.entries
    private var weights = FloatArray(0)
    private var bias = FloatArray(labels.size)
    private var dim = 0

    /** Reads every example and learns them (a few seconds, once). */
    suspend fun train(epochs: Int = 300) {
        val x = examples.map { embed(it.second) }
        dim = x.first().size
        val y = examples.map { it.first.ordinal }
        val k = labels.size
        val w = FloatArray(dim * k)
        val b = FloatArray(k)
        val n = x.size
        val p = FloatArray(k)
        repeat(epochs) {
            val gw = FloatArray(dim * k)
            val gb = FloatArray(k)
            for (i in 0 until n) {
                scores(x[i], w, b, p)
                p[y[i]] -= 1f
                for (c in 0 until k) {
                    val g = p[c] / n
                    if (g == 0f) continue
                    gb[c] += g
                    val row = x[i]
                    for (d in 0 until dim) gw[d * k + c] += SCALE * row[d] * g
                }
            }
            for (j in w.indices) w[j] -= RATE * (gw[j] + L2 * w[j])
            for (c in 0 until k) b[c] -= RATE * gb[c]
        }
        weights = w
        bias = b
    }

    /** What [text] most likely is. */
    suspend fun read(text: String): Reading {
        check(weights.isNotEmpty()) { "Train first" }
        val p = FloatArray(labels.size)
        scores(embed(text), weights, bias, p)
        val best = p.indices.maxBy { p[it] }
        return Reading(labels[best], p[best])
    }

    /** The meaning of a sentence: e5 reads it as a "query", averages the tokens and scales to unit length. */
    suspend fun embed(text: String): FloatArray {
        val ids = tokenizer.encode("query: " + UnigramTokenizer.normalize(text))
        val hidden = encoder.hidden(ids)
        val d = hidden.size / ids.size
        val v = FloatArray(d)
        for (t in ids.indices) for (j in 0 until d) v[j] += hidden[t * d + j]
        var norm = 0f
        for (j in 0 until d) { v[j] /= ids.size; norm += v[j] * v[j] }
        norm = sqrt(norm)
        for (j in 0 until d) v[j] /= norm
        return v
    }

    /** Softmax of the classifier's scores for [x], written into [p]. */
    private fun scores(x: FloatArray, w: FloatArray, b: FloatArray, p: FloatArray) {
        val k = p.size
        for (c in 0 until k) p[c] = b[c]
        for (d in x.indices) {
            val v = SCALE * x[d]
            if (v == 0f) continue
            for (c in 0 until k) p[c] += v * w[d * k + c]
        }
        val max = p.max()
        var sum = 0f
        for (c in 0 until k) { p[c] = exp(p[c] - max); sum += p[c] }
        for (c in 0 until k) p[c] /= sum
    }

    private companion object {
        const val SCALE = 10f
        const val RATE = 0.5f
        const val L2 = 1e-3f
    }
}

/**
 * Smarter listening: off until the family switches it on (and downloads the model). While it is on, the weekoverleg's
 * sentences are read by the model; [read] answers from what was read already, so the conversation never waits on it,
 * and the rules take what the model hasn't read (yet) or isn't sure about.
 */
class SmartListening(private val runtime: SenseRuntime) {
    sealed interface State {
        data object Off : State
        data class Downloading(val progress: Float) : State
        /** Reading its examples, once after starting. */
        data object Loading : State
        data object Ready : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()
    val available: Boolean get() = runtime.available

    private val lock = Mutex()
    private var model: SenseModel? = null
    /** Replaced as a whole, so [read] never sees it half-written. */
    private var readings: Map<String, Reading> = emptyMap()

    /** Downloads when needed, then loads. Safe to call again. */
    suspend fun start() = lock.withLock {
        if (!runtime.available || model != null) return@withLock
        try {
            if (!runtime.downloaded()) {
                _state.value = State.Downloading(0f)
                runtime.download { _state.value = State.Downloading(it) }
            }
            _state.value = State.Loading
            val m = SenseModel(UnigramTokenizer(UnigramTokenizer.parseVocab(runtime.tokenizerJson())), runtime.open())
            m.train()
            model = m
            _state.value = State.Ready
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _state.value = State.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    /** Off again, and the space back. */
    suspend fun stop() = lock.withLock {
        model = null
        readings = emptyMap()
        runCatching { runtime.remove() }
        _state.value = State.Off
    }

    /** Reads the sentences not read yet; true when something new was read. */
    suspend fun prepare(sentences: Collection<String>): Boolean {
        val m = model ?: return false
        val todo = lock.withLock { sentences.map(::key).filter { it.isNotEmpty() && it !in readings }.distinct() }
        if (todo.isEmpty()) return false
        val read = todo.map { it to m.read(it) }
        lock.withLock { readings = readings + read }
        return true
    }

    /** What the model read for [sentence], if it did. */
    fun read(sentence: String): Reading? = readings[key(sentence)]

    private fun key(s: String) = UnigramTokenizer.normalize(s).lowercase()
}
