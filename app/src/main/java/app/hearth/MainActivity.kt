package app.hearth

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.hearth.kiosk.KioskController
import app.hearth.ui.HearthScreen
import app.hearth.ui.HomeViewModel
import app.hearth.ui.theme.HearthTheme

class MainActivity : ComponentActivity() {
    private val dimmed = mutableStateOf(false)
    private val kiosk = KioskController(this, onDimmedChanged = { dimmed.value = it })
    private val container get() = (application as HearthApp).container
    private val viewModel: HomeViewModel by viewModels {
        viewModelFactory { initializer { HomeViewModel(container) } }
    }

    /** Google's account and consent screen, started from a suspend call and answered here. */
    private var consentReply: CompletableDeferred<Intent?>? = null
    private val consent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        consentReply?.complete(if (result.resultCode == RESULT_OK) result.data else null)
        consentReply = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kiosk.onCreate()
        (application as HearthApp).googleAuth.consent = { sender ->
            val reply = CompletableDeferred<Intent?>()
            consentReply?.complete(null)
            consentReply = reply
            consent.launch(IntentSenderRequest.Builder(sender).build())
            reply.await()
        }
        setContent {
            HearthTheme {
                HearthScreen(viewModel = viewModel, dimmed = dimmed.value)
            }
        }
    }

    override fun onDestroy() {
        (application as HearthApp).googleAuth.consent = null
        consentReply?.complete(null)
        super.onDestroy()
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
