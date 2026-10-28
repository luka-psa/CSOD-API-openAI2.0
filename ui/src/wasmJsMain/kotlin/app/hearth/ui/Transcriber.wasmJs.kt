@file:OptIn(ExperimentalWasmJsInterop::class)

package app.hearth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.hearth.ai.route.Spoken

/**
 * The browser's Web Speech API in continuous mode; it restarts by itself when the browser ends a session. It asks
 * for a few guesses per stretch and keeps the one with the household's own words; where the browser supports
 * phrase hints, it gets those words up front too.
 */
@Composable
actual fun rememberTranscriber(
    language: String,
    vocabulary: List<String>,
    onLine: (String) -> Unit,
    onPartial: (String) -> Unit,
    onActive: (Boolean) -> Unit,
): Transcriber {
    val line by rememberUpdatedState(onLine)
    val partial by rememberUpdatedState(onPartial)
    val active by rememberUpdatedState(onActive)
    val lang by rememberUpdatedState(language)
    val words by rememberUpdatedState(vocabulary)
    return remember {
        Transcriber(
            available = transcriptionSupported(),
            start = {
                active(true)
                startTranscribing(lang, words.joinToString(SEPARATOR), { guesses -> Spoken.pick(guesses.split(SEPARATOR), words, dutch = lang.startsWith("nl"))?.let(line) }, { partial(it) }) { active(false) }
            },
            stop = { stopTranscribing() },
        )
    }
}

private fun transcriptionSupported(): Boolean =
    js("('SpeechRecognition' in globalThis) || ('webkitSpeechRecognition' in globalThis)")

/** Between guesses and vocabulary words on their way through JavaScript. */
private const val SEPARATOR = "\u001F"

private fun startTranscribing(lang: String, vocabulary: String, onGuesses: (String) -> Unit, onPartial: (String) -> Unit, onEnd: () -> Unit): Unit = js(
    """{
        const Recognition = globalThis.SpeechRecognition || globalThis.webkitSpeechRecognition;
        const state = { on: true, phrases: true };
        globalThis.__hearthTranscriber = state;
        const words = vocabulary ? vocabulary.split('\u001F') : [];
        // Chrome on Android repeats the stretch so far in each result; there only its latest guess is shown live.
        const android = /Android/i.test(navigator.userAgent);
        const run = () => {
            const r = new Recognition();
            r.lang = lang;
            r.interimResults = true;
            r.continuous = true;
            r.maxAlternatives = 3;
            // Phrase hints, where the browser has them (a few words, a gentle boost).
            if (state.phrases && words.length && ('phrases' in r) && globalThis.SpeechRecognitionPhrase) {
                try { r.phrases = words.slice(0, 50).map((w) => new globalThis.SpeechRecognitionPhrase(w, 3.0)); } catch (_) { state.phrases = false; }
            }
            r.onresult = (e) => {
                let interim = '';
                for (let i = e.resultIndex; i < e.results.length; i++) {
                    const result = e.results[i];
                    if (result.isFinal) {
                        const guesses = [];
                        for (let j = 0; j < result.length; j++) { const t = result[j].transcript.trim(); if (t) guesses.push(t); }
                        if (guesses.length) onGuesses(guesses.join('\u001F'));
                    } else if (android) interim = result[0].transcript.trim();
                    else interim += (interim ? ' ' : '') + result[0].transcript.trim();
                }
                onPartial(interim);
            };
            r.onerror = (e) => {
                if (e && (e.error === 'not-allowed' || e.error === 'service-not-allowed')) state.on = false;
                // Without phrase support the next round starts without them.
                if (e && e.error === 'phrases-not-supported') state.phrases = false;
            };
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
