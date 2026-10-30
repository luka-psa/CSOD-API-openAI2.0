package app.hearth.ui

import androidx.compose.runtime.Composable

/**
 * While [on], the screen stays on and bright: a meeting goes on without anyone touching the tablet, and so does writing
 * it down and reading it afterwards. A screen that dims or sleeps meanwhile stops the work (and a tap to wake it could
 * close what is open).
 */
@Composable
expect fun KeepScreenOn(on: Boolean)
