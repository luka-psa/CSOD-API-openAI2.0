package app.hearth.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import app.hearth.core.model.TopicGroup
import app.hearth.core.model.topics
import app.hearth.ui.resources.add
import app.hearth.ui.resources.transcript_export
import app.hearth.ui.resources.wins_walk_hint
import app.hearth.ui.resources.wins_done_count
import app.hearth.ui.resources.wins_named_count
import app.hearth.ui.resources.wins_tap
import app.hearth.ui.resources.wins_topic_title
import app.hearth.ui.resources.wins_seen
import app.hearth.ui.resources.wins_topic_empty
import app.hearth.ui.resources.wins_own_words
import app.hearth.ui.resources.wins_done
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import app.hearth.core.model.easeOf
import app.hearth.core.model.scoreNow
import app.hearth.core.model.scoreWord
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
import app.hearth.ui.resources.voorstel_reading
import app.hearth.ui.resources.voorstel_reading_part
import app.hearth.ui.resources.voorstel_update
import app.hearth.ui.resources.voorstel_there
import app.hearth.ui.resources.voorstel_as_new
import app.hearth.ui.resources.transcript_fix_title
import app.hearth.ui.resources.words_title
import app.hearth.ui.resources.words_forget_title
import app.hearth.ui.resources.words_forget_body
import app.hearth.core.model.WordFix
import androidx.compose.material3.LinearProgressIndicator
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

/**
 * What Luister mee heard, line by line, as it was said. A tap on a line of this meeting fixes what was misheard, and
 * Hearth remembers those words ([words], each forgotten with an X that asks first).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TranscriptSheet(
    title: String,
    lines: List<TranscriptLine>,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
    onExport: (() -> Unit)? = null,
    onFix: ((TranscriptLine, String) -> Unit)? = null,
    words: List<WordFix> = emptyList(),
    onForget: ((String) -> Unit)? = null,
) {
    var clearing by remember { mutableStateOf(false) }
    var fixing by remember { mutableStateOf<TranscriptLine?>(null) }
    var forgetting by remember { mutableStateOf<WordFix?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.nestedScroll(SheetScrollStop).fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding().padding(horizontal = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                // The conversation and what Hearth made of it, as a file, to look at how well it understood.
                if (onExport != null && lines.isNotEmpty()) TextButton(onClick = onExport) { Text(stringResource(Res.string.transcript_export)) }
                if (onClear != null && lines.isNotEmpty()) TextButton(onClick = { clearing = true }) { Text(stringResource(Res.string.transcript_clear)) }
            }
            Text(stringResource(Res.string.transcript_kept), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(12.dp))
            if (lines.isEmpty()) EmptyHint(stringResource(Res.string.transcript_empty))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                itemsIndexed(lines) { _, line ->
                    val fixable = onFix != null && line.huddleId == null
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).then(if (fixable) Modifier.clickable { fixing = line } else Modifier).padding(vertical = 2.dp)) {
                        Text(clock(line.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp).padding(top = 3.dp))
                        Text(line.text, style = MaterialTheme.typography.bodyLarge, color = if (line.sorted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    }
                }
                if (words.isNotEmpty() && onForget != null) {
                    item {
                        Column(Modifier.padding(top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(Res.string.words_title), style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                words.forEach { w ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).padding(start = 12.dp)) {
                                        Text("“${w.heard}” → ${w.meant}", style = MaterialTheme.typography.labelLarge)
                                        DeleteButton({ forgetting = w })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (clearing && onClear != null) {
        ConfirmDialog(stringResource(Res.string.transcript_clear), stringResource(Res.string.transcript_clear_body), onYes = onClear) { clearing = false }
    }
    fixing?.let { line ->
        TextDialog(stringResource(Res.string.transcript_fix_title), line.text, initial = line.text, onDone = { t, _ -> onFix?.invoke(line, t) }) { fixing = null }
    }
    forgetting?.let { w ->
        ConfirmDialog(stringResource(Res.string.words_forget_title), stringResource(Res.string.words_forget_body, w.heard), onYes = { onForget?.invoke(w.heard) }) { forgetting = null }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
private fun clock(millis: Long): String =
    formatClock(kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()))

/**
 * The conversation sorted into places, after Hearth read it as a whole ([progress] while it reads): new things and
 * changes to what exists ticked, what is there already not; one tap adds it all. Just talk is folded at the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoorstelSheet(
    rows: List<VoorstelRow>,
    progress: Pair<Int, Int>?,
    today: LocalDate,
    lang: Lang,
    onMove: (Int, Place) -> Unit,
    onToggle: (Int) -> Unit,
    onEdit: (Int, SuggestionChange) -> Unit,
    onAsNew: (Int) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val count = rows.count { it.checked }
    var talkOpen by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.nestedScroll(SheetScrollStop).fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.voorstel_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(Res.string.voorstel_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onAdd, enabled = count > 0 && progress == null) { Text(stringResource(Res.string.voorstel_add, count)) }
            }
            Spacer(Modifier.size(12.dp))
            if (progress != null) {
                // Reading the whole conversation takes a moment on the tablet: say how far it is.
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.voorstel_reading), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { if (progress.second == 0) 0f else progress.first.toFloat() / progress.second }, modifier = Modifier.fillMaxWidth())
                    if (progress.second > 1) Text(stringResource(Res.string.voorstel_reading_part, minOf(progress.first + 1, progress.second), progress.second), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }
            if (rows.none { it.proposal.place != Place.NONE }) EmptyHint(stringResource(Res.string.voorstel_empty))
            // Grouped by topic, the same name, icon and colour as on the stage and the tiles; wins, then just talk, last.
            fun group(r: VoorstelRow) = r.proposal.topic?.ordinal ?: (Topic.entries.size + if (r.proposal.place == Place.NONE) Place.entries.size else r.proposal.place.ordinal)
            val order = rows.withIndex().sortedBy { (_, r) -> group(r) }
            val talk = rows.count { it.proposal.place == Place.NONE }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(order) { n, (index, row) ->
                    val place = row.proposal.place
                    val topic = row.proposal.topic
                    val quiet = place == Place.NONE
                    if (n == 0 || group(order[n - 1].value) != group(row)) {
                        val ink = topic?.style()?.ink ?: place.section().style().ink
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp).then(if (quiet) Modifier.clip(MaterialTheme.shapes.small).clickable { talkOpen = !talkOpen } else Modifier),
                        ) {
                            Icon(topic?.icon() ?: place.icon(), null, tint = ink, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text((topic?.title(lang) ?: stringResource(place.label())) + if (quiet) " ($talk)" else "", style = MaterialTheme.typography.titleSmall, color = ink)
                            if (quiet) Icon(HearthIcons.ChevronRight, null, tint = ink, modifier = Modifier.padding(start = 4.dp).size(18.dp).rotate(if (talkOpen) 90f else 0f))
                        }
                    }
                    if (!quiet || talkOpen) VoorstelLine(row, today, lang, onMove = { onMove(index, it) }, onToggle = { onToggle(index) }, onEdit = { onEdit(index, it) }, onAsNew = { onAsNew(index) })
                }
            }
        }
    }
}

@Composable
private fun VoorstelLine(row: VoorstelRow, today: LocalDate, lang: Lang, onMove: (Place) -> Unit, onToggle: () -> Unit, onEdit: (SuggestionChange) -> Unit, onAsNew: () -> Unit) {
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
            // About something the household has: what it changes, or that it is there already.
            p.match?.let { m ->
                Text(
                    stringResource(if (m.update) Res.string.voorstel_update else Res.string.voorstel_there, m.text),
                    style = MaterialTheme.typography.labelLarge,
                    color = p.topic?.style()?.ink ?: MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            val day = p.date?.let { dayLabel(it, today, lang) } ?: if (p.next) stringResource(Res.string.heard_this_week) else null
            val heard = if (p.text.trim().lowercase() != p.heard.trim().lowercase()) "“${p.heard}”" else null
            listOfNotNull(day, heard).joinToString("  ·  ").takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        if (p.match != null && !row.added) TextButton(onClick = onAsNew) { Text(stringResource(Res.string.voorstel_as_new)) }
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

/** What one topic brings to Wat ging goed: what got done there since the last meeting, and wins to name in one tap. */
class TopicLookBack(val done: Int = 0, val suggestions: List<String> = emptyList())

/**
 * "Wat ging goed bij Huishouden?": what is already named there (X asks first), Hearth's suggestions as one-tap wins,
 * and a line to say it in your own words. Stays open to name several; typed text is never lost by a swipe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WinSheet(topic: Topic, look: TopicLookBack, named: List<Win>, lang: Lang, onAdd: (String) -> Unit, onRemove: (Win) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val s = topic.style()
    FormSheet(dirty = text.isNotBlank(), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(s.tint), contentAlignment = Alignment.Center) {
                    Icon(topic.icon(), null, tint = s.ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(Res.string.wins_topic_title, topic.title(lang)), style = MaterialTheme.typography.titleLarge)
            }
            named.forEach { win ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(HearthIcons.Check, null, tint = s.ink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(win.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    DeleteButton({ onRemove(win) })
                }
            }
            val open = look.suggestions.filter { sug -> named.none { it.text.equals(sug, ignoreCase = true) } }
            if (open.isNotEmpty()) {
                Text(stringResource(Res.string.wins_seen), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    open.take(12).forEach { sug -> ChoiceChip(sug, false, { onAdd(sug) }, icon = HearthIcons.Add) }
                }
            } else if (named.isEmpty()) {
                Text(stringResource(Res.string.wins_topic_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                text, { text = it },
                placeholder = { Text(stringResource(Res.string.wins_own_words)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { onAdd(text.trim()); text = "" }, enabled = text.isNotBlank()) { Text(stringResource(Res.string.add)) }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.wins_done)) }
            }
        }
    }
}

/** How the week went, 0 to 5: Hearth's guess has a ring; "Geen akkoord" is an answer too. Can be switched off. */
class WeekWeather(val on: Boolean, val rating: WeekRating?, val suggested: Int, val onRate: (Int?, Int) -> Unit, val onSwitch: (Boolean) -> Unit)

/** "De week": Hearth's guess as a ring on 0 to 5, agreed together; "Geen akkoord" is an answer too. Can be switched off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekWeatherRow(week: WeekWeather, lang: Lang) {
    if (!week.on) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
            Text(stringResource(Res.string.week_off), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { week.onSwitch(true) }) { Text(stringResource(Res.string.week_switch_on)) }
        }
        return
    }
    val rating = week.rating
    val score = rating?.score
    Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 6.dp)) {
        Text(stringResource(Res.string.week_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(Res.string.week_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            ScoreScale(score, { week.onRate(it, week.suggested) }, guess = week.suggested)
            val status = when {
                rating == null -> stringResource(Res.string.week_guess, weekWord(week.suggested, lang))
                score == null -> stringResource(Res.string.week_neutral)
                else -> stringResource(Res.string.week_agreed, weekWord(score, lang))
            }
            Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 6.dp))
            if (rating == null) TextButton(onClick = { week.onRate(week.suggested, week.suggested) }) { Text(stringResource(Res.string.week_ok)) }
            if (rating == null || score != null) TextButton(onClick = { week.onRate(null, week.suggested) }) { Text(stringResource(Res.string.week_no_agreement)) }
            TextButton(onClick = { week.onSwitch(false) }) { Text(stringResource(Res.string.week_switch_off), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

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
