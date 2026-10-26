package app.hearth.ui

import androidx.compose.runtime.Composable

/** Luister mee: keeps listening through pauses until [stop]. [available] is false where speech isn't. */
class Transcriber(val available: Boolean, val start: () -> Unit, val stop: () -> Unit)

/**
 * Listens for a whole conversation. Each stretch of talk ends up in [onLine] once the recognizer is sure
 * of it; [onPartial] shows the words being heard right now; [onActive] says when listening starts or ends.
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
): Transcriber
