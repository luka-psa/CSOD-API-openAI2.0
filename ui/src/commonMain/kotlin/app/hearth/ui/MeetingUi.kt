package app.hearth.ui

import app.hearth.ai.Orchestrator
import app.hearth.core.model.plusDays
import app.hearth.ui.resources.place_rhythm
import app.hearth.ui.resources.suggestion_rhythm_hint
import app.hearth.ui.resources.suggestion_added
import app.hearth.ui.resources.suggestion_add_now
import app.hearth.ui.resources.suggestion_no_day
import app.hearth.ui.resources.suggestion_when
import app.hearth.ui.resources.suggestion_where
import app.hearth.ui.resources.suggestion_name
import app.hearth.ui.resources.suggestion_title
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.toMutableStateList
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import app.hearth.ui.resources.save
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.heard_this_week
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardItem
import app.hearth.core.model.Ease
import app.hearth.core.model.isRoutine
import app.hearth.core.model.WeekRating
import app.hearth.core.model.weekWord
import app.hearth.core.model.RuleCheck
import app.hearth.core.model.title
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import app.hearth.ui.resources.habit_easier
import app.hearth.ui.resources.attention_hint
import app.hearth.ui.resources.attention_title
import app.hearth.ui.resources.ease_postponed
import app.hearth.ui.resources.small_step_add
import app.hearth.ui.resources.small_step_hint
import app.hearth.ui.resources.small_step_line
import app.hearth.ui.resources.pairing_line
import app.hearth.ui.resources.coach_before
import app.hearth.ui.resources.coach_said
import app.hearth.core.model.HabitReview
import app.hearth.core.model.HabitStage
import app.hearth.core.model.HabitTip
import app.hearth.core.model.TipAction
import app.hearth.ui.resources.small_step_title
import app.hearth.ui.resources.habit_less_often
import app.hearth.ui.resources.week_agreed
import app.hearth.ui.resources.week_guess
import app.hearth.ui.resources.week_hint
import app.hearth.ui.resources.week_neutral
import app.hearth.ui.resources.week_no_agreement
import app.hearth.ui.resources.week_off
import app.hearth.ui.resources.week_ok
import app.hearth.ui.resources.week_switch_off
import app.hearth.ui.resources.week_switch_on
import app.hearth.ui.resources.week_title
import app.hearth.ui.resources.review_title
import app.hearth.ui.resources.review_hint
import app.hearth.ui.resources.review_empty
import app.hearth.ui.resources.anchor_none
import app.hearth.core.model.Win
import app.hearth.core.model.dayLabel
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.conversation_show
import app.hearth.ui.resources.listen_hint
import app.hearth.ui.resources.listen_lines
import app.hearth.ui.resources.listen_on
import app.hearth.ui.resources.listen_start
import app.hearth.ui.resources.listen_stop
import app.hearth.ui.resources.listen_running
import app.hearth.ui.resources.listen_back
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
import app.hearth.ui.theme.AccentStyle
import app.hearth.core.model.Topic
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
    val ink = AccentStyle
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

/** On other screens while Luister mee runs: it's still listening, and one tap goes back to the meeting. */
@Composable
fun ListenPill(lines: Int, partial: String, onBack: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val ink = AccentStyle
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(ink.tint).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Icon(HearthIcons.Mic, null, tint = ink.ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.listen_running), style = MaterialTheme.typography.titleSmall, color = ink.ink)
            Text(
                if (partial.isNotBlank()) "“$partial”" else pluralStringResource(Res.plurals.listen_lines, lines, lines),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onBack) { Text(stringResource(Res.string.listen_back)) }
        TextButton(onClick = onStop) { Text(stringResource(Res.string.listen_stop)) }
    }
}

/** What Luister mee heard, line by line, as it was said. What Hearth made of it is changed on the suggestions. */
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
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(clock(line.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp).padding(top = 3.dp))
                        Text(line.text, style = MaterialTheme.typography.bodyLarge, color = if (line.sorted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
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
    onEdit: (Int, SuggestionChange) -> Unit,
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
            // Grouped by topic, the same name, icon and colour as on the stage and the tiles; wins, then just talk, last.
            fun group(r: VoorstelRow) = r.proposal.topic?.ordinal ?: (Topic.entries.size + if (r.proposal.place == Place.NONE) Place.entries.size else r.proposal.place.ordinal)
            val order = rows.withIndex().sortedBy { (_, r) -> group(r) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(order) { n, (index, row) ->
                    val place = row.proposal.place
                    val topic = row.proposal.topic
                    if (n == 0 || group(order[n - 1].value) != group(row)) {
                        val ink = topic?.style()?.ink ?: place.section().style().ink
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) {
                            Icon(topic?.icon() ?: place.icon(), null, tint = ink, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(topic?.title(lang) ?: stringResource(place.label()), style = MaterialTheme.typography.titleSmall, color = ink)
                        }
                    }
                    VoorstelLine(row, today, lang, onMove = { onMove(index, it) }, onToggle = { onToggle(index) }, onEdit = { onEdit(index, it) })
                }
            }
        }
    }
}

@Composable
private fun VoorstelLine(row: VoorstelRow, today: LocalDate, lang: Lang, onMove: (Place) -> Unit, onToggle: () -> Unit, onEdit: (SuggestionChange) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val p = row.proposal
    if (editing) SuggestionSheet(p, today, lang, onSave = onEdit, onAddNow = null) { editing = false }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        if (row.added) Icon(HearthIcons.Check, stringResource(Res.string.suggestion_added), tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(12.dp).size(22.dp))
        else Checkbox(checked = row.checked, onCheckedChange = { onToggle() }, enabled = p.calls.isNotEmpty())
        // Tap it to change its name, place or day.
        Column(Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable { editing = true }.padding(vertical = 6.dp, horizontal = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.text.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge,
                    color = if (row.checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Icon(HearthIcons.Edit, stringResource(Res.string.suggestion_title), tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
            }
            val day = p.date?.let { dayLabel(it, today, lang) } ?: if (p.next) stringResource(Res.string.heard_this_week) else null
            val heard = if (p.text.trim().lowercase() != p.heard.trim().lowercase()) "“${p.heard}”" else null
            listOfNotNull(day, heard).joinToString("  ·  ").takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

/** What the family changed on a suggestion: its words, where it goes, and its day (or "deze week"). */
data class SuggestionChange(val text: String, val place: Place, val date: LocalDate?, val next: Boolean)

/**
 * One suggestion from the conversation, opened to change it: the name, where it goes (a task, an appointment, a
 * rhythm…) and its day, all filled in from what was said. Bewaar keeps the change for the Voorstel; Zet erin adds it
 * right away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionSheet(
    p: Orchestrator.Proposal,
    today: LocalDate,
    lang: Lang,
    onSave: (SuggestionChange) -> Unit,
    onAddNow: ((SuggestionChange) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(p.text.replaceFirstChar { it.uppercase() }) }
    var place by remember { mutableStateOf(p.place) }
    var date by remember { mutableStateOf(p.date) }
    var next by remember { mutableStateOf(p.next) }
    val change = SuggestionChange(text.trim(), place, date, next && date == null)
    val dirty = text.trim() != p.text.replaceFirstChar { it.uppercase() } || place != p.place || date != p.date || next != p.next
    val dated = place in listOf(Place.PLANNING, Place.EVENT, Place.FREE_TIME, Place.MEAL)
    FormSheet(dirty, onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 28.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(Res.string.suggestion_title), style = MaterialTheme.typography.headlineSmall)
            if (p.heard.isNotBlank()) {
                Text("“${p.heard.trim()}”", style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(text, { text = it }, label = { Text(stringResource(Res.string.suggestion_name)) }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(Res.string.suggestion_where), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Place.entries.forEach { pl -> ChoiceChip(stringResource(pl.label()), place == pl, { place = pl }, icon = pl.icon()) }
            }
            if (dated) {
                Text(stringResource(Res.string.suggestion_when), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (place != Place.MEAL) {
                        ChoiceChip(stringResource(Res.string.suggestion_no_day), date == null && !next, { date = null; next = false })
                        ChoiceChip(stringResource(Res.string.heard_this_week).replaceFirstChar { it.uppercase() }, date == null && next, { date = null; next = true })
                    }
                    (0 until 7).map { today.plusDays(it) }.forEach { d ->
                        ChoiceChip(dayLabel(d, today, lang).replaceFirstChar { it.uppercase() } + if (d.toEpochDays() - today.toEpochDays() >= 2) " ${d.day}" else "", date == d, { date = d; next = false })
                    }
                    // A day further away that was heard keeps its own chip.
                    p.date?.takeIf { it > today.plusDays(6) }?.let { d -> ChoiceChip(dayLabel(d, today, lang), date == d, { date = d; next = false }) }
                }
            }
            if (place == Place.RHYTHM) {
                Text(stringResource(Res.string.suggestion_rhythm_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onAddNow != null) {
                    Button(onClick = { onAddNow(change); onDismiss() }, enabled = text.isNotBlank() && place != Place.NONE) { Text(stringResource(Res.string.suggestion_add_now)) }
                }
                FilledTonalButton(onClick = { onSave(change); onDismiss() }, enabled = text.isNotBlank()) { Text(stringResource(Res.string.save)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        }
    }
}

fun Place.label(): StringResource = when (this) {
    Place.PLANNING -> Res.string.place_planning
    Place.EVENT -> Res.string.place_event
    Place.FREE_TIME -> Res.string.place_free
    Place.RHYTHM -> Res.string.place_rhythm
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
    Place.RHYTHM -> Section.RHYTHM
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
    Place.FREE_TIME -> Topic.FREE_TIME.icon()
    Place.RHYTHM -> Topic.RHYTHM.icon()
    Place.SHOPPING -> HearthIcons.Cart
    Place.MEAL -> HearthIcons.Restaurant
    Place.HOUSE_RULE -> HearthIcons.Rule
    Place.FRICTION -> Topic.FRICTION.icon()
    Place.IDEA -> HearthIcons.Idea
    Place.GOAL -> Topic.GOALS.icon()
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
            Icon(HearthIcons.Heart, null, tint = AccentStyle.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(win.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            win.memberId?.let(byId::get)?.let { MemberDot(it) }
            DeleteButton({ removing = win })
        }
    }
    AddRow(stringResource(Res.string.wins_hint), onAdd, compact = true)
    removing?.let { w -> ConfirmDialog(stringResource(Res.string.win_remove), w.text, onYes = { onRemove(w) }) { removing = null } }
}

/** How the week went, as weather: Hearth's guess has a ring; "Geen akkoord" is an answer too. Can be switched off. */
class WeekWeather(val on: Boolean, val rating: WeekRating?, val suggested: Ease, val onRate: (Ease?, Ease) -> Unit, val onSwitch: (Boolean) -> Unit)

/**
 * Terugblik: "Hoe liep het?" The week, every routine (in the order of the day) and every house rule on one weather
 * scale, decided together: zon (loopt vanzelf), wolk (vraagt moeite), bliksem (te zwaar). Never who. Not running
 * easily? A smaller first step, right there. A house rule that is too much two meetings in a row comes back as a friction.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewSection(
    routines: List<BacklogItem>,
    rules: List<BoardItem>,
    checks: List<RuleCheck>,
    lang: Lang,
    onRateRoutine: (BacklogItem, Ease) -> Unit,
    onRateRule: (BoardItem, Ease) -> Unit,
    onSmallStep: (BacklogItem, String) -> Unit,
    week: WeekWeather? = null,
    coach: HabitCoaching? = null,
) {
    var asking by remember { mutableStateOf<BacklogItem?>(null) }
    var pairing by remember { mutableStateOf<BacklogItem?>(null) }
    GroupTitle(stringResource(Res.string.review_title))
    Text(stringResource(Res.string.review_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))

    if (week != null) {
        if (week.on) {
            val rating = week.rating
            val ease = rating?.ease
            Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 10.dp)) {
                Text(stringResource(Res.string.week_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.week_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    WeatherScale(ease, { week.onRate(it, week.suggested) }, guess = week.suggested)
                    val status = when {
                        rating == null -> stringResource(Res.string.week_guess, week.suggested.weekWord(lang))
                        ease == null -> stringResource(Res.string.week_neutral)
                        else -> stringResource(Res.string.week_agreed, ease.weekWord(lang))
                    }
                    Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 6.dp))
                    if (rating == null) TextButton(onClick = { week.onRate(week.suggested, week.suggested) }) { Text(stringResource(Res.string.week_ok)) }
                    if (rating == null || ease != null) TextButton(onClick = { week.onRate(null, week.suggested) }) { Text(stringResource(Res.string.week_no_agreement)) }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                Text(stringResource(Res.string.week_off), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { week.onSwitch(true) }) { Text(stringResource(Res.string.week_switch_on)) }
            }
        }
    }

    if (routines.isEmpty() && rules.isEmpty()) {
        Text(stringResource(Res.string.review_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp, top = 4.dp))
    }

    @Composable
    fun Heading(topic: Topic) = Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 2.dp)) {
        Icon(topic.icon(), null, tint = topic.style().ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(topic.title(lang), style = MaterialTheme.typography.labelLarge, color = topic.style().ink)
    }

    @Composable
    fun Scale(current: Ease?, onRate: (Ease) -> Unit, extra: @Composable () -> Unit = {}) =
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
            WeatherScale(current, onRate)
            current?.let { Text(stringResource(it.label()), style = MaterialTheme.typography.labelMedium, color = easeColors(it).first, modifier = Modifier.align(Alignment.CenterVertically)) }
            extra()
        }

    if (routines.isNotEmpty()) {
        Heading(Topic.RHYTHM)
        routines.forEach { r ->
            val review = coach?.reviews?.get(r.id)
            Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 8.dp)) {
                Text(r.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        r.anchor?.title(lang),
                        r.pairing?.let { stringResource(Res.string.pairing_line, it) },
                        r.smallStep?.let { stringResource(Res.string.small_step_line, it) },
                    ).joinToString("  ·  ").ifEmpty { stringResource(Res.string.anchor_none) },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (r.smallStep != null || r.pairing != null) Topic.RHYTHM.style().ink else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (coach == null) {
                    Scale(r.ease, { onRateRoutine(r, it) }) {
                        if (r.ease != null && r.ease != Ease.EASY) TextButton(onClick = { asking = r }) { Text(stringResource(Res.string.small_step_add)) }
                    }
                } else {
                    // This meeting's weather, with last time's as Hearth's ring; the earlier ones small beside it.
                    val now = coach.checks[r.id]
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                        WeatherScale(now, { onRateRoutine(r, it) }, guess = r.ease)
                        now?.let { Text(stringResource(it.label()), style = MaterialTheme.typography.labelMedium, color = easeColors(it).first, modifier = Modifier.align(Alignment.CenterVertically)) }
                        val before = review?.ratings.orEmpty().let { if (now != null) it.dropLast(1) else it }.takeLast(5)
                        if (before.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp)) {
                                Text(stringResource(Res.string.coach_before), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                before.forEach { WeatherMark(it, 14.dp) }
                            }
                        }
                    }
                    review?.frictions?.lastOrNull()?.let {
                        Text(stringResource(Res.string.coach_said, "“$it”"), style = MaterialTheme.typography.bodySmall, color = Topic.FRICTION.style().ink, modifier = Modifier.padding(top = 2.dp))
                    }
                    // One thing to try, when it's too much, takes effort, was talked about, or is ready to grow.
                    review?.tips?.firstOrNull()?.takeIf { review.stage != HabitStage.NEW && review.stage != HabitStage.STEADY }?.let { tip ->
                        CoachTip(tip, lang, onClick = {
                            when {
                                tip.action.oneTap -> coach.onTip(r, tip)
                                tip.action == TipAction.SMALL_STEP -> asking = r
                                tip.action == TipAction.PAIR -> pairing = r
                                else -> coach.onOpen(r)
                            }
                        }, modifier = Modifier.padding(top = 6.dp, end = 4.dp))
                    }
                }
            }
        }
    }
    if (rules.isNotEmpty()) {
        Heading(Topic.RULES)
        rules.forEach { rule ->
            Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 8.dp)) {
                Text(rule.text, style = MaterialTheme.typography.bodyLarge)
                Scale(checks.firstOrNull { it.ruleId == rule.id }?.ease, { onRateRule(rule, it) })
            }
        }
    }
    if (week?.on == true) {
        TextButton(onClick = { week.onSwitch(false) }) { Text(stringResource(Res.string.week_switch_off), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    asking?.let { h ->
        TextDialog(stringResource(Res.string.small_step_title), stringResource(Res.string.small_step_hint), onDone = { t, _ -> onSmallStep(h, t) }) { asking = null }
    }
    pairing?.let { h -> PairingDialog(h.pairing, { coach?.onPairing?.invoke(h, it) }) { pairing = null } }
}

/** The habit coach in the Terugblik: this meeting's weather per habit, the coach per habit, and what its tips do. */
class HabitCoaching(
    val checks: Map<String, Ease>,
    val reviews: Map<String, HabitReview>,
    val onTip: (BacklogItem, HabitTip) -> Unit,
    val onPairing: (BacklogItem, String) -> Unit,
    /** Tips that point at the routine's moment, days or steps open its sheet. */
    val onOpen: (BacklogItem) -> Unit,
)

/** Chores that slip and tasks that keep being postponed: smaller, easier or less often. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttentionList(
    items: List<Pair<BacklogItem, Ease>>,
    onLessOften: (BacklogItem) -> Unit,
    onEasier: (BacklogItem) -> Unit,
    onSmallStep: (BacklogItem, String) -> Unit,
) {
    if (items.isEmpty()) return
    var asking by remember { mutableStateOf<BacklogItem?>(null) }
    GroupTitle(stringResource(Res.string.attention_title))
    Text(stringResource(Res.string.attention_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
    items.forEach { (item, ease) ->
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                EasePill(ease, if (item.postponed >= 2 && !item.isRoutine()) stringResource(Res.string.ease_postponed, item.postponed) else null)
            }
            item.smallStep?.let { Text(stringResource(Res.string.small_step_line, it), style = MaterialTheme.typography.labelMedium, color = Section.HABIT.style().ink) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (item.isRoutine()) {
                    ChoiceChip(stringResource(Res.string.habit_less_often), false, { onLessOften(item) }, icon = HearthIcons.Later)
                    ChoiceChip(stringResource(Res.string.habit_easier), false, { onEasier(item) }, icon = HearthIcons.Idea)
                } else {
                    ChoiceChip(stringResource(Res.string.small_step_add), false, { asking = item }, icon = HearthIcons.Next)
                }
            }
        }
    }
    asking?.let { t ->
        TextDialog(stringResource(Res.string.small_step_title), stringResource(Res.string.small_step_hint), onDone = { s, _ -> onSmallStep(t, s) }) { asking = null }
    }
}

/** A meeting's summary as written at Klaar: the part on the left, or above it on a phone. */
@Composable
fun SummaryText(summary: String) {
    val lines = summary.lines().filter { it.isNotBlank() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 520.dp
        Column(verticalArrangement = Arrangement.spacedBy(if (stacked) 8.dp else 4.dp)) {
            lines.forEachIndexed { i, line ->
                val colon = line.indexOf(": ")
                if (i == 0) {
                    Text(line, style = MaterialTheme.typography.titleMedium)
                } else if (colon > 0 && stacked) {
                    Column {
                        Text(line.substring(0, colon), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(line.substring(colon + 2), style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (colon > 0) {
                    Row {
                        Text(line.substring(0, colon), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(190.dp).padding(top = 2.dp, end = 10.dp))
                        Text(line.substring(colon + 2), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                } else {
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
