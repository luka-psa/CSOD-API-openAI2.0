package app.hearth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.hearth.ai.llm.ProviderConfig
import app.hearth.ai.llm.ProviderKind
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.ai_cancel
import app.hearth.ui.resources.ai_helper_model
import app.hearth.ui.resources.ai_intro
import app.hearth.ui.resources.ai_key
import app.hearth.ui.resources.ai_key_note_android
import app.hearth.ui.resources.ai_key_note_web
import app.hearth.ui.resources.ai_off
import app.hearth.ui.resources.ai_router_model
import app.hearth.ui.resources.ai_save
import app.hearth.ui.resources.ai_test
import app.hearth.ui.resources.ai_test_ok
import app.hearth.ui.resources.ai_testing
import app.hearth.ui.resources.ai_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Choose the AI provider, paste a key, optionally change the two models, test, save. */
@Composable
fun SettingsDialog(
    current: ProviderConfig?,
    onTest: suspend (ProviderConfig) -> String?,
    onSave: (ProviderConfig?) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(current?.kind) }
    var key by remember { mutableStateOf(current?.apiKey.orEmpty()) }
    var router by remember { mutableStateOf(current?.routerModel.orEmpty()) }
    var helper by remember { mutableStateOf(current?.helperModel.orEmpty()) }
    var testState by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun select(k: ProviderKind?) {
        if (k == kind) return
        kind = k
        testState = null
        if (k != null) {
            router = ProviderConfig.defaultRouterModel(k)
            helper = ProviderConfig.defaultHelperModel(k)
            if (current?.kind == k) key = current.apiKey else key = ""
        }
    }

    val config = kind?.let { ProviderConfig(it, key.trim(), router.trim(), helper.trim()) }
    val testing = stringResource(Res.string.ai_testing)
    val ok = stringResource(Res.string.ai_test_ok)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 560.dp),
        title = { Text(stringResource(Res.string.ai_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.ai_intro), style = MaterialTheme.typography.bodyMedium)
                Choice(stringResource(Res.string.ai_off), kind == null) { select(null) }
                ProviderKind.entries.forEach { k -> Choice(k.label, kind == k) { select(k) } }
                if (kind != null) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it; testState = null },
                        label = { Text(stringResource(Res.string.ai_key)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(router, { router = it }, label = { Text(stringResource(Res.string.ai_router_model)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(helper, { helper = it }, label = { Text(stringResource(Res.string.ai_helper_model)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(if (isWebPlatform) Res.string.ai_key_note_web else Res.string.ai_key_note_android),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            enabled = key.isNotBlank() && testState != testing,
                            onClick = {
                                val c = config ?: return@OutlinedButton
                                testState = testing
                                scope.launch { testState = onTest(c) ?: ok }
                            },
                        ) { Text(stringResource(Res.string.ai_test)) }
                        testState?.let {
                            Text(it, color = if (it == ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = kind == null || key.isNotBlank(), onClick = { onSave(config); onDismiss() }) {
                Text(stringResource(Res.string.ai_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.ai_cancel)) } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
