package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.hearth.ai.route.Place
import app.hearth.core.model.Lang
import app.hearth.core.model.Member
import app.hearth.core.model.Section
import app.hearth.core.model.TranscriptLine
import app.hearth.core.model.WeekFeeling
import app.hearth.core.model.Win
import app.hearth.core.model.dayLabel
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.conversation_show
import app.hearth.ui.resources.feeling_heavy
import app.hearth.ui.resources.feeling_light
import app.hearth.ui.resources.feeling_title
import app.hearth.ui.resources.feeling_us
import app.hearth.ui.resources.listen_hint
import app.hearth.ui.resources.listen_lines
import app.hearth.ui.resources.listen_on
import app.hearth.ui.resources.listen_start
import app.hearth.ui.resources.listen_stop
import app.hearth.ui.resources.place_event
import app.hearth.ui.resources.place_free
import app.hearth.ui.resources.place_friction
import app.hearth.ui.resources.place_goal
import app.hearth.ui.resources.place_idea
import app.hearth.ui.resources.place_meal
import app.hearth.ui.resources.place_none
import app.hearth.ui.resources.place_planning
import app.hearth.ui.resources.place_rule
import app.hearth.ui.resources.place_shopping
import app.hearth.ui.resources.place_win
import app.hearth.ui.resources.sort_conversation
import app.hearth.ui.resources.transcript_clear
import app.hearth.ui.resources.transcript_clear_body
import app.hearth.ui.resources.transcript_empty
import app.hearth.ui.resources.transcript_kept
import app.hearth.ui.resources.voorstel_add
import app.hearth.ui.resources.voorstel_empty
import app.hearth.ui.resources.voorstel_hint
import app.hearth.ui.resources.voorstel_title
import app.hearth.ui.resources.win_remove
import app.hearth.ui.resources.wins_hint
import app.hearth.ui.resources.wins_title
import app.hearth.ui.theme.style
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Luister mee during the Weekoverleg: on/off, what is being heard, and "put it in place". */
@Composable
fun ListenBar(
    available: Boolean,
    listening: Boolean,
    partial: String,
    lines: List<TranscriptLine>,
    onToggle: () -> Unit,
    onShow: () -> Unit,
    onSort: () -> Unit,
) {
    val unsorted = lines.count { !it.sorted }
    val ink = Section.PROTECTED_TIME.style()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(if (listening) ink.tint else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (available) {
            MicButton(listening, onToggle)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (listening) Res.string.listen_on else if (available) Res.string.listen_start else Res.string.conversation_show),
                style = MaterialTheme.typography.titleMedium,
            )
            val sub = when {
                listening && partial.isNotBlank() -> "“$partial”"
                lines.isNotEmpty() -> pluralStringResource(Res.plurals.listen_lines, lines.size, lines.size)
                else -> stringResource(Res.string.listen_hint)
            }
            Text(
                sub, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                fontStyle = if (listening && partial.isNotBlank()) FontStyle.Italic else FontStyle.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (lines.isNotEmpty()) TextButton(onClick = onShow) { Text(stringResource(Res.string.conversation_show)) }
        if (unsorted > 0) {
            Spacer(Modifier.width(6.dp))
            Button(onClick = onSort) { Text(stringResource(Res.string.sort_conversation) + " ($unsorted)") }
        }
        if (listening && available) {
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onToggle) { Text(stringResource(Res.string.listen_stop)) }
        }
    }
}

/** What Luister mee heard, line by line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptSheet(title: String, lines: List<TranscriptLine>, onClear: (() -> Unit)?, onDismiss: () -> Unit) {
    var clearing by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding().padding(horizontal = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (onClear != null && lines.isNotEmpty()) TextButton(onClick = { clearing = true }) { Text(stringResource(Res.string.transcript_clear)) }
            }
            Text(stringResource(Res.string.transcript_kept), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(12.dp))
            if (lines.isEmpty()) EmptyHint(stringResource(Res.string.transcript_empty))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(lines) { _, line ->
                    Row {
                        Text(clock(line.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp).padding(top = 3.dp))
                        Text(line.text, style = MaterialTheme.typography.bodyLarge, color = if (line.sorted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
    if (clearing && onClear != null) {
        ConfirmDialog(stringResource(Res.string.transcript_clear), stringResource(Res.string.transcript_clear_body), onYes = onClear) { clearing = false }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
private fun clock(millis: Long): String =
    formatClock(kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()))

/** The conversation sorted into places: everything ticked, one tap adds it all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoorstelSheet(
    rows: List<VoorstelRow>,
    today: LocalDate,
    lang: Lang,
    onMove: (Int, Place) -> Unit,
    onToggle: (Int) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val count = rows.count { it.checked }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.voorstel_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(Res.string.voorstel_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onAdd, enabled = count > 0) { Text(stringResource(Res.string.voorstel_add, count)) }
            }
            Spacer(Modifier.size(12.dp))
            if (rows.isEmpty()) EmptyHint(stringResource(Res.string.voorstel_empty))
            // Grouped by place; just talk last.
            val order = rows.withIndex().sortedBy { (_, r) -> if (r.proposal.place == Place.NONE) Int.MAX_VALUE else r.proposal.place.ordinal }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(order) { n, (index, row) ->
                    val place = row.proposal.place
                    if (n == 0 || order[n - 1].value.proposal.place != place) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) {
                            Icon(place.icon(), null, tint = place.section().style().ink, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(place.label()), style = MaterialTheme.typography.titleSmall, color = place.section().style().ink)
                        }
                    }
                    VoorstelLine(row, today, lang, onMove = { onMove(index, it) }, onToggle = { onToggle(index) })
                }
            }
        }
    }
}

@Composable
private fun VoorstelLine(row: VoorstelRow, today: LocalDate, lang: Lang, onMove: (Place) -> Unit, onToggle: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val p = row.proposal
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Checkbox(checked = row.checked, onCheckedChange = { onToggle() }, enabled = p.calls.isNotEmpty())
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(p.text.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, color = if (row.checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            val day = p.date?.let { dayLabel(it, today, lang) }
            val heard = if (p.text.trim().lowercase() != p.sentence.trim().lowercase()) "“${p.sentence}”" else null
            listOfNotNull(day, heard).joinToString("  ·  ").takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        Box {
            ChoiceChip(stringResource(p.place.label()), false, { choosing = true }, icon = p.place.icon())
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                Place.entries.forEach { place ->
                    DropdownMenuItem(
                        text = { Text(stringResource(place.label())) },
                        leadingIcon = { Icon(place.icon(), null, tint = place.section().style().ink) },
                        onClick = { choosing = false; onMove(place) },
                    )
                }
            }
        }
    }
}

fun Place.label(): StringResource = when (this) {
    Place.PLANNING -> Res.string.place_planning
    Place.EVENT -> Res.string.place_event
    Place.FREE_TIME -> Res.string.place_free
    Place.SHOPPING -> Res.string.place_shopping
    Place.MEAL -> Res.string.place_meal
    Place.HOUSE_RULE -> Res.string.place_rule
    Place.FRICTION -> Res.string.place_friction
    Place.IDEA -> Res.string.place_idea
    Place.GOAL -> Res.string.place_goal
    Place.WIN -> Res.string.place_win
    Place.NONE -> Res.string.place_none
}

fun Place.section(): Section = when (this) {
    Place.PLANNING -> Section.PROJECT
    Place.EVENT -> Section.APPOINTMENT
    Place.FREE_TIME -> Section.PROTECTED_TIME
    Place.SHOPPING -> Section.SHOPPING
    Place.MEAL -> Section.SHOPPING
    Place.HOUSE_RULE -> Section.NOTE
    Place.FRICTION -> Section.FRICTION
    Place.IDEA -> Section.IDEA
    Place.GOAL -> Section.GOAL
    Place.WIN -> Section.CHORE
    Place.NONE -> Section.OTHER
}

fun Place.icon() = when (this) {
    Place.PLANNING -> HearthIcons.Backlog
    Place.EVENT -> HearthIcons.Calendar
    Place.FREE_TIME -> HearthIcons.Sun
    Place.SHOPPING -> HearthIcons.Cart
    Place.MEAL -> HearthIcons.Restaurant
    Place.HOUSE_RULE -> HearthIcons.Rule
    Place.FRICTION -> HearthIcons.Bolt
    Place.IDEA -> HearthIcons.Idea
    Place.GOAL -> HearthIcons.Star
    Place.WIN -> HearthIcons.Check
    Place.NONE -> HearthIcons.Chat
}

/** Wat ging goed: what people named, with an X that asks first. */
@Composable
fun WinsSection(wins: List<Win>, members: List<Member>, onAdd: (String) -> Unit, onRemove: (Win) -> Unit) {
    var removing by remember { mutableStateOf<Win?>(null) }
    val byId = members.associateBy { it.id }
    GroupTitle(stringResource(Res.string.wins_title))
    wins.forEach { win ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp)) {
            Icon(HearthIcons.Heart, null, tint = Section.PROTECTED_TIME.style().ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(win.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            win.memberId?.let(byId::get)?.let { MemberDot(it) }
            DeleteButton({ removing = win })
        }
    }
    AddRow(stringResource(Res.string.wins_hint), onAdd, compact = true)
    removing?.let { w -> ConfirmDialog(stringResource(Res.string.win_remove), w.text, onYes = { onRemove(w) }) { removing = null } }
}

/** "Hoe zwaar voelde de week?": five flames per person (or one row for the household). */
@Composable
fun FeelingMeter(members: List<Member>, feelings: List<WeekFeeling>, onSet: (Int, Member?) -> Unit) {
    GroupTitle(stringResource(Res.string.feeling_title))
    val people: List<Member?> = members.ifEmpty { listOf(null) }
    people.forEach { member ->
        val value = feelings.firstOrNull { it.memberId == member?.id }?.value
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 2.dp, bottom = 2.dp)) {
            if (member != null) MemberDot(member)
            Spacer(Modifier.width(10.dp))
            Text(member?.name ?: stringResource(Res.string.feeling_us), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(110.dp))
            Text(stringResource(Res.string.feeling_light), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            (1..5).forEach { n ->
                val on = value != null && n <= value
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp).clip(CircleShape).clickable { onSet(n, member) },
                ) {
                    Icon(
                        HearthIcons.Flame, n.toString(),
                        tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(if (on) 32.dp else 28.dp),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.feeling_heavy), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A meeting's summary as written at Klaar. */
@Composable
fun SummaryText(summary: String) {
    val lines = summary.lines().filter { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEachIndexed { i, line ->
            val colon = line.indexOf(": ")
            if (i == 0) {
                Text(line, style = MaterialTheme.typography.titleMedium)
            } else if (colon > 0) {
                Row {
                    Text(line.substring(0, colon), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(140.dp).padding(top = 2.dp))
                    Text(line.substring(colon + 2), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            } else {
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
