package app.hearth.ui

import androidx.compose.runtime.Composable

/** Luister mee: keeps listening through pauses until [stop]. [available] is false where speech isn't. */
class Transcriber(val available: Boolean, val start: () -> Unit, val stop: () -> Unit)

/**
 * What the speech recognizer is doing, shown small on the stage so a tablet that hears nothing says why: ready, hearing
 * speech, a quiet pause, an error (Android's error number, or the browser's error as [name]) or given up. [service] is
 * the recognition service in use (Android).
 */
data class ListenStatus(
    val event: Event,
    val code: Int? = null,
    val service: String? = null,
    val name: String? = null,
    /** The loudest the microphone was so far (Android's dB scale, about -2 silent to 10 loud); null: not known. */
    val level: Float? = null,
) {
    enum class Event { STARTING, READY, SPEECH, QUIET, ERROR, GAVE_UP, STOPPED }
}

/**
 * Listens for a whole conversation. Each stretch of talk ends up in [onLine] once the recognizer is sure
 * of it; [onPartial] shows the words being heard right now; [onActive] says when listening starts or ends; [onStatus] what the recognizer reports.
 * [language] is a BCP-47 tag ("nl-BE", "en-GB"). [vocabulary] is the household's own words (names, stock, recipes):
 * where the recognizer can, it leans towards them, and of its guesses the one that names them wins.
 */
@Composable
expect fun rememberTranscriber(
    language: String,
    vocabulary: List<String>,
    onLine: (String) -> Unit,
    onPartial: (String) -> Unit,
    onActive: (Boolean) -> Unit,
    onStatus: (ListenStatus) -> Unit,
): Transcriber
