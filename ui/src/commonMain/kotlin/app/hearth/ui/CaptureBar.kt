package app.hearth.ui

import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import app.hearth.ui.resources.listening
import app.hearth.ui.resources.speak
import app.hearth.ui.resources.stop_listening
import app.hearth.ui.resources.voice_add
import app.hearth.ui.resources.voice_check
import app.hearth.ui.resources.voice_clear
import app.hearth.ui.resources.voice_more
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
    var listening by remember { mutableStateOf(false) }
    // Spoken words wait in the field until the person says "yes, add it". Talking again adds to them.
    var heard by remember { mutableStateOf(false) }
    var spokenBefore by remember { mutableStateOf("") }
    var speechLang by rememberSaveable { mutableStateOf(speechLanguage) }
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
        AnimatedVisibility(heard && input.isNotBlank() && !listening, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
            VoiceCheck(
                text = input,
                onAdd = { onSend(input); input = ""; heard = false },
                onClear = { input = ""; heard = false; spokenBefore = "" },
            )
        }
        AnimatedVisibility(!heard && showReply && last != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
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
                        heard = false
                    }
                    Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                        if (input.isEmpty()) {
                            Text(
                                stringResource(if (listening) Res.string.listening else Res.string.capture_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            readOnly = listening,
                            // A pasted list keeps its lines (one item each); Enter still sends, Shift+Enter adds a line.
                            singleLine = false,
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 12.dp)
                                .onPreviewKeyEvent { e ->
                                    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) { send(); true } else false
                                },
                        )
                    }
                    LangPill(speechLang) { speechLang = if (speechLang.startsWith("nl")) "en-GB" else "nl-BE" }
                    VoiceButton(
                        language = speechLang,
                        listening = listening,
                        onListening = {
                            listening = it
                            if (it) spokenBefore = if (heard) input.trim() else "" else heard = input.isNotBlank()
                        },
                        onPartial = { input = joinSpoken(spokenBefore, it) },
                        onFinal = { input = joinSpoken(spokenBefore, it); heard = input.isNotBlank() },
                    )
                    Spacer(Modifier.size(6.dp))
                    // Spoken words are added from the "Klopt dit?" card; here they can be wiped to start over.
                    val wipe = heard || listening
                    FilledIconButton(
                        onClick = if (wipe) ({ input = ""; heard = false; spokenBefore = "" }) else send,
                        enabled = input.isNotBlank(),
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Icon(
                            if (wipe) HearthIcons.Delete else HearthIcons.Send,
                            stringResource(if (wipe) Res.string.voice_clear else Res.string.send),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A second take continues the first: "melk" + "en eieren" = "melk en eieren". */
internal fun joinSpoken(before: String, now: String): String =
    listOf(before.trim(), now.trim()).filter { it.isNotEmpty() }.joinToString(" ")

/** Ember mic; while listening it turns dark with a soft ring, and tapping stops. */
@Composable
internal fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val ring by rememberInfiniteTransition().animateFloat(1f, 1.18f, infiniteRepeatable(tween(700), RepeatMode.Reverse))
    Box(contentAlignment = Alignment.Center) {
        if (listening) Box(Modifier.size(52.dp).scale(ring).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape))
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp),
            colors = if (listening) {
                IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.surface)
            } else {
                IconButtonDefaults.filledIconButtonColors()
            },
        ) {
            Icon(if (listening) HearthIcons.Close else HearthIcons.Mic, contentDescription = stringResource(if (listening) Res.string.stop_listening else Res.string.speak))
        }
    }
}

/** NL / EN: which language the mic listens for. The text parser understands both either way. */
@Composable
private fun LangPill(language: String, onToggle: () -> Unit) {
    Text(
        if (language.startsWith("nl")) "NL" else "EN",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/** What the mic heard, asking before anything is added. */
@Composable
private fun VoiceCheck(text: String, onAdd: () -> Unit, onClear: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.onBackground, shadowElevation = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
            Icon(HearthIcons.Mic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.voice_check), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                Text("“$text”", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.surface)
                Text(stringResource(Res.string.voice_more), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            }
            Spacer(Modifier.size(10.dp))
            OutlinedButton(onClick = onClear) { Text(stringResource(Res.string.voice_clear), color = MaterialTheme.colorScheme.surface) }
            Spacer(Modifier.size(8.dp))
            Button(onClick = onAdd) { Text(stringResource(Res.string.voice_add)) }
        }
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
        Column(Modifier.nestedScroll(SheetScrollStop).fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding()) {
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
