package app.hearth.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hearth.core.tools.system.JoinCodeResult
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.sync_add_device
import app.hearth.ui.resources.sync_body_off
import app.hearth.ui.resources.sync_body_on
import app.hearth.ui.resources.sync_busy
import app.hearth.ui.resources.sync_card
import app.hearth.ui.resources.sync_code_done
import app.hearth.ui.resources.sync_code_hint
import app.hearth.ui.resources.sync_code_title
import app.hearth.ui.resources.sync_code_until
import app.hearth.ui.resources.sync_join
import app.hearth.ui.resources.sync_join_go
import app.hearth.ui.resources.sync_join_hint
import app.hearth.ui.resources.sync_join_keep
import app.hearth.ui.resources.sync_join_keep_body
import app.hearth.ui.resources.sync_join_take
import app.hearth.ui.resources.sync_join_take_body
import app.hearth.ui.resources.sync_last
import app.hearth.ui.resources.sync_last_day
import app.hearth.ui.resources.sync_no_relay
import app.hearth.ui.resources.sync_now
import app.hearth.ui.resources.sync_share
import app.hearth.ui.resources.sync_stop
import app.hearth.ui.resources.sync_stop_body
import app.hearth.ui.resources.sync_stop_title
import app.hearth.ui.theme.AccentStyle
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

class SyncCallbacks(
    val start: () -> Unit,
    val share: () -> Unit,
    val hideCode: () -> Unit,
    val join: (code: String, keepThisDevice: Boolean) -> Unit,
    val syncNow: () -> Unit,
    val stop: () -> Unit,
)

/**
 * Samen op meer toestellen: the household on the tablet, the phones and an iPad, like git. One device shares, the
 * others join with a one-time code; after that it syncs by itself (after a change, every few minutes while Hearth
 * is open) and with the button. Everything is sealed on the device; the relay only sees a locked box.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncCard(state: SyncState, code: JoinCodeResult?, cb: SyncCallbacks) {
    var joining by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    HearthCard(stringResource(Res.string.sync_card), HearthIcons.Sync, Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.on) Res.string.sync_body_on else Res.string.sync_body_off), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(12.dp))
        if (state.on) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    state.busy -> {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.sync_busy), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Calm, never red: it simply tries again.
                    state.problem != null -> Text(state.problem, style = MaterialTheme.typography.labelLarge, color = AccentStyle.ink)
                    state.lastSync != null -> Text(lastSynced(state.lastSync), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = cb.syncNow, enabled = !state.busy) { Text(stringResource(Res.string.sync_now)) }
                FilledTonalButton(onClick = cb.share, enabled = !state.busy) { Text(stringResource(Res.string.sync_add_device)) }
                TextButton(onClick = { stopping = true }) { Text(stringResource(Res.string.sync_stop)) }
            }
        } else if (!state.available) {
            Text(stringResource(Res.string.sync_no_relay), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = cb.start, enabled = !state.busy) { Text(stringResource(Res.string.sync_share)) }
                FilledTonalButton(onClick = { joining = true }, enabled = !state.busy) { Text(stringResource(Res.string.sync_join)) }
                if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        if (code != null && state.on) {
            Spacer(Modifier.size(16.dp))
            Text(stringResource(Res.string.sync_code_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(code.shown, style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 4.sp, fontWeight = FontWeight.SemiBold), color = AccentStyle.ink)
            Text(stringResource(Res.string.sync_code_hint), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.sync_code_until, clockOf(code.expiresAt)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = cb.hideCode) { Text(stringResource(Res.string.sync_code_done)) }
        }
    }
    if (joining) JoinDialog(onJoin = { c, keep -> joining = false; cb.join(c, keep) }) { joining = false }
    if (stopping) ConfirmDialog(stringResource(Res.string.sync_stop_title), stringResource(Res.string.sync_stop_body), onYes = cb.stop) { stopping = false }
}

/**
 * The code, and which way: take the household over (recommended: what is on this device now goes) or add this
 * device's things to it. The choice says plainly what happens, so it is the confirmation too.
 */
@Composable
private fun JoinDialog(onJoin: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val clean = text.uppercase().filter { it in CODE_LETTERS }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sync_join)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.sync_join_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.uppercase().take(12) },
                    placeholder = { Text("ABCD-2345") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 3.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Choice(stringResource(Res.string.sync_join_take), stringResource(Res.string.sync_join_take_body), !keep) { keep = false }
                Choice(stringResource(Res.string.sync_join_keep), stringResource(Res.string.sync_join_keep_body), keep) { keep = true }
            }
        },
        confirmButton = { Button(onClick = { onJoin(clean, keep) }, enabled = clean.length == 8) { Text(stringResource(Res.string.sync_join_go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun Choice(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        RadioButton(selected, onClick)
        Column(Modifier.padding(top = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun lastSynced(millis: Long): String {
    val today = dateOf(app.hearth.core.model.nowMillis())
    val day = dateOf(millis)
    return if (day == today) stringResource(Res.string.sync_last, clockOf(millis))
    else stringResource(Res.string.sync_last_day, "${day.day}/${day.month.ordinal + 1}", clockOf(millis))
}

@OptIn(ExperimentalTime::class)
private fun clockOf(millis: Long): String =
    formatClock(kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()))

private const val CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
