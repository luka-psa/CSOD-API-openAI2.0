package app.hearth.ui

import androidx.compose.runtime.Composable

/**
 * Mic button. While listening, [onPartial] gets the words so far (shown live in the capture bar);
 * [onFinal] gets the finished sentence, which the person then confirms. Nothing is added by voice alone.
 * [language] is a BCP-47 tag ("nl-BE", "en-GB"). Renders nothing where speech isn't available.
 */
@Composable
expect fun VoiceButton(
    language: String,
    listening: Boolean,
    onListening: (Boolean) -> Unit,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
)
