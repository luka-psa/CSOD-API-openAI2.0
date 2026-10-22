package app.hearth.ui

import androidx.compose.runtime.Composable

/** Mic button that turns speech into a chat message. Renders nothing where speech isn't available. */
@Composable
expect fun VoiceButton(language: String, onResult: (String) -> Unit)
