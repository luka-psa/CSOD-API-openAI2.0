package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.core.model.Lang
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.tap_to_wake
import app.hearth.ui.resources.today
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * The home screen: chat beside today's dashboard on a tablet (or any wide window),
 * dashboard above chat on a phone-sized window.
 */
@Composable
fun HearthScreen(viewModel: HomeViewModel, lang: Lang, dimmed: Boolean) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val shopping by viewModel.shopping.collectAsStateWithLifecycle()

    val chat = @Composable { modifier: Modifier ->
        ChatPanel(messages, busy, viewModel.speechLanguage, viewModel::send, modifier)
    }
    val dashboard = @Composable { modifier: Modifier ->
        Column(modifier) {
            TodayHeader(lang)
            Spacer(Modifier.height(12.dp))
            ShoppingCard(shopping, viewModel::toggleBought, viewModel::addFromCard, Modifier.weight(1f))
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        if (maxWidth >= WIDE_LAYOUT) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                chat(Modifier.weight(0.36f).fillMaxHeight())
                dashboard(Modifier.weight(0.64f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                dashboard(Modifier.weight(0.5f).fillMaxWidth())
                chat(Modifier.weight(0.5f).fillMaxWidth())
            }
        }
        if (dimmed) AmbientOverlay()
    }
}

private val WIDE_LAYOUT = 840.dp

@Composable
private fun TodayHeader(lang: Lang) {
    val now = rememberNow()
    Row(verticalAlignment = Alignment.Bottom) {
        Text(stringResource(Res.string.today), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(16.dp))
        Text(
            formatDay(now, lang),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown while the tablet is dimmed: just the time, readable from across the room. */
@Composable
private fun AmbientOverlay() {
    val now = rememberNow()
    Box(Modifier.fillMaxSize().background(Color(0xF0100C0A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatClock(now), color = Color(0xFFE8D9C8), fontSize = 120.sp)
            Text(stringResource(Res.string.tap_to_wake), color = Color(0x99E8D9C8), fontSize = 20.sp)
        }
    }
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(localNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = localNow()
        }
    }
    return now
}
