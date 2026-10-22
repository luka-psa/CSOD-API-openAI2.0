package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.chat_hint
import app.hearth.ui.resources.chat_welcome
import app.hearth.ui.resources.chip_list
import app.hearth.ui.resources.chip_milk
import app.hearth.ui.resources.send
import app.hearth.ui.resources.speak
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    busy: Boolean,
    speechLanguage: String,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val welcome = stringResource(Res.string.chat_welcome)
    val shown = remember(messages, welcome) { listOf(ChatMessage(false, welcome)) + messages }

    LaunchedEffect(shown.size) { listState.animateScrollToItem(shown.lastIndex) }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(HearthIcons.Flame, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(" Hearth", style = MaterialTheme.typography.titleLarge)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown) { Bubble(it) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()),
            ) {
                val list = stringResource(Res.string.chip_list)
                val milk = stringResource(Res.string.chip_milk)
                AssistChip(onClick = { onSend(list) }, label = { Text(list, fontSize = 16.sp) })
                AssistChip(onClick = { onSend(milk) }, label = { Text(milk, fontSize = 16.sp) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val send = {
                    onSend(input)
                    input = ""
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(Res.string.chat_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f),
                )
                VoiceButton(speechLanguage, onResult = onSend)
                FilledIconButton(onClick = send, enabled = input.isNotBlank(), modifier = Modifier.size(56.dp)) {
                    Icon(HearthIcons.Send, contentDescription = stringResource(Res.string.send))
                }
            }
        }
    }
}

@Composable
internal fun MicButton(onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(HearthIcons.Mic, contentDescription = stringResource(Res.string.speak))
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .background(
                    if (message.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
