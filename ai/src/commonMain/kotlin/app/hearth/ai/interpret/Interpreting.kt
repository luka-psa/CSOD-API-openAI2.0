package app.hearth.ai.interpret

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Where the interpreter's model lives on this device: fetched once in the background from [INTERPRETER_URL] (the
 * household's own download site, next to the app) by its [ModelManifest], run on the device itself.
 */
interface InterpreterRuntime {
    /** False where it can't run here, or this build doesn't offer it. */
    val available: Boolean
    /** On without anyone switching it on (the tablet); elsewhere it waits for the switch. */
    val onByDefault: Boolean get() = false
    suspend fun downloaded(): Boolean
    /** True when the site has a newer model than this device (false when offline). */
    suspend fun outdated(): Boolean = false
    /** [progress] goes from 0 to 1. */
    suspend fun download(progress: (Float) -> Unit)
    suspend fun tokenizerJson(): String
    suspend fun open(): Decoder
    suspend fun remove()
}

/** `manifest.json` next to the model: each file, the parts it is split in (each under 100 MB), its size and SHA-256. */
@kotlinx.serialization.Serializable
data class ModelManifest(val version: String, val files: List<ModelFile>) {
    val size: Long get() = files.sumOf { it.size }

    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        fun parse(text: String): ModelManifest = json.decodeFromString(serializer(), text)
    }
}

@kotlinx.serialization.Serializable
data class ModelFile(val name: String, val parts: List<String>, val size: Long, val sha256: String)

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
            // A newer model on the site replaces this one (offline: the one we have).
            if (!runtime.downloaded() || runtime.outdated()) {
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
