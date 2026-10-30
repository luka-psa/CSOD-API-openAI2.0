package app.hearth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** The window keeps the screen on (charging or not), and the kiosk doesn't dim it ([ScreenAwake]). */
@Composable
actual fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        ScreenAwake.on = on
        onDispose {
            view.keepScreenOn = false
            ScreenAwake.on = false
        }
    }
}

/** Whether Hearth is busy with a meeting right now (listening, writing it down, reading it): the kiosk doesn't dim meanwhile. */
object ScreenAwake {
    @Volatile
    var on: Boolean = false
}
