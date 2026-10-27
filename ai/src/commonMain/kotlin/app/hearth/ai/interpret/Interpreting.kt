package app.hearth.ai.interpret

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Where the interpreter's model lives on this device: downloaded once (model.onnx, its weights and its tokenizer) from
 * [INTERPRETER_URL], run on the device itself.
 */
interface InterpreterRuntime {
    /** False where it can't run here, or this build doesn't offer it. */
    val available: Boolean
    suspend fun downloaded(): Boolean
    /** [progress] goes from 0 to 1. */
    suspend fun download(progress: (Float) -> Unit)
    suspend fun tokenizerJson(): String
    suspend fun open(): Decoder
    suspend fun remove()

    companion object {
        /** The files, in download order, and how much of the download each one is. */
        val FILES = listOf("tokenizer.json" to 0.12f, "model.onnx" to 0.01f, "model.onnx.data" to 0.87f)
    }
}

object NoInterpreterRuntime : InterpreterRuntime {
    override val available = false
    override suspend fun downloaded() = false
    override suspend fun download(progress: (Float) -> Unit) = error("No interpreter here")
    override suspend fun tokenizerJson() = error("No interpreter here")
    override suspend fun open() = error("No interpreter here")
    override suspend fun remove() {}
}

/**
 * Understanding conversations: off until the family switches it on (it downloads once). While it is on, each meeting is
 * read by a [MeetingReader] over the [Interpreter]; one reading at a time, the newest talk first.
 */
class Interpreting(private val runtime: InterpreterRuntime) {
    sealed interface State {
        data object Off : State
        data class Downloading(val progress: Float) : State
        data object Loading : State
        data object Ready : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()
    val available: Boolean get() = runtime.available

    private val lock = Mutex()
    private var interpreter: Interpreter? = null
    private var reader: MeetingReader? = null
    private var date = ""

    suspend fun start() = lock.withLock {
        if (!runtime.available || interpreter != null) return@withLock
        try {
            if (!runtime.downloaded()) {
                _state.value = State.Downloading(0f)
                runtime.download { _state.value = State.Downloading(it) }
            }
            _state.value = State.Loading
            interpreter = Interpreter(GemmaTokenizer.parse(runtime.tokenizerJson()), runtime.open())
            _state.value = State.Ready
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _state.value = State.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    suspend fun stop() = lock.withLock {
        interpreter = null
        reader = null
        runCatching { runtime.remove() }
        _state.value = State.Off
    }

    /**
     * The meeting so far ([lines], oldest first, on [today] as "maandag 2 november") read as it stands; a new meeting (its
     * first line changed) starts afresh. Null when the interpreter isn't ready.
     */
    suspend fun read(lines: List<String>, today: String): List<MeetingReader.Found>? {
        val i = interpreter ?: return null
        val r = lock.withLock {
            if (reader == null || date != today || lines.firstOrNull() != first) {
                date = today; first = lines.firstOrNull()
                reader = MeetingReader({ i.read(today, it) })
            }
            reader!!
        }
        return r.update(lines)
    }

    private var first: String? = null
}
