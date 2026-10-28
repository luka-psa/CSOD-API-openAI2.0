package app.hearth.ui

import androidx.compose.runtime.Composable

/** Sends text somewhere else: the share sheet on Android, the clipboard (or share sheet) in a browser. */
@Composable
expect fun rememberShare(): (String) -> Unit

/** Puts text on the clipboard, to paste it in another app. */
@Composable
expect fun rememberCopy(): (String) -> Unit

/** Hands a file over: a download in a browser, the share sheet (save to Drive, mail, …) on Android. (name, JSON text). */
@Composable
expect fun rememberSaveFile(): (String, String) -> Unit
