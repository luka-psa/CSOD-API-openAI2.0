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
    val state = remember { object { var on = false } }

    fun listen() {
        recognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
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
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(partial)
            }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { Spoken.pick(it, words, dutch = lang.startsWith("nl")) }?.let(line)
                partial("")
                again(100)
            }
            override fun onError(error: Int) {
                partial("")
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        state.on = false
                        active(false)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_SERVER -> again(1_500)
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
            state.on = false
            handler.removeCallbacksAndMessages(null)
            recognizer?.destroy()
        }
    }

    fun begin() {
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
                state.on = false
                handler.removeCallbacksAndMessages(null)
                recognizer?.stopListening()
                active(false)
            },
        )
    }
}
