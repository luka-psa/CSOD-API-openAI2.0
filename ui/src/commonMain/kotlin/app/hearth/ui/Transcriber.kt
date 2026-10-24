package app.hearth.ui

import androidx.compose.runtime.Composable

/** Luister mee: keeps listening through pauses until [stop]. [available] is false where speech isn't. */
class Transcriber(val available: Boolean, val start: () -> Unit, val stop: () -> Unit)

/**
 * Listens for a whole conversation. Each stretch of talk ends up in [onLine] once the recognizer is sure
 * of it; [onPartial] shows the words being heard right now; [onActive] says when listening starts or ends.
 * [language] is a BCP-47 tag ("nl-BE", "en-GB").
 */
@Composable
expect fun rememberTranscriber(
    language: String,
    onLine: (String) -> Unit,
    onPartial: (String) -> Unit,
    onActive: (Boolean) -> Unit,
): Transcriber
