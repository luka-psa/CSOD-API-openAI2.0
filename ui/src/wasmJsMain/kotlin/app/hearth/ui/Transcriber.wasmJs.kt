@file:OptIn(ExperimentalWasmJsInterop::class)

package app.hearth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** The browser's Web Speech API in continuous mode; it restarts by itself when the browser ends a session. */
@Composable
actual fun rememberTranscriber(
    language: String,
    onLine: (String) -> Unit,
    onPartial: (String) -> Unit,
    onActive: (Boolean) -> Unit,
): Transcriber {
    val line by rememberUpdatedState(onLine)
    val partial by rememberUpdatedState(onPartial)
    val active by rememberUpdatedState(onActive)
    val lang by rememberUpdatedState(language)
    return remember {
        Transcriber(
            available = transcriptionSupported(),
            start = {
                active(true)
                startTranscribing(lang, { line(it) }, { partial(it) }) { active(false) }
            },
            stop = { stopTranscribing() },
        )
    }
}

private fun transcriptionSupported(): Boolean =
    js("('SpeechRecognition' in globalThis) || ('webkitSpeechRecognition' in globalThis)")

private fun startTranscribing(lang: String, onLine: (String) -> Unit, onPartial: (String) -> Unit, onEnd: () -> Unit): Unit = js(
    """{
        const Recognition = globalThis.SpeechRecognition || globalThis.webkitSpeechRecognition;
        const state = { on: true };
        globalThis.__hearthTranscriber = state;
        const run = () => {
            const r = new Recognition();
            r.lang = lang;
            r.interimResults = true;
            r.continuous = true;
            r.maxAlternatives = 1;
            r.onresult = (e) => {
                let interim = '';
                for (let i = e.resultIndex; i < e.results.length; i++) {
                    const text = e.results[i][0].transcript.trim();
                    if (e.results[i].isFinal) { if (text) onLine(text); } else interim += (interim ? ' ' : '') + text;
                }
                onPartial(interim);
            };
            r.onerror = (e) => { if (e && (e.error === 'not-allowed' || e.error === 'service-not-allowed')) state.on = false; };
            r.onend = () => {
                onPartial('');
                if (state.on) { try { run(); return; } catch (_) {} }
                globalThis.__hearthTranscriber = null;
                onEnd();
            };
            state.r = r;
            r.start();
        };
        run();
    }""",
)

private fun stopTranscribing(): Unit = js(
    "{ const s = globalThis.__hearthTranscriber; if (s) { s.on = false; if (s.r) s.r.stop(); } }",
)
