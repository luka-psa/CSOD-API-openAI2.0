package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hearth.core.data.settings.Connection
import app.hearth.core.model.nowMillis
import app.hearth.mcp.LinkedApp
import app.hearth.mcp.PairingCode
import app.hearth.mcp.RelayStatus
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.connect_activity
import app.hearth.ui.resources.connect_address
import app.hearth.ui.resources.connect_advanced
import app.hearth.ui.resources.connect_advanced_body
import app.hearth.ui.resources.connect_apps
import app.hearth.ui.resources.connect_chatgpt
import app.hearth.ui.resources.connect_chatgpt_steps
import app.hearth.ui.resources.connect_claude
import app.hearth.ui.resources.connect_claude_steps
import app.hearth.ui.resources.connect_code_hide
import app.hearth.ui.resources.connect_code_valid
import app.hearth.ui.resources.connect_link
import app.hearth.ui.resources.connect_link_body
import app.hearth.ui.resources.connect_manual_hint
import app.hearth.ui.resources.connect_no_relay
import app.hearth.ui.resources.connect_no_relay_body
import app.hearth.ui.resources.connect_on
import app.hearth.ui.resources.connect_plan_note
import app.hearth.ui.resources.connect_share
import app.hearth.ui.resources.connect_show_code
import app.hearth.ui.resources.connect_since
import app.hearth.ui.resources.connect_standard_relay
import app.hearth.ui.resources.connect_status_connecting
import app.hearth.ui.resources.connect_status_off
import app.hearth.ui.resources.connect_status_online
import app.hearth.ui.resources.connect_status_retrying
import app.hearth.ui.resources.connect_subtitle
import app.hearth.ui.resources.connect_unlink_body
import app.hearth.ui.resources.connect_unlink_confirm
import app.hearth.ui.resources.nav_connect
import app.hearth.ui.resources.save
import app.hearth.ui.theme.style
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

class ConnectCallbacks(
    val save: (enabled: Boolean?, relayUrl: String?) -> Unit,
    val showCode: () -> Unit,
    val hideCode: () -> Unit,
    val unlink: (appId: String) -> Unit,
)

/**
 * Connect Hearth to the family's own ChatGPT or Claude. Each person adds the same Hearth address once
 * in their AI app, which opens a Hearth page; they type the code this tablet shows, and that's it.
 */
@Composable
fun ConnectScreen(
    connection: Connection,
    status: RelayStatus,
    apps: List<LinkedApp>,
    code: PairingCode?,
    activity: List<String>,
    cb: ConnectCallbacks,
) {
    var unlinking by remember { mutableStateOf<LinkedApp?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(stringResource(Res.string.nav_connect), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(Res.string.connect_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(20.dp))

        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            HearthCard(stringResource(Res.string.connect_on), HearthIcons.Link, Modifier.fillMaxWidth(), action = {
                Switch(
                    checked = connection.enabled,
                    onCheckedChange = { cb.save(it, null) },
                    enabled = connection.relayUrl != null,
                )
            }) {
                StatusPill(if (connection.enabled) status else RelayStatus.OFF)
                if (connection.enabled && apps.isNotEmpty()) {
                    Spacer(Modifier.size(14.dp))
                    Text(stringResource(Res.string.connect_apps), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    apps.forEach { app ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(app.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(stringResource(Res.string.connect_since, shortDate(app.since)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DeleteButton(onClick = { unlinking = app })
                        }
                    }
                }
                if (activity.isNotEmpty()) {
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(Res.string.connect_activity), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    activity.reversed().forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            if (connection.relayUrl == null) {
                HearthCard(stringResource(Res.string.connect_no_relay), HearthIcons.Flame, Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.connect_no_relay_body), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LinkCard(connection, status, code, cb)
            }
            AdvancedCard(connection, cb)
        }
    }
    unlinking?.let { app ->
        ConfirmDialog(
            stringResource(Res.string.connect_unlink_confirm, app.name),
            stringResource(Res.string.connect_unlink_body, app.name),
            onYes = { cb.unlink(app.id) },
        ) { unlinking = null }
    }
}

@Composable
private fun StatusPill(status: RelayStatus) {
    val (label, color) = when (status) {
        RelayStatus.OFF -> stringResource(Res.string.connect_status_off) to MaterialTheme.colorScheme.onSurfaceVariant
        RelayStatus.CONNECTING -> stringResource(Res.string.connect_status_connecting) to MaterialTheme.colorScheme.primary
        RelayStatus.ONLINE -> stringResource(Res.string.connect_status_online) to MaterialTheme.colorScheme.secondary
        RelayStatus.RETRYING -> stringResource(Res.string.connect_status_retrying) to MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

/** The one Hearth address, the pairing code, and the steps for each AI app. */
@Composable
private fun LinkCard(connection: Connection, status: RelayStatus, code: PairingCode?, cb: ConnectCallbacks) {
    val share = rememberShare()
    val url = connection.connectorUrl ?: return
    HearthCard(stringResource(Res.string.connect_link), HearthIcons.Share, Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.connect_link_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    url,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp)).padding(14.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(onClick = { share(url) }) {
                Icon(HearthIcons.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.connect_share))
            }
        }
        Spacer(Modifier.size(16.dp))
        if (code != null && code.expiresAt > nowMillis()) {
            CodeBox(code, cb.hideCode)
        } else {
            Button(
                onClick = cb.showCode,
                enabled = connection.enabled && status == RelayStatus.ONLINE,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.connect_show_code), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 6.dp)) }
        }
        Spacer(Modifier.size(18.dp))
        Text(stringResource(Res.string.connect_claude), style = MaterialTheme.typography.titleSmall)
        Steps(stringResource(Res.string.connect_claude_steps))
        Spacer(Modifier.size(12.dp))
        Text(stringResource(Res.string.connect_chatgpt), style = MaterialTheme.typography.titleSmall)
        Steps(stringResource(Res.string.connect_chatgpt_steps))
        Spacer(Modifier.size(12.dp))
        Text(stringResource(Res.string.connect_plan_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The pairing code, big enough to read from the sofa, with how long it still works. */
@Composable
private fun CodeBox(code: PairingCode, onHide: () -> Unit) {
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(code) {
        while (now < code.expiresAt) {
            delay(5_000)
            now = nowMillis()
        }
    }
    if (now >= code.expiresAt) return
    val minutes = ((code.expiresAt - now + 59_999) / 60_000).toInt()
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(22.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(code.spaced, fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(Res.string.connect_code_valid, minutes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onHide) { Text(stringResource(Res.string.connect_code_hide)) }
    }
}

/** Another relay than the built-in one (self-hosting). Most families never open this. */
@Composable
private fun AdvancedCard(connection: Connection, cb: ConnectCallbacks) {
    var open by rememberSaveable { mutableStateOf(connection.relayUrl == null) }
    var manual by rememberSaveable { mutableStateOf("") }
    if (!open) {
        TextButton(onClick = { open = true }) { Text(stringResource(Res.string.connect_advanced)) }
        return
    }
    HearthCard(stringResource(Res.string.connect_advanced), HearthIcons.Flame, Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.connect_advanced_body), style = MaterialTheme.typography.bodyMedium)
        connection.relayUrl?.let {
            Spacer(Modifier.size(6.dp))
            Text(stringResource(Res.string.connect_address, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(manual, { manual = it }, placeholder = { Text(stringResource(Res.string.connect_manual_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(onClick = { cb.save(true, manual); manual = "" }, enabled = manual.isNotBlank()) { Text(stringResource(Res.string.save)) }
        }
        if (connection.customRelay) {
            TextButton(onClick = { cb.save(null, "") }) { Text(stringResource(Res.string.connect_standard_relay)) }
        }
    }
}

/** "1. … | 2. …" as numbered lines. */
@Composable
private fun Steps(text: String) {
    val s = app.hearth.core.model.Section.NOTE.style()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        text.split("|").map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(24.dp).background(s.tint, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = s.ink)
                }
                Spacer(Modifier.width(10.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** "23/10" — small and the same in both languages. */
private fun shortDate(millis: Long): String {
    val d = dateOf(millis)
    return "${d.day}/${d.month.ordinal + 1}"
}
