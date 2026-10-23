package app.hearth.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hearth.core.tools.system.BackupFile
import kotlinx.datetime.toLocalDateTime
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.google_backup
import app.hearth.ui.resources.google_backup_body
import app.hearth.ui.resources.google_backup_daily
import app.hearth.ui.resources.google_backup_last
import app.hearth.ui.resources.google_backup_never
import app.hearth.ui.resources.google_backup_now
import app.hearth.ui.resources.google_backup_on
import app.hearth.ui.resources.google_calendar
import app.hearth.ui.resources.google_calendar_body
import app.hearth.ui.resources.google_calendar_last
import app.hearth.ui.resources.google_calendar_on
import app.hearth.ui.resources.google_calendar_share
import app.hearth.ui.resources.google_calendar_sync
import app.hearth.ui.resources.google_calendar_follow
import app.hearth.ui.resources.google_not_here
import app.hearth.ui.resources.google_restore
import app.hearth.ui.resources.google_restore_body
import app.hearth.ui.resources.google_restore_confirm
import app.hearth.ui.resources.google_restore_empty
import app.hearth.ui.resources.google_restore_pick
import org.jetbrains.compose.resources.stringResource

class GoogleCallbacks(
    val connectBackup: () -> Unit,
    val backupNow: () -> Unit,
    val openBackups: () -> Unit,
    val closeBackups: () -> Unit,
    val restore: (id: String) -> Unit,
    val connectCalendar: () -> Unit,
    val syncCalendar: () -> Unit,
    val set: (backup: Boolean?, calendar: Boolean?) -> Unit,
)

/** Back-up to the family's own Google Drive and the plan in Google Calendar. Both opt-in. */
@Composable
fun GoogleCards(state: GoogleState, available: Boolean, busy: Boolean, backups: List<BackupFile>?, cb: GoogleCallbacks) {
    var restoring by remember { mutableStateOf<BackupFile?>(null) }

    HearthCard(stringResource(Res.string.google_backup), HearthIcons.Cloud, Modifier.fillMaxWidth(), action = {
        if (state.backup || state.lastBackup != null) Switch(checked = state.backup, onCheckedChange = { cb.set(it, null) }, enabled = available)
    }) {
        Text(stringResource(Res.string.google_backup_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(12.dp))
        when {
            !available -> Muted(stringResource(Res.string.google_not_here))
            !state.backup && state.lastBackup == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = cb.connectBackup, enabled = !busy) { Text(stringResource(Res.string.google_backup_on)) }
                Busy(busy)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = cb.openBackups, enabled = !busy) { Text(stringResource(Res.string.google_restore)) }
            }
            else -> {
                Muted(if (state.backup) stringResource(Res.string.google_backup_daily) else "")
                Muted(state.lastBackup?.let { stringResource(Res.string.google_backup_last, whenText(it)) } ?: stringResource(Res.string.google_backup_never))
                Spacer(Modifier.size(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = cb.backupNow, enabled = !busy) { Text(stringResource(Res.string.google_backup_now)) }
                    Busy(busy)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = cb.openBackups, enabled = !busy) { Text(stringResource(Res.string.google_restore)) }
                }
            }
        }
    }

    HearthCard(stringResource(Res.string.google_calendar), HearthIcons.Calendar, Modifier.fillMaxWidth(), action = {
        if (state.calendar || state.lastSync != null) Switch(checked = state.calendar, onCheckedChange = { cb.set(null, it) }, enabled = available)
    }) {
        Text(stringResource(Res.string.google_calendar_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(12.dp))
        when {
            !available -> Muted(stringResource(Res.string.google_not_here))
            !state.calendar && state.lastSync == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = cb.connectCalendar, enabled = !busy) { Text(stringResource(Res.string.google_calendar_on)) }
                Busy(busy)
            }
            else -> {
                if (state.calendar) Muted(stringResource(Res.string.google_calendar_follow))
                state.lastSync?.let { Muted(stringResource(Res.string.google_calendar_last, whenText(it))) }
                Muted(stringResource(Res.string.google_calendar_share))
                Spacer(Modifier.size(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = cb.syncCalendar, enabled = !busy && state.calendar) { Text(stringResource(Res.string.google_calendar_sync)) }
                    Busy(busy)
                }
            }
        }
    }

    if (backups != null) {
        AlertDialog(
            onDismissRequest = cb.closeBackups,
            title = { Text(stringResource(Res.string.google_restore_pick)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (backups.isEmpty()) Text(stringResource(Res.string.google_restore_empty))
                    backups.forEach { file ->
                        Text(
                            whenText(file.createdAt),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth().clickable { restoring = file }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = cb.closeBackups) { Text(stringResource(Res.string.cancel)) } },
        )
    }
    restoring?.let { file ->
        ConfirmDialog(
            stringResource(Res.string.google_restore_confirm),
            stringResource(Res.string.google_restore_body, whenText(file.createdAt)),
            onYes = { cb.restore(file.id) },
        ) { restoring = null }
    }
}

@Composable
private fun Muted(text: String) {
    if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Busy(busy: Boolean) {
    if (busy) {
        Spacer(Modifier.width(14.dp))
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
    }
}

/** "23/10 08:12": short, the same in both languages. */
@OptIn(kotlin.time.ExperimentalTime::class)
private fun whenText(millis: Long): String {
    val t = kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "${t.date.day}/${t.date.month.ordinal + 1} ${formatClock(t)}"
}
