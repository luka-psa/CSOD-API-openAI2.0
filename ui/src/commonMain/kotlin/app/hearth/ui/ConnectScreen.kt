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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.hearth.core.data.settings.Connection
import app.hearth.mcp.RelayStatus
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.connect_account_hint
import app.hearth.ui.resources.connect_activity
import app.hearth.ui.resources.connect_chatgpt
import app.hearth.ui.resources.connect_chatgpt_steps
import app.hearth.ui.resources.connect_claude
import app.hearth.ui.resources.connect_claude_steps
import app.hearth.ui.resources.connect_install
import app.hearth.ui.resources.connect_install_body
import app.hearth.ui.resources.connect_install_title
import app.hearth.ui.resources.connect_link
import app.hearth.ui.resources.connect_link_body
import app.hearth.ui.resources.connect_manual
import app.hearth.ui.resources.connect_manual_hint
import app.hearth.ui.resources.connect_new_link
import app.hearth.ui.resources.connect_new_link_body
import app.hearth.ui.resources.connect_new_link_confirm
import app.hearth.ui.resources.connect_on
import app.hearth.ui.resources.connect_plan_note
import app.hearth.ui.resources.connect_share
import app.hearth.ui.resources.connect_status_connecting
import app.hearth.ui.resources.connect_status_off
import app.hearth.ui.resources.connect_status_online
import app.hearth.ui.resources.connect_status_retrying
import app.hearth.ui.resources.connect_subtitle
import app.hearth.ui.resources.connect_token_hint
import app.hearth.ui.resources.connect_token_steps
import app.hearth.ui.resources.nav_connect
import app.hearth.ui.resources.save
import app.hearth.ui.theme.style
import org.jetbrains.compose.resources.stringResource

class ConnectCallbacks(
    val install: (token: String, accountId: String) -> Unit,
    val save: (enabled: Boolean?, relayUrl: String?, newLink: Boolean) -> Unit,
)

/**
 * Connect Hearth to the family's own ChatGPT or Claude. One time: install the relay on a free Cloudflare
 * account (or type its address). Then each person adds the link once in their AI app.
 */
@Composable
fun ConnectScreen(
    connection: Connection,
    status: RelayStatus,
    installing: Boolean,
    installError: String?,
    activity: List<String>,
    cb: ConnectCallbacks,
) {
    var renewing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(stringResource(Res.string.nav_connect), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(Res.string.connect_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(20.dp))

        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            HearthCard(stringResource(Res.string.connect_on), HearthIcons.Link, Modifier.fillMaxWidth(), action = {
                Switch(
                    checked = connection.enabled,
                    onCheckedChange = { cb.save(it, null, false) },
                    enabled = connection.relayUrl != null,
                )
            }) {
                StatusPill(if (connection.enabled) status else RelayStatus.OFF)
                if (activity.isNotEmpty()) {
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(Res.string.connect_activity), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    activity.reversed().forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            if (connection.relayUrl == null) {
                InstallCard(installing, installError, cb)
            } else {
                LinkCard(connection, cb, onRenew = { renewing = true })
            }
        }
    }
    if (renewing) {
        ConfirmDialog(stringResource(Res.string.connect_new_link_confirm), stringResource(Res.string.connect_new_link_body), onYes = { cb.save(null, null, true) }) { renewing = false }
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

/** One time: a Cloudflare API token, and Hearth installs the relay itself. Or type an address. */
@Composable
private fun InstallCard(installing: Boolean, error: String?, cb: ConnectCallbacks) {
    var token by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf("") }
    HearthCard(stringResource(Res.string.connect_install_title), HearthIcons.Flame, Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.connect_install_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(10.dp))
        Steps(stringResource(Res.string.connect_token_steps))
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(token, { token = it }, placeholder = { Text(stringResource(Res.string.connect_token_hint)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(account, { account = it }, placeholder = { Text(stringResource(Res.string.connect_account_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { cb.install(token, account) }, enabled = token.isNotBlank() && !installing) { Text(stringResource(Res.string.connect_install)) }
            if (installing) {
                Spacer(Modifier.width(14.dp))
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            }
        }
        if (error != null) {
            Spacer(Modifier.size(10.dp))
            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.size(18.dp))
        Text(stringResource(Res.string.connect_manual), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(manual, { manual = it }, placeholder = { Text(stringResource(Res.string.connect_manual_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(onClick = { cb.save(true, manual, false) }, enabled = manual.isNotBlank()) { Text(stringResource(Res.string.save)) }
        }
    }
}

/** The link to add once in ChatGPT or Claude, with the steps for each. */
@Composable
private fun LinkCard(connection: Connection, cb: ConnectCallbacks, onRenew: () -> Unit) {
    val share = rememberShare()
    val url = connection.connectorUrl ?: return
    HearthCard(stringResource(Res.string.connect_link), HearthIcons.Share, Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.connect_link_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(10.dp))
        SelectionContainer {
            Text(
                url,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp)).padding(14.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { share(url) }) {
                Icon(HearthIcons.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.connect_share))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRenew) { Text(stringResource(Res.string.connect_new_link)) }
        }
        Spacer(Modifier.size(16.dp))
        Text(stringResource(Res.string.connect_chatgpt), style = MaterialTheme.typography.titleSmall)
        Steps(stringResource(Res.string.connect_chatgpt_steps))
        Spacer(Modifier.size(12.dp))
        Text(stringResource(Res.string.connect_claude), style = MaterialTheme.typography.titleSmall)
        Steps(stringResource(Res.string.connect_claude_steps))
        Spacer(Modifier.size(12.dp))
        Text(stringResource(Res.string.connect_plan_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
