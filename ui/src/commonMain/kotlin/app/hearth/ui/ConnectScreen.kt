package app.hearth.ui

import app.hearth.ai.interpret.Interpreting
import app.hearth.ai.sense.SmartListening
import app.hearth.ui.resources.smart_title
import app.hearth.ui.resources.smart_card
import app.hearth.ui.resources.smart_body
import app.hearth.ui.resources.smart_downloading
import app.hearth.ui.resources.smart_loading
import app.hearth.ui.resources.smart_ready
import app.hearth.ui.resources.smart_failed
import app.hearth.ui.resources.smart_retry

import androidx.compose.ui.platform.LocalUriHandler
import app.hearth.ui.resources.connect_step_done
import app.hearth.ui.resources.connect_step_code
import app.hearth.ui.resources.connect_chatgpt_how
import app.hearth.ui.resources.connect_claude_how
import app.hearth.ui.resources.connect_open_chatgpt
import app.hearth.ui.resources.connect_open_claude
import app.hearth.ui.resources.connect_step_open
import app.hearth.ui.resources.connect_copied
import app.hearth.ui.resources.connect_copy
import app.hearth.ui.resources.connect_step_copy
import app.hearth.ui.resources.connect_share_text
import app.hearth.ui.resources.settings_language_card
import app.hearth.ui.resources.settings_language
import app.hearth.core.model.Lang
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hearth.core.data.settings.Connection
import app.hearth.core.model.nowMillis
import app.hearth.core.tools.system.BackupFile
import app.hearth.mcp.LinkedApp
import app.hearth.mcp.PairingCode
import app.hearth.mcp.RelayStatus
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.sync_title
import app.hearth.ui.resources.connect_activity
import app.hearth.ui.resources.connect_ai
import app.hearth.ui.resources.connect_google
import app.hearth.ui.resources.examples_body
import app.hearth.ui.resources.examples_card
import app.hearth.ui.resources.examples_load
import app.hearth.ui.resources.examples_title
import app.hearth.ui.resources.connect_apps
import app.hearth.ui.resources.connect_code_hide
import app.hearth.ui.resources.connect_code_valid
import app.hearth.ui.resources.connect_link
import app.hearth.ui.resources.connect_link_body
import app.hearth.ui.resources.connect_no_relay
import app.hearth.ui.resources.connect_no_relay_body
import app.hearth.ui.resources.connect_on
import app.hearth.ui.resources.connect_plan_note
import app.hearth.ui.resources.connect_share
import app.hearth.ui.resources.connect_show_code
import app.hearth.ui.resources.connect_since
import app.hearth.ui.resources.connect_status_connecting
import app.hearth.ui.resources.connect_status_off
import app.hearth.ui.resources.connect_status_online
import app.hearth.ui.resources.connect_status_retrying
import app.hearth.ui.resources.connect_subtitle
import app.hearth.ui.resources.connect_unlink_body
import app.hearth.ui.resources.connect_unlink_confirm
import app.hearth.ui.resources.interpreter_body
import app.hearth.ui.resources.interpreter_card
import app.hearth.ui.resources.interpreter_loading
import app.hearth.ui.resources.interpreter_ready
import app.hearth.ui.resources.nav_connect
import app.hearth.ui.theme.style
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

class ConnectCallbacks(
    val save: (enabled: Boolean) -> Unit,
    val showCode: () -> Unit,
    val hideCode: () -> Unit,
    val unlink: (appId: String) -> Unit,
    /** Null when examples aren't offered here (the web preview starts with them). */
    val loadExamples: (() -> Unit)? = null,
    val setLanguage: (Lang) -> Unit = {},
    /** Smarter listening on or off on this device. */
    val setSmartListening: (Boolean) -> Unit = {},
    val retrySmartListening: () -> Unit = {},
    /** Understanding conversations on or off on this device. */
    val setInterpreter: (Boolean) -> Unit = {},
    val retryInterpreter: () -> Unit = {},
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
    google: GoogleState,
    googleAvailable: Boolean,
    googleBusy: Boolean,
    backups: List<BackupFile>?,
    googleCb: GoogleCallbacks,
    sync: SyncState = SyncState(),
    joinCode: app.hearth.core.tools.system.JoinCodeResult? = null,
    syncCb: SyncCallbacks? = null,
    listening: SmartListening.State = SmartListening.State.Off,
    listeningAvailable: Boolean = false,
    interpreter: Interpreting.State = Interpreting.State.Off,
    interpreterAvailable: Boolean = false,
) {
    var unlinking by remember { mutableStateOf<LinkedApp?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(stringResource(Res.string.nav_connect), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(Res.string.connect_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(20.dp))

        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Dutch (Flemish) by default; each option is written in its own language.
            SectionTitle(stringResource(Res.string.settings_language))
            HearthCard(stringResource(Res.string.settings_language_card), HearthIcons.Chat, Modifier.fillMaxWidth()) {
                val current = LocalLang.current
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceChip("Nederlands (Vlaams)", current == Lang.NL, { cb.setLanguage(Lang.NL) })
                    ChoiceChip("English", current == Lang.EN, { cb.setLanguage(Lang.EN) })
                }
            }
            if (listeningAvailable || interpreterAvailable) SectionTitle(stringResource(Res.string.smart_title))
            if (listeningAvailable) SmartListeningCard(listening, cb)
            if (interpreterAvailable) InterpreterCard(interpreter, cb)
            // The household on more devices: right after the language, it's what a new device needs first.
            if (syncCb != null) {
                SectionTitle(stringResource(Res.string.sync_title))
                SyncCard(sync, joinCode, syncCb)
            }
            SectionTitle(stringResource(Res.string.connect_ai))
            HearthCard(stringResource(Res.string.connect_on), HearthIcons.Link, Modifier.fillMaxWidth(), action = {
                Switch(
                    checked = connection.enabled,
                    onCheckedChange = { cb.save(it) },
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
                HearthCard(stringResource(Res.string.connect_no_relay), HearthIcons.Link, Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.connect_no_relay_body), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LinkCard(connection, status, code, cb)
            }
            SectionTitle(stringResource(Res.string.connect_google))
            GoogleCards(google, googleAvailable, googleBusy, backups, googleCb)
            cb.loadExamples?.let { load ->
                SectionTitle(stringResource(Res.string.examples_title))
                HearthCard(stringResource(Res.string.examples_card), HearthIcons.Next, Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.examples_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(10.dp))
                    FilledTonalButton(onClick = load) { Text(stringResource(Res.string.examples_load)) }
                }
            }
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
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
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

/**
 * Linking ChatGPT or Claude, as four plain steps: copy the address (or share it to your phone), open your AI app on
 * its connector page and add it, type the code this tablet shows, done. One tap without pasting only exists for
 * apps listed in Claude's and ChatGPT's own directories.
 */
@Composable
private fun LinkCard(connection: Connection, status: RelayStatus, code: PairingCode?, cb: ConnectCallbacks) {
    val share = rememberShare()
    val copy = rememberCopy()
    val links = LocalUriHandler.current
    val url = connection.connectorUrl ?: return
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2_500); copied = false } }
    val shareText = stringResource(Res.string.connect_share_text, url, CLAUDE_CONNECTORS, CHATGPT_CONNECTORS)
    HearthCard(stringResource(Res.string.connect_link), HearthIcons.Share, Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.connect_link_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(14.dp))

        LinkStep(1, stringResource(Res.string.connect_step_copy)) {
            SelectionContainer(Modifier.fillMaxWidth()) {
                Text(
                    url,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp)).padding(14.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilledTonalButton(onClick = { copy(url); copied = true }) { Text(stringResource(if (copied) Res.string.connect_copied else Res.string.connect_copy)) }
                TextButton(onClick = { share(shareText) }) {
                    Icon(HearthIcons.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.connect_share))
                }
            }
        }
        LinkStep(2, stringResource(Res.string.connect_step_open)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { runCatching { links.openUri(CLAUDE_CONNECTORS) } }) { Text(stringResource(Res.string.connect_open_claude)) }
                FilledTonalButton(onClick = { runCatching { links.openUri(CHATGPT_CONNECTORS) } }) { Text(stringResource(Res.string.connect_open_chatgpt)) }
            }
            Text(stringResource(Res.string.connect_claude_how), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(Res.string.connect_chatgpt_how), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        LinkStep(3, stringResource(Res.string.connect_step_code)) {
            if (code != null && code.expiresAt > nowMillis()) {
                CodeBox(code, cb.hideCode)
            } else {
                // Switches the connection on when it is off; the code comes as soon as the tablet is online.
                Button(
                    onClick = cb.showCode,
                    enabled = !connection.enabled || status == RelayStatus.ONLINE || status == RelayStatus.RETRYING,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(if (connection.enabled && status == RelayStatus.CONNECTING) Res.string.connect_status_connecting else Res.string.connect_show_code),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }
        LinkStep(4, stringResource(Res.string.connect_step_done), last = true) {}
        Spacer(Modifier.size(6.dp))
        Text(stringResource(Res.string.connect_plan_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One numbered step: a small round number, what to do, and its buttons under it. */
@Composable
private fun LinkStep(n: Int, title: String, last: Boolean = false, content: @Composable () -> Unit) {
    val s = app.hearth.core.model.Section.NOTE.style()
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else 14.dp)) {
        Box(Modifier.size(28.dp).background(s.tint, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = s.ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            content()
        }
    }
}

/** Where each app adds a custom connector: Claude's connector page, ChatGPT's Developer mode switch. */
private const val CLAUDE_CONNECTORS = "https://claude.ai/customize/connectors"
private const val CHATGPT_CONNECTORS = "https://chatgpt.com/#settings/Connectors/Advanced"

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

/** "23/10" — small and the same in both languages. */
private fun shortDate(millis: Long): String {
    val d = dateOf(millis)
    return "${d.day}/${d.month.ordinal + 1}"
}

/**
 * Slimmer luisteren: a small sentence model on this device reads Luister mee next to the rules. Off by default; on, it
 * downloads once (with its progress), learns its examples, and says when it's ready. Off removes it again.
 */
@Composable
private fun SmartListeningCard(state: SmartListening.State, cb: ConnectCallbacks) {
    val on = state != SmartListening.State.Off
    HearthCard(stringResource(Res.string.smart_card), HearthIcons.Mic, Modifier.fillMaxWidth(), action = {
        Switch(checked = on, onCheckedChange = { cb.setSmartListening(it) })
    }) {
        Text(stringResource(Res.string.smart_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (state) {
            SmartListening.State.Off -> {}
            is SmartListening.State.Downloading -> {
                Spacer(Modifier.size(12.dp))
                Text(stringResource(Res.string.smart_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(6.dp))
                androidx.compose.material3.LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
            }
            SmartListening.State.Loading -> {
                Spacer(Modifier.size(12.dp))
                Text(stringResource(Res.string.smart_loading), style = MaterialTheme.typography.labelLarge)
            }
            SmartListening.State.Ready -> {
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(HearthIcons.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.smart_ready), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
            }
            is SmartListening.State.Failed -> {
                Spacer(Modifier.size(12.dp))
                Text(stringResource(Res.string.smart_failed, state.reason), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = cb.retrySmartListening) { Text(stringResource(Res.string.smart_retry)) }
            }
        }
    }
}

/**
 * Gesprekken begrijpen: a small language model on this device reads the whole weekoverleg (what came back, what changed)
 * and names what was decided. Off by default; on, it downloads once and says when it's ready. Off removes it again.
 */
@Composable
private fun InterpreterCard(state: Interpreting.State, cb: ConnectCallbacks) {
    HearthCard(stringResource(Res.string.interpreter_card), HearthIcons.Chat, Modifier.fillMaxWidth(), action = {
        Switch(checked = state != Interpreting.State.Off, onCheckedChange = { cb.setInterpreter(it) })
    }) {
        Text(stringResource(Res.string.interpreter_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (state) {
            Interpreting.State.Off -> {}
            is Interpreting.State.Downloading -> {
                Spacer(Modifier.size(12.dp))
                Text(stringResource(Res.string.smart_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(6.dp))
                androidx.compose.material3.LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
            }
            Interpreting.State.Loading -> {
                Spacer(Modifier.size(12.dp))
                Text(stringResource(Res.string.interpreter_loading), style = MaterialTheme.typography.labelLarge)
            }
            Interpreting.State.Ready -> {
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(HearthIcons.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.interpreter_ready), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
            }
            is Interpreting.State.Failed -> {
                Spacer(Modifier.size(12.dp))
                Text(stringResource(Res.string.smart_failed, state.reason), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = cb.retryInterpreter) { Text(stringResource(Res.string.smart_retry)) }
            }
        }
    }
}
