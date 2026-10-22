package app.hearth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.capture_hint
import app.hearth.ui.resources.chat_welcome
import app.hearth.ui.resources.close
import app.hearth.ui.resources.conversation
import app.hearth.ui.resources.no
import app.hearth.ui.resources.send
import app.hearth.ui.resources.speak
import app.hearth.ui.resources.yes
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Always at the bottom of every screen: say or type anything. The latest answer floats above it
 * (with Ja / Nee when something needs confirming); the full conversation opens in a sheet.
 */
@Composable
fun CaptureBar(
    messages: List<ChatMessage>,
    busy: Boolean,
    speechLanguage: String,
    onSend: (String) -> Unit,
    onAnswer: (String, Boolean) -> Unit,
    onOpenConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val last = messages.lastOrNull()?.takeIf { !it.fromUser }
    var showReply by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        showReply = last != null
        if (last != null && last.pending == null) {
            delay(9_000)
            showReply = false
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(showReply && last != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
            if (last != null) ReplyCard(last, onAnswer, onClose = { showReply = false }, onOpenConversation = onOpenConversation)
        }
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
            Column {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                    IconButton(onClick = onOpenConversation) {
                        Icon(HearthIcons.Chat, stringResource(Res.string.conversation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val send = {
                        onSend(input)
                        input = ""
                    }
                    Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                        if (input.isEmpty()) {
                            Text(
                                stringResource(Res.string.capture_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 12.dp),
                        )
                    }
                    VoiceButton(speechLanguage, onResult = onSend)
                    Spacer(Modifier.size(6.dp))
                    FilledIconButton(
                        onClick = send,
                        enabled = input.isNotBlank(),
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onBackground),
                    ) { Icon(HearthIcons.Send, stringResource(Res.string.send), modifier = Modifier.size(22.dp)) }
                }
            }
        }
    }
}

@Composable
internal fun MicButton(onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(HearthIcons.Mic, contentDescription = stringResource(Res.string.speak))
    }
}

@Composable
private fun ReplyCard(message: ChatMessage, onAnswer: (String, Boolean) -> Unit, onClose: () -> Unit, onOpenConversation: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.onBackground, shadowElevation = 6.dp) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(HearthIcons.Flame, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp).size(18.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f).padding(top = 10.dp),
                )
                IconButton(onClick = onClose) {
                    Icon(HearthIcons.Close, stringResource(Res.string.close), tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                }
            }
            if (message.pending != null) {
                val yes = stringResource(Res.string.yes)
                val no = stringResource(Res.string.no)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 28.dp, top = 10.dp)) {
                    Button(onClick = { onAnswer(yes, true) }) { Text(yes) }
                    OutlinedButton(onClick = { onAnswer(no, false) }) { Text(no, color = MaterialTheme.colorScheme.surface) }
                }
            }
        }
    }
}

/** The whole conversation, newest at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationSheet(messages: List<ChatMessage>, onAnswer: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val welcome = stringResource(Res.string.chat_welcome)
    val shown = remember(messages, welcome) { listOf(ChatMessage(false, welcome)) + messages }
    val listState = rememberLazyListState()
    LaunchedEffect(shown.size) { listState.scrollToItem(shown.lastIndex) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding()) {
            Text(stringResource(Res.string.conversation), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 28.dp))
            LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown) { m ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                        Column(horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
                            Text(
                                m.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (m.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .widthIn(max = 520.dp)
                                    .background(
                                        if (m.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(20.dp),
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            if (m.pending != null) {
                                val yes = stringResource(Res.string.yes)
                                val no = stringResource(Res.string.no)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    Button(onClick = { onAnswer(yes, true) }) { Text(yes) }
                                    OutlinedButton(onClick = { onAnswer(no, false) }) { Text(no) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
