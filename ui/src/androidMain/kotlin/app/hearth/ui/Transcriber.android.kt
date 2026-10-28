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
            // How plainly it asks: 0 = offline first, with the household's words and a longer pause; 1 = the plain
            // request the capture bar uses; 2 = Android's on-device recognizer (Android 12+).
            var level = 0
            var answered = false
            var loudest = -100f
            var spoke = false
            var watchdog: Runnable? = null
            // Empty rounds in a row (nothing heard or understood): three, and Hearth asks another way.
            var empty = 0
            // The speech app that may not use the microphone (its own permission, apart from Hearth's).
            var blocked: String? = null
            var silenceCheck: Runnable? = null
        }
    }

    /** The service in use, for the status line: Google's when asked by name, else the tablet's default. */
    fun service(): String =
        (if (state.level == 2) "on-device" else if (state.google) GOOGLE_SERVICE.packageName
        else Settings.Secure.getString(context.contentResolver, "voice_recognition_service")?.substringBefore('/') ?: "?") +
            if (state.level > 0) " (${state.level})" else ""

    // The recognizer beeps each time it starts listening again, after every pause; while Luister mee runs that is a
    // steady stream of beeps, so its sounds are muted until it stops.
    fun quiet(on: Boolean) {
        val how = if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)
            .forEach { stream -> runCatching { audio?.adjustStreamVolume(stream, how, 0) } }
    }

    /** The app that does the recognizing: Google's when asked by name, else the tablet's default service. */
    fun servicePackage(): String? =
        if (state.google) GOOGLE_SERVICE.packageName
        else Settings.Secure.getString(context.contentResolver, "voice_recognition_service")?.substringBefore('/')

    /**
     * Whether that app may use the microphone itself. Hearth's own permission isn't enough: Android hands the listening
     * to the speech app, and without its microphone permission it hears silence (the browser has its own permission,
     * so it works there).
     */
    fun mayListen(pkg: String?): Boolean =
        pkg == null || pkg == context.packageName ||
            context.packageManager.checkPermission(Manifest.permission.RECORD_AUDIO, pkg) == PackageManager.PERMISSION_GRANTED

    fun googleInstalled(): Boolean =
        runCatching { context.packageManager.getPackageInfo(GOOGLE_SERVICE.packageName, 0); true }.getOrDefault(false)

    lateinit var listener: RecognitionListener
    // Set below: what to do when the service never answers (declared here, the watchdog in listen() needs it).
    var onNoAnswer: () -> Unit = {}

    fun recognizer(): SpeechRecognizer? {
        state.recognizer?.let { return it }
        val made = runCatching {
            when {
                state.level == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                state.google -> SpeechRecognizer.createSpeechRecognizer(context, GOOGLE_SERVICE)
                else -> SpeechRecognizer.createSpeechRecognizer(context)
            }
        }.getOrNull() ?: return null
        made.setRecognitionListener(listener)
        state.recognizer = made
        return made
    }

    fun listen() {
        val r = recognizer() ?: run { status(ListenStatus(ListenStatus.Event.ERROR, SpeechRecognizer.ERROR_CLIENT, service())); return }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, state.fallback ?: lang)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, state.fallback ?: lang)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .apply {
                if (state.level == 0) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, state.offline)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && words.isNotEmpty()) {
                        putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(words.take(50)))
                    }
                }
            }
        state.answered = false
        runCatching { r.startListening(intent) }
            .onFailure { status(ListenStatus(ListenStatus.Event.ERROR, SpeechRecognizer.ERROR_CLIENT, service())) }
        // No answer at all within eight seconds (a cold start can take a while): the service ignores this request; ask more plainly.
        state.watchdog?.let(handler::removeCallbacks)
        state.watchdog = Runnable { if (state.on && !state.answered) onNoAnswer() }.also { handler.postDelayed(it, 8_000) }
    }

    fun again(delayMs: Long) {
        if (state.on) handler.postDelayed({ if (state.on) listen() }, delayMs)
    }

    /** Start over with Google's service (a fresh recognizer, online first). */
    fun toGoogle() {
        if (!mayListen(GOOGLE_SERVICE.packageName)) {
            state.blocked = GOOGLE_SERVICE.packageName
            state.on = false
            quiet(false)
            active(false)
            status(ListenStatus(ListenStatus.Event.GAVE_UP, null, GOOGLE_SERVICE.packageName, "service-mic"))
            return
        }
        state.recognizer?.destroy()
        state.recognizer = null
        state.google = true
        state.offline = false
        state.failures = 0
        again(300)
    }

    /** The service never answered: next a plainer request, then Android's on-device recognizer, then give up. */
    fun noAnswer() {
        status(ListenStatus(ListenStatus.Event.ERROR, null, service(), "no-answer"))
        val next = state.level + 1
        val max = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) 2 else 1
        if (next > max) {
            // The next start tries from the top again.
            state.recognizer?.destroy()
            state.recognizer = null
            state.level = 0
            state.google = false
            state.offline = true
            state.on = false
            quiet(false)
            status(ListenStatus(ListenStatus.Event.GAVE_UP, null, service(), "no-answer"))
            active(false)
            return
        }
        runCatching { state.recognizer?.cancel() }
        state.recognizer?.destroy()
        state.recognizer = null
        state.level = next
        state.offline = false
        again(300)
    }

    onNoAnswer = ::noAnswer

    fun giveUp(code: Int) {
        state.on = false
        quiet(false)
        status(ListenStatus(ListenStatus.Event.GAVE_UP, code, service()))
        active(false)
    }

    listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            state.answered = true
            status(ListenStatus(ListenStatus.Event.READY, null, service()))
            // Ready, but the microphone stays silent (another app holds it, or the microphone switch in quick
            // settings is off): say so instead of waiting forever.
            if (state.silenceCheck == null && !state.spoke) {
                state.silenceCheck = Runnable {
                    state.silenceCheck = null
                    if (state.on && !state.spoke && state.loudest < 1f) status(ListenStatus(ListenStatus.Event.ERROR, null, service(), "silent"))
                }.also { handler.postDelayed(it, 10_000) }
            }
        }
        override fun onBeginningOfSpeech() {
            state.answered = true
            state.spoke = true
            status(ListenStatus(ListenStatus.Event.SPEECH, null, service()))
        }
        override fun onPartialResults(results: Bundle?) {
            state.answered = true
            state.spoke = true
            state.failures = 0
            state.empty = 0
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(partial)
        }
        override fun onResults(results: Bundle?) {
            state.answered = true
            state.failures = 0
            if (results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().any { it.isNotBlank() }) state.empty = 0
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { Spoken.pick(it, words, dutch = lang.startsWith("nl")) }?.let(line)
            partial("")
            again(100)
        }
        override fun onError(error: Int) {
            state.answered = true
            partial("")
            // A pause with nothing said is normal: listen again. Anything else counts.
            val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (!silence) {
                state.failures++
                status(ListenStatus(ListenStatus.Event.ERROR, error, service()))
            } else {
                state.empty++
                status(ListenStatus(ListenStatus.Event.QUIET, error, service(), level = state.loudest.takeIf { it > -100f }))
            }
            // Round after round without a word: some services answer "nothing understood" instead of saying the
            // language is missing. Ask another way: online, then nl-NL, then Android's on-device recognizer.
            if (silence && state.empty >= 3 && state.on) {
                state.empty = 0
                val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                when {
                    state.offline || state.level == 0 -> { state.offline = false; state.level = 1; again(100); return }
                    state.fallback == null && lang.startsWith("nl") -> { state.fallback = "nl-NL"; again(100); return }
                    state.level < 2 && onDevice -> {
                        state.recognizer?.destroy()
                        state.recognizer = null
                        state.level = 2
                        again(300)
                        return
                    }
                    // Tried every way: keep listening, the status line says the microphone hears nothing.
                    else -> if (state.loudest < 1f) status(ListenStatus(ListenStatus.Event.ERROR, null, service(), "silent"))
                }
            }
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
        override fun onRmsChanged(rmsdB: Float) {
            if (rmsdB > state.loudest) state.loudest = rmsdB
        }
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
        // The tablet's default speech app may not listen, Google's may: use that. Neither may: say so, with a button.
        if (!mayListen(servicePackage()) && !state.google && googleInstalled() && mayListen(GOOGLE_SERVICE.packageName)) {
            state.recognizer?.destroy()
            state.recognizer = null
            state.google = true
        }
        val pkg = servicePackage()
        if (!mayListen(pkg)) {
            state.blocked = pkg
            status(ListenStatus(ListenStatus.Event.GAVE_UP, null, pkg, "service-mic"))
            return
        }
        state.blocked = null
        state.failures = 0
        state.empty = 0
        state.loudest = -100f
        state.spoke = false
        state.silenceCheck = null
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
            fix = {
                // The speech app's own page: Machtigingen › Microfoon.
                val pkg = state.blocked ?: servicePackage() ?: GOOGLE_SERVICE.packageName
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
    }
}
