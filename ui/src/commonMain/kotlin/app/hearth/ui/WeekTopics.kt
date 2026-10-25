package app.hearth.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.ai.route.Place
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.model.Topic
import app.hearth.core.model.title
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.agree_empty
import app.hearth.ui.resources.agree_hint
import app.hearth.ui.resources.agree_later
import app.hearth.ui.resources.agree_title
import app.hearth.ui.resources.agree_unsorted
import app.hearth.ui.resources.agree_yes
import app.hearth.ui.resources.conversation_show
import app.hearth.ui.resources.heard_just
import app.hearth.ui.resources.listen_hint
import app.hearth.ui.resources.listen_live
import app.hearth.ui.resources.listen_on
import app.hearth.ui.resources.listen_stage_hint
import app.hearth.ui.resources.listen_start
import app.hearth.ui.resources.listen_stop
import app.hearth.ui.resources.sort_conversation
import app.hearth.ui.theme.SectionStyle
import app.hearth.ui.theme.style
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Each topic borrows the colour of the sections it holds. */
fun Topic.style(): SectionStyle = when (this) {
    Topic.FREE_TIME -> Section.PROTECTED_TIME
    Topic.ADMIN -> Section.ADMIN
    Topic.PROJECTS -> Section.PROJECT
    Topic.HOME -> Section.CHORE
    Topic.RHYTHM -> Section.RHYTHM
    Topic.SHOPPING -> Section.SHOPPING
    Topic.MENU -> Section.APPOINTMENT
    Topic.FRICTION -> Section.FRICTION
    Topic.RULES -> Section.NOTE
}.style()

fun Topic.icon() = when (this) {
    Topic.FREE_TIME -> HearthIcons.Sun
    Topic.ADMIN -> HearthIcons.Flag
    Topic.PROJECTS -> HearthIcons.Backlog
    Topic.HOME -> HearthIcons.Home
    Topic.RHYTHM -> HearthIcons.Repeat
    Topic.SHOPPING -> HearthIcons.Cart
    Topic.MENU -> HearthIcons.Restaurant
    Topic.FRICTION -> HearthIcons.Bolt
    Topic.RULES -> HearthIcons.Rule
}

/**
 * Luister mee, front and centre: a big mic that breathes while it listens, the words as they come in,
 * and the last sentences with the topic each one goes to. The tiles below light up in the same colour.
 */
@Composable
fun ListenStage(
    available: Boolean,
    listening: Boolean,
    partial: String,
    heard: List<HeardLine>,
    hasLines: Boolean,
    lit: Set<Topic>,
    lang: Lang,
    onToggle: () -> Unit,
    onShow: () -> Unit,
    onSort: () -> Unit,
) {
    val ink = Section.PROTECTED_TIME.style()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(if (listening) ink.tint else MaterialTheme.colorScheme.surface)
            .animateContentSize()
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (available) {
                BigMic(listening, ink.ink, onToggle)
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (listening) Res.string.listen_on else if (available) Res.string.listen_start else Res.string.conversation_show),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (listening) {
                        Spacer(Modifier.width(12.dp))
                        SoundBars(ink.ink)
                    }
                }
                Text(
                    stringResource(if (listening) Res.string.listen_live else if (available) Res.string.listen_stage_hint else Res.string.listen_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (listening || heard.isNotEmpty()) {
            Spacer(Modifier.size(14.dp))
            TopicRail(lit)
        }
        if (listening && partial.isNotBlank()) {
            Spacer(Modifier.size(14.dp))
            Text(
                "“$partial”",
                style = MaterialTheme.typography.headlineSmall,
                fontStyle = FontStyle.Italic,
                color = ink.ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (heard.isNotEmpty()) {
            Spacer(Modifier.size(12.dp))
            heard.forEachIndexed { i, line -> HeardRow(line, newest = i == 0, lang = lang) }
        }
        if (heard.isNotEmpty() || hasLines || listening) {
            Spacer(Modifier.size(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (heard.isNotEmpty()) Button(onClick = onSort) { Text(stringResource(Res.string.sort_conversation)) }
                if (hasLines) TextButton(onClick = onShow) { Text(stringResource(Res.string.conversation_show)) }
                if (listening && available) TextButton(onClick = onToggle) { Text(stringResource(Res.string.listen_stop)) }
            }
        }
    }
}

/** All seven topics as small dots: the one the last sentence went to glows, so it's visible without scrolling. */
@Composable
private fun TopicRail(lit: Set<Topic>) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Topic.entries.forEach { topic ->
            val s = topic.style()
            val on = topic in lit
            val glow = if (on) {
                val a by rememberInfiniteTransition().animateFloat(0.3f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse))
                Modifier.drawBehind { drawCircle(s.ink.copy(alpha = a), radius = size.minDimension / 2 + 3.dp.toPx(), style = Stroke(width = 2.5f.dp.toPx())) }
            } else Modifier
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp).then(glow).clip(CircleShape).background(if (on) s.ink else MaterialTheme.colorScheme.surface),
            ) {
                Icon(topic.icon(), null, tint = if (on) Color.White else s.ink.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A mic that breathes: two soft rings grow and fade while it listens. Drawn, so it never recomposes. */
@Composable
private fun BigMic(listening: Boolean, ink: Color, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val ringModifier = if (listening) {
        val t by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)))
        Modifier.drawBehind {
            listOf(t, (t + 0.5f) % 1f).forEach { p ->
                drawCircle(ink.copy(alpha = 0.35f * (1f - p)), radius = size.minDimension / 2 * (1f + 0.55f * p), style = Stroke(width = 3.dp.toPx()))
            }
        }
    } else Modifier
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(76.dp).then(ringModifier).clip(CircleShape).background(if (listening) ink else primary).clickable(onClick = onClick),
    ) {
        Icon(if (listening) HearthIcons.Close else HearthIcons.Mic, null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

/** Five little bars that move while Hearth listens. */
@Composable
private fun SoundBars(ink: Color) {
    val t by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)))
    Box(
        Modifier.size(width = 34.dp, height = 18.dp).drawBehind {
            val w = size.width / 9
            repeat(5) { i ->
                val h = size.height * (0.3f + 0.7f * abs(sin(2 * PI.toFloat() * (t + i * 0.18f))))
                drawRoundRect(ink, Offset(i * 2 * w, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
            }
        },
    )
}

/** "Mia moet naar de tandarts" → Admin: the sentence with the topic it goes to. */
@Composable
private fun HeardRow(line: HeardLine, newest: Boolean, lang: Lang) {
    val topic = line.topic
    val s = topic?.style() ?: Section.OTHER.style()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp))
            .background(if (newest) MaterialTheme.colorScheme.surface else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(s.tint).padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(topic?.icon() ?: line.place.icon(), null, tint = s.ink, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(topic?.title(lang) ?: stringResource(line.place.label()), style = MaterialTheme.typography.labelMedium, color = s.ink, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(line.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (newest) Text(stringResource(Res.string.heard_just), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The household at a glance: one calm tile per topic, a few words each. Tap one to open it below; the
 * topic Luister mee just heard about glows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicGrid(glances: List<Pair<Topic, String>>, selected: Topic?, lit: Set<Topic>, lang: Lang, onSelect: (Topic) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 12.dp
        val columns = when {
            maxWidth >= 1000.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }
        val width = (maxWidth - gap * (columns - 1)) / columns
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            glances.forEach { (topic, glance) ->
                TopicTile(topic, glance, topic == selected, topic in lit, lang, Modifier.width(width)) { onSelect(topic) }
            }
        }
    }
}

@Composable
private fun TopicTile(topic: Topic, glance: String, selected: Boolean, lit: Boolean, lang: Lang, modifier: Modifier, onClick: () -> Unit) {
    val s = topic.style()
    val glow = if (lit) {
        val a by rememberInfiniteTransition().animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse))
        Modifier.drawBehind { drawRoundRect(s.ink.copy(alpha = a), cornerRadius = CornerRadius(24.dp.toPx()), style = Stroke(width = 3.dp.toPx())) }
    } else if (selected) {
        Modifier.border(2.dp, s.ink, RoundedCornerShape(24.dp))
    } else Modifier
    Column(
        modifier.heightIn(min = 118.dp).then(glow).clip(RoundedCornerShape(24.dp))
            .background(if (selected || lit) s.tint else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (selected || lit) MaterialTheme.colorScheme.surface else s.tint), contentAlignment = Alignment.Center) {
            Icon(topic.icon(), null, tint = s.ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(10.dp))
        Text(topic.title(lang), style = MaterialTheme.typography.titleSmall, maxLines = 2)
        Spacer(Modifier.size(2.dp))
        Text(glance, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Before Klaar: the summary to read together and agree on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgreeSheet(lines: List<String>, unsorted: Boolean, onSort: () -> Unit, onAgree: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).navigationBarsPadding().padding(horizontal = 24.dp)) {
            Text(stringResource(Res.string.agree_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(Res.string.agree_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (unsorted) {
                    val s = Section.PROTECTED_TIME.style()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(s.tint, MaterialTheme.shapes.medium).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(Res.string.agree_unsorted), style = MaterialTheme.typography.bodyMedium, color = s.ink, modifier = Modifier.weight(1f))
                        TextButton(onClick = onSort) { Text(stringResource(Res.string.sort_conversation)) }
                    }
                    Spacer(Modifier.size(14.dp))
                }
                if (lines.size <= 1) EmptyHint(stringResource(Res.string.agree_empty))
                else Column(Modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(18.dp)) { SummaryText(lines.joinToString("\n")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 14.dp)) {
                Button(onClick = onAgree) {
                    Icon(HearthIcons.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.agree_yes))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.agree_later)) }
            }
        }
    }
}
