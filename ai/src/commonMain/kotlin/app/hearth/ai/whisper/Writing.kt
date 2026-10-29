package app.hearth.ai.whisper

import app.hearth.core.model.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Beter uitschrijven: Luister mee records the meeting itself and Whisper writes it down on this device, a stretch at a
 * time, a few seconds behind the talk. Nothing between two stretches is lost (the recording never stops), the audio
 * never leaves the device and is never stored: each stretch is written and let go. The model is downloaded once and
 * opened only while a meeting is being written.
 */
class Writing(private val runtime: WhisperRuntime, private val scope: CoroutineScope) {
    sealed interface State {
        data object Off : State
        data class Downloading(val progress: Float) : State
        data object Ready : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()
    val available: Boolean get() = runtime.available

    private val _pending = MutableStateFlow(0)
    /** Stretches heard and not written yet. */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val setup = Mutex()
    private val lock = Mutex()
    private var whisper: Whisper? = null
    private var model: WhisperModel? = null
    /** Meetings being written right now (one, normally). */
    private val sessions = MutableStateFlow(0)

    /** Downloads the model once (in the background; the app goes on). */
    suspend fun start() = setup.withLock {
        if (!runtime.available || _state.value == State.Ready) return@withLock
        try {
            if (!runtime.downloaded()) {
                _state.value = State.Downloading(0f)
                runtime.download { _state.value = State.Downloading(it) }
            }
            _state.value = State.Ready
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _state.value = State.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    /** Off: the model goes, and its space with it. */
    suspend fun stop() = setup.withLock {
        lock.withLock { release() }
        runCatching { runtime.remove() }
        _state.value = State.Off
    }

    /** Ready to write a meeting. */
    val ready: Boolean get() = _state.value == State.Ready

    /** Waits until every stretch heard so far is written. */
    suspend fun drained() {
        pending.first { it == 0 }
    }

    /**
     * Writes one meeting: feed it the recording with [Session.hear]; each stretch written goes to [onLine] with the
     * moment it was said (epoch ms). [hints] are the household's own words, given to Whisper so it spells them right.
     */
    fun session(language: String, hints: () -> List<String>, onLine: (String, Long) -> Unit): Session = Session(language, hints, onLine)

    inner class Session internal constructor(
        private val language: String,
        private val hints: () -> List<String>,
        private val onLine: (String, Long) -> Unit,
    ) {
        private val cutter = SpeechCutter()
        private val chunks = Channel<SpeechChunk>(Channel.UNLIMITED)
        private val startedAt = nowMillis()

        /** Whether someone speaks right now: the stage shows it as it really is. */
        val speaking: Boolean get() = cutter.speaking

        init {
            sessions.update { it + 1 }
            scope.launch {
                try {
                    for (chunk in chunks) {
                        try {
                            val text = write(chunk.samples, language, prompt())
                            if (text != null) onLine(text, startedAt + chunk.startMs)
                        } catch (e: Throwable) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                        } finally {
                            _pending.update { (it - 1).coerceAtLeast(0) }
                        }
                    }
                } finally {
                    // The meeting is written: the model's memory goes back to the tablet until the next one.
                    withContext(NonCancellable) { lock.withLock { if (sessions.updateAndGet { it - 1 } == 0) release() } }
                }
            }
        }

        /** Samples from the microphone (16 kHz, -1..1), from the recording thread. */
        fun hear(samples: FloatArray) {
            cutter.feed(samples).forEach(::queue)
        }

        /** The recording stopped: what is still open is written too. */
        fun end() {
            cutter.flush().forEach(::queue)
            chunks.close()
        }

        private fun queue(chunk: SpeechChunk) {
            _pending.update { it + 1 }
            chunks.trySend(chunk)
        }

        /**
         * The household's own words (names, things, words the family put right), so Whisper spells them the family's
         * way. Not what was said before: on real talk that carries one stretch's mistakes into the next.
         */
        private fun prompt(): String =
            hints().filter { it.isNotBlank() }.distinct().take(40).joinToString(", ").let { if (it.isEmpty()) it else "$it." }
    }

    /** One stretch written, or null when nobody really spoke in it. */
    private suspend fun write(samples: FloatArray, language: String, prompt: String): String? = lock.withLock {
        if (_state.value != State.Ready) return@withLock null
        val w = whisper ?: run {
            val opened = runtime.open()
            model = opened
            Whisper(WhisperTokenizer.parse(runtime.tokenizerJson()), opened).also { whisper = it }
        }
        heard(w.write(samples, language, prompt))
    }

    private fun release() {
        model?.close()
        model = null
        whisper = null
    }

    companion object {
        /**
         * What Whisper wrote, or null: nobody spoke (it says so itself, and is unsure of its words), or it wrote one of
         * the lines it is known to invent on silence and noise (subtitle credits, "bedankt voor het kijken").
         */
        fun heard(w: Whisper.Written): String? {
            val text = w.text.trim()
            if (text.isEmpty()) return null
            if (w.noSpeech > 0.6f && w.logProb < -1f) return null
            if (text.split(Regex("\\s+")).size <= 12 && INVENTED.containsMatchIn(text)) return null
            if (text.none { it.isLetterOrDigit() }) return null
            return text
        }

        private val INVENTED = Regex(
            "ondertitel|amara\\.org|bedankt voor het (kijken|luisteren)|abonneer|tv gelderland|www\\.|\\.com\\b|" +
                "thanks for watching|thank you for watching|subscribe|subtitles by",
            RegexOption.IGNORE_CASE,
        )
    }
}
