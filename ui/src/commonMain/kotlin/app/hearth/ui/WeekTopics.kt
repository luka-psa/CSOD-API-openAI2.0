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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.hearth.ai.Orchestrator
import app.hearth.ai.route.Place
import app.hearth.core.model.dayLabel
import kotlinx.datetime.LocalDate
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.model.Topic
import androidx.compose.ui.unit.Dp
import app.hearth.core.model.topics
import app.hearth.core.model.TopicGroup
import app.hearth.core.model.title
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.agree_empty
import app.hearth.ui.resources.agree_hint
import app.hearth.ui.resources.agree_later
import app.hearth.ui.resources.agree_title
import app.hearth.ui.resources.agree_unsorted
import app.hearth.ui.resources.agree_yes
import app.hearth.ui.resources.conversation_show
import app.hearth.ui.resources.heard_this_week
import app.hearth.ui.resources.suggestion_added
import app.hearth.ui.resources.listen_still
import app.hearth.ui.resources.place_none
import app.hearth.ui.resources.listen_hint
import app.hearth.ui.resources.listen_live
import app.hearth.ui.resources.listen_on
import app.hearth.ui.resources.listen_stage_hint
import app.hearth.ui.resources.listen_start
import app.hearth.ui.resources.listen_stop
import app.hearth.ui.resources.sort_conversation
import app.hearth.ui.theme.SectionStyle
import app.hearth.ui.theme.AccentStyle
import app.hearth.ui.theme.UnsortedStyle
import app.hearth.ui.theme.style
import app.hearth.ui.theme.style
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Luister mee, front and centre: a big mic that breathes while it listens, the words as they come in, and the
 * last thoughts with what Hearth concludes from each. Pauses don't cut a thought; Hearth waits until it has
 * settled. A tap on a suggestion opens it: its name, place and day, filled in from what was said. The tiles below
 * light up in the same colour.
 */
@Composable
fun ListenStage(
    available: Boolean,
    listening: Boolean,
    partial: String,
    heard: List<HeardThought>,
    settled: Boolean,
    hasLines: Boolean,
    lit: Set<Topic>,
    today: LocalDate,
    lang: Lang,
    onToggle: () -> Unit,
    onShow: () -> Unit,
    onSort: () -> Unit,
    /** A suggestion was changed (or added right away, with true). */
    onSuggestion: (HeardThought, Int, SuggestionChange, Boolean) -> Unit,
) {
    val ink = AccentStyle
    var opened by remember { mutableStateOf<Pair<HeardThought, Int>?>(null) }
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
            heard.forEachIndexed { i, thought ->
                ThoughtRow(thought, newest = i == 0, waiting = i == 0 && !settled, today = today, lang = lang, onOpen = { n -> opened = thought to n })
            }
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
    opened?.let { (t, n) ->
        t.proposals.getOrNull(n)?.let { p ->
            SuggestionSheet(
                p, today, lang,
                onSave = { onSuggestion(t, n, it, false) },
                onAddNow = if (n in t.added) null else { c -> onSuggestion(t, n, c, true) },
            ) { opened = null }
        }
    }
}

/** All topics as small dots: the one the last sentence went to glows, so it's visible without scrolling. */
@Composable
private fun TopicRail(lit: Set<Topic>) = BoxWithConstraints(Modifier.fillMaxWidth()) {
    // Ten dots: full size on the tablet, a little smaller on a phone so they stay on one line.
    val dot = minOf(36.dp, (maxWidth - 4.dp * (Topic.entries.size - 1)) / Topic.entries.size)
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
                modifier = Modifier.size(dot).then(glow).clip(CircleShape).background(if (on) s.ink else MaterialTheme.colorScheme.surface),
            ) {
                Icon(topic.icon(), null, tint = if (on) Color.White else s.ink.copy(alpha = 0.55f), modifier = Modifier.size(dot / 2))
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

/**
 * One thought as it was said, and under it what Hearth concludes: "Naar de opvang · donderdag" in the colour of
 * its topic. While the newest one may still go on, Hearth only says it is listening.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThoughtRow(thought: HeardThought, newest: Boolean, waiting: Boolean, today: LocalDate, lang: Lang, onOpen: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(18.dp))
            .background(if (newest) MaterialTheme.colorScheme.surface else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            thought.text,
            style = if (newest) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = if (newest) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (newest) 4 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(6.dp))
        when {
            waiting -> Text(stringResource(Res.string.listen_still), style = MaterialTheme.typography.labelMedium, color = AccentStyle.ink, fontStyle = FontStyle.Italic)
            thought.proposals.isEmpty() -> Text(stringResource(Res.string.place_none), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // A tap opens the suggestion to change its name, place or day.
                thought.proposals.forEachIndexed { n, p -> ConclusionPill(p, today, lang, added = n in thought.added, onClick = { onOpen(n) }) }
            }
        }
    }
}

/** "Afspreken met Anthony · deze week", with the icon and colour of where it goes. */
@Composable
internal fun ConclusionPill(p: Orchestrator.Proposal, today: LocalDate, lang: Lang, added: Boolean = false, onClick: (() -> Unit)? = null) {
    val s = p.topic?.style() ?: if (p.place == Place.WIN) AccentStyle else UnsortedStyle
    val day = p.date?.let { dayLabel(it, today, lang) } ?: if (p.next) stringResource(Res.string.heard_this_week) else null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(s.tint)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // Added already: a tick instead of its icon.
        Icon(if (added) HearthIcons.Check else p.topic?.icon() ?: p.place.icon(), if (added) stringResource(Res.string.suggestion_added) else p.topic?.title(lang), tint = s.ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        // The day always shows whole; a long text wraps (on a phone) before it gives way.
        Text(p.text.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, color = s.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (day != null) Text("  ·  $day", style = MaterialTheme.typography.labelMedium, color = s.ink, maxLines = 1, softWrap = false)
    }
}

/**
 * The household at a glance: one calm tile per topic, a few words each, in the order and two groups used everywhere:
 * what we do and eat (six tiles, three by two) and, on a shelf beside them, the rhythm and agreements (four, two by two),
 * all tiles the same size. Tap one to open it below; the topic Luister mee just heard about glows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicGrid(glances: List<Pair<Topic, String>>, selected: Topic?, lit: Set<Topic>, lang: Lang, onSelect: (Topic) -> Unit) {
    val byTopic = glances.toMap()
    val gap = 12.dp
    // The shelf's own padding, so its tiles line up with the others.
    val shelfPad = 14.dp * 2

    @Composable
    fun Tiles(group: TopicGroup, width: Dp, columns: Int) = FlowRow(
        maxItemsInEachRow = columns,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        group.topics.forEach { topic ->
            byTopic[topic]?.let { glance -> TopicTile(topic, glance, topic == selected, topic in lit, lang, Modifier.width(width)) { onSelect(topic) } }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A hair narrower than the exact share, so rounding never pushes the last tile to the next row.
        fun tile(space: Dp, columns: Int) = (space - gap * (columns - 1)) / columns - 1.dp
        if (maxWidth >= 1000.dp) {
            // Five tiles across (three, then two on the shelf), all the same size: 6 and 4 side by side. The six get the
            // same inset as the shelf, so both labels and both rows of tiles line up.
            val size = (maxWidth - gap * 4 - shelfPad * 2) / 5 - 1.dp
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GroupLabel(TopicGroup.TASKS, lang)
                    Tiles(TopicGroup.TASKS, size, 3)
                }
                Shelf {
                    GroupLabel(TopicGroup.BOARD, lang)
                    Tiles(TopicGroup.BOARD, size, 2)
                }
            }
        } else {
            // Three to a row in between, two on a phone; the shelf's four in one row when they fit, else two by two.
            val full = maxWidth
            val columns = if (full >= 600.dp) 3 else 2
            val shelfColumns = if (full >= 600.dp) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GroupLabel(TopicGroup.TASKS, lang)
                    Tiles(TopicGroup.TASKS, tile(full - shelfPad, columns), columns)
                }
                Shelf(Modifier.fillMaxWidth()) {
                    GroupLabel(TopicGroup.BOARD, lang)
                    Tiles(TopicGroup.BOARD, tile(full - shelfPad, shelfColumns), shelfColumns)
                }
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
        // One height for every tile, so the rows line up on both sides; a long glance ends in "…".
        modifier.height(124.dp).then(glow).clip(RoundedCornerShape(24.dp))
            .background(if (selected || lit) s.tint else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (selected || lit) MaterialTheme.colorScheme.surface else s.tint), contentAlignment = Alignment.Center) {
            Icon(topic.icon(), null, tint = s.ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(10.dp))
        Text(topic.title(lang), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(2.dp))
        Text(glance, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    val s = AccentStyle
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
