@file:OptIn(ExperimentalWasmJsInterop::class)

package app.hearth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Uses the browser's Web Speech API (Chrome, Edge, Safari). */
@Composable
actual fun VoiceButton(language: String, onResult: (String) -> Unit) {
    if (!remember { speechSupported() }) return
    MicButton { startSpeech(language) { onResult(it) } }
}

private fun speechSupported(): Boolean =
    js("('SpeechRecognition' in globalThis) || ('webkitSpeechRecognition' in globalThis)")

private fun startSpeech(lang: String, onText: (String) -> Unit): Unit = js(
    """{
        const Recognition = globalThis.SpeechRecognition || globalThis.webkitSpeechRecognition;
        const r = new Recognition();
        r.lang = lang;
        r.interimResults = false;
        r.maxAlternatives = 1;
        r.onresult = (e) => onText(e.results[0][0].transcript);
        r.start();
    }""",
)
