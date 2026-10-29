package app.hearth.kiosk

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Makes the activity behave like a wall display: full screen, visible over the lock screen,
 * awake while charging (and while Luister mee listens), and dimmed after a quiet spell, never during Luister mee.
 * Any touch brings it back.
 */
class KioskController(
    private val activity: Activity,
    private val onDimmedChanged: (Boolean) -> Unit,
    private val idleMillis: Long = 2 * 60_000L,
) {
    private val handler = Handler(Looper.getMainLooper())
    // Not while Luister mee listens: a meeting goes on without anyone touching the screen. Try again later.
    private val dimRunnable: Runnable = object : Runnable {
        override fun run() {
            if (app.hearth.ui.ListeningNow.on) handler.postDelayed(this, idleMillis) else setDimmed(true)
        }
    }
    var dimmed = false
        private set

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = applyCharging(intent)
    }

    fun onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }

    fun onResume() {
        hideSystemBars()
        activity.registerReceiver(powerReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::applyCharging)
        userActivity()
    }

    fun onPause() {
        activity.unregisterReceiver(powerReceiver)
        handler.removeCallbacks(dimRunnable)
    }

    /** Call on every touch. Returns true when the touch only woke the screen and should be swallowed. */
    fun userActivity(): Boolean {
        val wasDimmed = dimmed
        if (wasDimmed) setDimmed(false)
        handler.removeCallbacks(dimRunnable)
        handler.postDelayed(dimRunnable, idleMillis)
        return wasDimmed
    }

    fun hideSystemBars() {
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun applyCharging(intent: Intent) {
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (plugged) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setDimmed(value: Boolean) {
        dimmed = value
        val attrs = activity.window.attributes
        attrs.screenBrightness = if (value) DIM_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = attrs
        onDimmedChanged(value)
    }

    private companion object {
        const val DIM_BRIGHTNESS = 0.03f
    }
}
