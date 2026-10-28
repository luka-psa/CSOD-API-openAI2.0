package app.hearth.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.hearth.ai.route.Spoken

/** Google's own recognition service: asked for by name when the tablet's default one keeps failing. */
private val GOOGLE_SERVICE = ComponentName("com.google.android.googlequicksearchbox", "com.google.android.voicesearch.serviceapi.GoogleRecognitionService")

/**
 * Android's recognizer stops after every pause, so Luister mee starts it again right away until stopped. Offline
 * first (the talk stays on the tablet); without the language pack it asks online, then in nl-NL. When the tablet's
 * default recognition service keeps failing (on some tablets it isn't Google's), it moves to Google's service. Every
 * step shows in [onStatus], so what the tablet does is visible on the stage.
 */
@Composable
actual fun rememberTranscriber(
    language: String,
    vocabulary: List<String>,
    onLine: (String) -> Unit,
    onPartial: (String) -> Unit,
    onActive: (Boolean) -> Unit,
    onStatus: (ListenStatus) -> Unit,
): Transcriber {
    val context = LocalContext.current
    val line by rememberUpdatedState(onLine)
    val partial by rememberUpdatedState(onPartial)
    val active by rememberUpdatedState(onActive)
    val status by rememberUpdatedState(onStatus)
    val lang by rememberUpdatedState(language)
    val words by rememberUpdatedState(vocabulary)
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val state = remember {
        object {
            var on = false
            var offline = true
            var fallback: String? = null
            var failures = 0
            var google = false
            var recognizer: SpeechRecognizer? = null
        }
    }

    /** The service in use, for the status line: Google's when asked by name, else the tablet's default. */
    fun service(): String =
        if (state.google) GOOGLE_SERVICE.packageName
        else Settings.Secure.getString(context.contentResolver, "voice_recognition_service")?.substringBefore('/') ?: "?"

    // The recognizer beeps each time it starts listening again, after every pause; while Luister mee runs that is a
    // steady stream of beeps, so its sounds are muted until it stops.
    fun quiet(on: Boolean) {
        val how = if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)
            .forEach { stream -> runCatching { audio?.adjustStreamVolume(stream, how, 0) } }
    }

    fun googleInstalled(): Boolean =
        runCatching { context.packageManager.getPackageInfo(GOOGLE_SERVICE.packageName, 0); true }.getOrDefault(false)

    lateinit var listener: RecognitionListener

    fun recognizer(): SpeechRecognizer? {
        state.recognizer?.let { return it }
        val made = runCatching {
            if (state.google) SpeechRecognizer.createSpeechRecognizer(context, GOOGLE_SERVICE) else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull() ?: return null
        made.setRecognitionListener(listener)
        state.recognizer = made
        return made
    }

    fun listen() {
        val r = recognizer() ?: run { status(ListenStatus(ListenStatus.Event.ERROR, SpeechRecognizer.ERROR_CLIENT, service())); return }
        runCatching {
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, state.fallback ?: lang)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, state.fallback ?: lang)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, state.offline)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && words.isNotEmpty()) {
                            putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(words.take(50)))
                        }
                    },
            )
        }.onFailure { status(ListenStatus(ListenStatus.Event.ERROR, SpeechRecognizer.ERROR_CLIENT, service())) }
    }

    fun again(delayMs: Long) {
        if (state.on) handler.postDelayed({ if (state.on) listen() }, delayMs)
    }

    /** Start over with Google's service (a fresh recognizer, online first). */
    fun toGoogle() {
        state.recognizer?.destroy()
        state.recognizer = null
        state.google = true
        state.offline = false
        state.failures = 0
        again(300)
    }

    fun giveUp(code: Int) {
        state.on = false
        quiet(false)
        status(ListenStatus(ListenStatus.Event.GAVE_UP, code, service()))
        active(false)
    }

    listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = status(ListenStatus(ListenStatus.Event.READY, null, service()))
        override fun onBeginningOfSpeech() = status(ListenStatus(ListenStatus.Event.SPEECH, null, service()))
        override fun onPartialResults(results: Bundle?) {
            state.failures = 0
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(partial)
        }
        override fun onResults(results: Bundle?) {
            state.failures = 0
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { Spoken.pick(it, words, dutch = lang.startsWith("nl")) }?.let(line)
            partial("")
            again(100)
        }
        override fun onError(error: Int) {
            partial("")
            // A pause with nothing said is normal: listen again. Anything else counts.
            val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (!silence) {
                state.failures++
                status(ListenStatus(ListenStatus.Event.ERROR, error, service()))
            } else status(ListenStatus(ListenStatus.Event.QUIET, error, service()))
            val language = error == 12 || error == 13
            when {
                error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> giveUp(error)
                // 12: language not supported, 13: language pack not there. Online next, then plain Dutch.
                language && state.offline -> { state.offline = false; again(100) }
                language && state.fallback == null && lang.startsWith("nl") -> { state.fallback = "nl-NL"; again(100) }
                // The tablet's own service keeps failing: Google's, if it is there.
                (language || error == SpeechRecognizer.ERROR_CLIENT || state.failures >= 3) && !state.google && googleInstalled() -> toGoogle()
                state.failures >= 6 -> giveUp(error)
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_SERVER -> again(1_500)
                else -> again(200)
            }
        }
        override fun onEndOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            if (state.on) quiet(false)
            state.on = false
            handler.removeCallbacksAndMessages(null)
            state.recognizer?.destroy()
            state.recognizer = null
        }
    }

    fun begin() {
        state.failures = 0
        if (!state.on) quiet(true)
        state.on = true
        active(true)
        status(ListenStatus(ListenStatus.Event.STARTING, null, service()))
        listen()
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else status(ListenStatus(ListenStatus.Event.GAVE_UP, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, service()))
    }
    return remember {
        Transcriber(
            available = available,
            start = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                else askMic.launch(Manifest.permission.RECORD_AUDIO)
            },
            stop = {
                if (state.on) quiet(false)
                state.on = false
                handler.removeCallbacksAndMessages(null)
                state.recognizer?.stopListening()
                status(ListenStatus(ListenStatus.Event.STOPPED, null, null))
                active(false)
            },
        )
    }
}
