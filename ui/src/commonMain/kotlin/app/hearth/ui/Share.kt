package app.hearth.ui

import androidx.compose.runtime.Composable

/** Sends text somewhere else: the share sheet on Android, the clipboard (or share sheet) in a browser. */
@Composable
expect fun rememberShare(): (String) -> Unit

/** Puts text on the clipboard, to paste it in another app. */
@Composable
expect fun rememberCopy(): (String) -> Unit
