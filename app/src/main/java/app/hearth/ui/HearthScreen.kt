package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HearthScreen(viewModel: HomeViewModel, dimmed: Boolean) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val shopping by viewModel.shopping.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChatPanel(
                messages = messages,
                busy = busy,
                speechLanguage = viewModel.speechLanguage,
                onSend = viewModel::send,
                modifier = Modifier.weight(0.36f).fillMaxHeight(),
            )
            Column(Modifier.weight(0.64f).fillMaxHeight()) {
                TodayHeader()
                Spacer(Modifier.height(12.dp))
                ShoppingCard(
                    sections = shopping,
                    onToggle = viewModel::toggleBought,
                    onAdd = viewModel::addFromCard,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (dimmed) AmbientOverlay()
    }
}

@Composable
private fun TodayHeader() {
    val now = rememberNow()
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.Bottom) {
        Text(stringResource(R.string.today), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.padding(horizontal = 8.dp))
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown while the screen is dimmed: just the time, readable from across the room. */
@Composable
private fun AmbientOverlay() {
    val now = rememberNow()
    Box(Modifier.fillMaxSize().background(Color(0xF0100C0A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color(0xFFE8D9C8), fontSize = 120.sp)
            Text(stringResource(R.string.tap_to_wake), color = Color(0x99E8D9C8), fontSize = 20.sp)
        }
    }
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = LocalDateTime.now()
        }
    }
    return now
}
