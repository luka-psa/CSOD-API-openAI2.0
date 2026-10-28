package app.hearth.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

/**
 * Android's recognizer stops after every pause, so Luister mee starts it again right away until stopped.
 * It asks for on-device recognition where the language pack is installed, so the talk stays on the tablet,
 * leans towards the household's own words (Android 13+), and of its guesses keeps the one that names them.
 */
@Composable
actual fun rememberTranscriber(
    language: String,
    vocabulary: List<String>,
    onLine: (String) -> Unit,
    onPartial: (String) -> Unit,
    onActive: (Boolean) -> Unit,
): Transcriber {
    val context = LocalContext.current
    val line by rememberUpdatedState(onLine)
    val partial by rememberUpdatedState(onPartial)
    val active by rememberUpdatedState(onActive)
    val lang by rememberUpdatedState(language)
    val words by rememberUpdatedState(vocabulary)
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember { if (available) SpeechRecognizer.createSpeechRecognizer(context) else null }
    val handler = remember { Handler(Looper.getMainLooper()) }
    // Offline first; without the language pack (or without nl-BE) Android says so, and Hearth asks again online, in nl-NL.
    val state = remember { object { var on = false; var offline = true; var fallback: String? = null; var failures = 0 } }
    val audio = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager }

    // The recognizer beeps each time it starts listening again, after every pause; while Luister mee runs that is a
    // steady stream of beeps, so its sounds are muted until it stops.
    fun quiet(on: Boolean) {
        val how = if (on) android.media.AudioManager.ADJUST_MUTE else android.media.AudioManager.ADJUST_UNMUTE
        listOf(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.STREAM_SYSTEM)
            .forEach { stream -> runCatching { audio?.adjustStreamVolume(stream, how, 0) } }
    }

    fun listen() {
        recognizer?.startListening(
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
    }

    fun again(delayMs: Long) {
        if (state.on) handler.postDelayed({ if (state.on) listen() }, delayMs)
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(results: Bundle?) {
                state.failures = 0
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(partial)
            }
            override fun onResults(results: Bundle?) {
                state.failures = 0
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { Spoken.pick(it, words, dutch = lang.startsWith("nl")) }?.let(line)
                partial("")
                again(100)
            }
            override fun onError(error: Int) {
                partial("")
                // A pause with nothing said is normal: listen again. Anything else counts; six in a row and it stops,
                // so the mic shows off instead of restarting forever without hearing anything.
                val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (!silence) state.failures++
                when {
                    error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || state.failures >= 6 -> {
                        state.on = false
                        quiet(false)
                        active(false)
                    }
                    // 12: language not supported, 13: language pack not there. Online next, then plain Dutch.
                    (error == 12 || error == 13) && state.offline -> { state.offline = false; again(100) }
                    (error == 12 || error == 13) && state.fallback == null && lang.startsWith("nl") -> { state.fallback = "nl-NL"; again(100) }
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_SERVER -> again(1_500)
                    else -> again(200)
                }
            }
            override fun onEndOfSpeech() {}
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            if (state.on) quiet(false)
            state.on = false
            handler.removeCallbacksAndMessages(null)
            recognizer?.destroy()
        }
    }

    fun begin() {
        state.failures = 0
        if (!state.on) quiet(true)
        state.on = true
        active(true)
        listen()
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) begin() }
    return remember(recognizer) {
        Transcriber(
            available = recognizer != null,
            start = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                else askMic.launch(Manifest.permission.RECORD_AUDIO)
            },
            stop = {
                if (state.on) quiet(false)
                state.on = false
                handler.removeCallbacksAndMessages(null)
                recognizer?.stopListening()
                active(false)
            },
        )
    }
}
