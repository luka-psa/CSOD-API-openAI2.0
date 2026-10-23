@file:OptIn(ExperimentalWasmJsInterop::class)

package app.hearth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Uses the browser's Web Speech API (Chrome, Edge, Safari) with live, interim results. */
@Composable
actual fun VoiceButton(
    language: String,
    listening: Boolean,
    onListening: (Boolean) -> Unit,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
) {
    if (!remember { speechSupported() }) return
    MicButton(listening) {
        if (listening) {
            stopSpeech()
        } else {
            onListening(true)
            startSpeech(language, onPartial, onFinal) { onListening(false) }
        }
    }
}

private fun speechSupported(): Boolean =
    js("('SpeechRecognition' in globalThis) || ('webkitSpeechRecognition' in globalThis)")

private fun startSpeech(lang: String, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onEnd: () -> Unit): Unit = js(
    """{
        const Recognition = globalThis.SpeechRecognition || globalThis.webkitSpeechRecognition;
        const r = new Recognition();
        r.lang = lang;
        r.interimResults = true;
        r.continuous = false;
        r.maxAlternatives = 1;
        let text = '';
        r.onresult = (e) => {
            text = '';
            let final = false;
            for (let i = 0; i < e.results.length; i++) {
                text += e.results[i][0].transcript;
                final = e.results[i].isFinal;
            }
            if (final) onFinal(text.trim()); else onPartial(text.trim());
        };
        r.onend = () => { globalThis.__hearthSpeech = null; onEnd(); };
        r.onerror = () => {};
        globalThis.__hearthSpeech = r;
        r.start();
    }""",
)

private fun stopSpeech(): Unit = js("{ if (globalThis.__hearthSpeech) globalThis.__hearthSpeech.stop(); }")
