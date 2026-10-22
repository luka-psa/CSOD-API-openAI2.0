package app.hearth

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import app.hearth.kiosk.KioskController
import app.hearth.ui.HearthScreen
import app.hearth.ui.HomeViewModel
import app.hearth.ui.theme.HearthTheme

class MainActivity : ComponentActivity() {
    private val dimmed = mutableStateOf(false)
    private val kiosk = KioskController(this, onDimmedChanged = { dimmed.value = it })
    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.factory((application as HearthApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kiosk.onCreate()
        setContent {
            HearthTheme {
                HearthScreen(viewModel = viewModel, dimmed = dimmed.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        kiosk.onResume()
    }

    override fun onPause() {
        kiosk.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) kiosk.hideSystemBars()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && kiosk.userActivity()) {
            swallowGesture = true
        }
        if (swallowGesture) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                swallowGesture = false
            }
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    /** The touch that wakes a dimmed screen must not also tick an item. */
    private var swallowGesture = false
}
