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
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardItem
import app.hearth.core.model.HabitHealth
import app.hearth.core.model.Meter
import app.hearth.core.model.MeterValue
import app.hearth.core.model.RuleCheck
import app.hearth.core.model.intervalLabel
import app.hearth.core.model.title
import app.hearth.core.model.word
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import app.hearth.ui.resources.habit_easier
import app.hearth.ui.resources.habit_less_often
import app.hearth.ui.resources.habits_attention
import app.hearth.ui.resources.habits_attention_hint
import app.hearth.ui.resources.meters_agreed
import app.hearth.ui.resources.meters_guess
import app.hearth.ui.resources.meters_hint
import app.hearth.ui.resources.meters_neutral
import app.hearth.ui.resources.meters_no_agreement
import app.hearth.ui.resources.meters_off
import app.hearth.ui.resources.meters_ok
import app.hearth.ui.resources.meters_switch_off
import app.hearth.ui.resources.meters_switch_on
import app.hearth.ui.resources.meters_title
import app.hearth.ui.resources.rule_not_yet
import app.hearth.ui.resources.rule_worked
import app.hearth.ui.resources.rules_check_title
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

/** On other screens while Luister mee runs: it's still listening, and one tap goes back to the meeting. */
@Composable
fun ListenPill(lines: Int, partial: String, onBack: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val ink = Section.PROTECTED_TIME.style()
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

/**
 * The two household meters, agreed together. Hearth's guess has a ring; a tap on a step is what the family
 * agreed; "Geen akkoord" leaves it neutral. Can be switched off when it causes more friction than it helps.
 */
@Composable
fun MetersSection(
    on: Boolean,
    meters: List<MeterValue>,
    suggestedFlow: Int,
    suggestedStrain: Int,
    lang: Lang,
    onSet: (Meter, Int?, Int?) -> Unit,
    onSwitch: (Boolean) -> Unit,
) {
    if (!on) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.meters_off), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { onSwitch(true) }) { Text(stringResource(Res.string.meters_switch_on)) }
        }
        return
    }
    GroupTitle(stringResource(Res.string.meters_title))
    Text(stringResource(Res.string.meters_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
    listOf(Meter.FLOW to suggestedFlow, Meter.STRAIN to suggestedStrain).forEach { (meter, guess) ->
        val set = meters.firstOrNull { it.meter == meter }
        val ink = if (meter == Meter.FLOW) Section.CHORE.style().ink else Section.FRICTION.style().ink
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meter.title(lang) + "?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(140.dp))
                (1..5).forEach { n ->
                    val chosen = set != null && n <= set.value
                    val isGuess = n == guess
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(46.dp).clip(CircleShape)
                            .then(if (isGuess) Modifier.border(2.dp, ink.copy(alpha = 0.5f), CircleShape) else Modifier)
                            .clickable { onSet(meter, n, guess) },
                    ) {
                        Icon(
                            if (meter == Meter.FLOW) HearthIcons.Sun else HearthIcons.Bolt, n.toString(),
                            // Not agreed yet: Hearth's guess shows faintly.
                            tint = when {
                                chosen -> ink
                                set == null && n <= guess -> ink.copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(if (chosen) 26.dp else 22.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 140.dp)) {
                val status = when {
                    set == null -> stringResource(Res.string.meters_guess, meter.word(guess, lang))
                    !set.agreed -> stringResource(Res.string.meters_neutral)
                    else -> stringResource(Res.string.meters_agreed, meter.word(set.value, lang))
                }
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (set == null) TextButton(onClick = { onSet(meter, guess, guess) }) { Text(stringResource(Res.string.meters_ok)) }
                TextButton(onClick = { onSet(meter, null, guess) }) { Text(stringResource(Res.string.meters_no_agreement)) }
            }
        }
    }
    TextButton(onClick = { onSwitch(false) }) { Text(stringResource(Res.string.meters_switch_off), color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** "Hoe liepen de afspraken?": per house rule, for the household. Never who. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleChecks(rules: List<BoardItem>, checks: List<RuleCheck>, onCheck: (BoardItem, Boolean) -> Unit) {
    if (rules.isEmpty()) return
    GroupTitle(stringResource(Res.string.rules_check_title))
    rules.forEach { rule ->
        val check = checks.firstOrNull { it.ruleId == rule.id }
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(HearthIcons.Rule, null, tint = Section.NOTE.style().ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(rule.text, style = MaterialTheme.typography.bodyLarge)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                ChoiceChip(stringResource(Res.string.rule_worked), check?.worked == true, { onCheck(rule, true) }, icon = HearthIcons.Check)
                ChoiceChip(stringResource(Res.string.rule_not_yet), check?.worked == false, { onCheck(rule, false) })
            }
        }
    }
}

/**
 * Habits done much less often than their rhythm asks. Atomic Habits: make it smaller, easier or less
 * often, rather than trying harder.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitsAttention(habits: List<BacklogItem>, lang: Lang, onLessOften: (BacklogItem) -> Unit, onEasier: (BacklogItem) -> Unit) {
    if (habits.isEmpty()) return
    GroupTitle(stringResource(Res.string.habits_attention))
    Text(stringResource(Res.string.habits_attention_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
    habits.forEach { habit ->
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitDot(HabitHealth.NEEDS_ATTENTION)
                Spacer(Modifier.width(10.dp))
                Text(habit.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(intervalLabel(habit.intervalDays, lang), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 20.dp, top = 4.dp)) {
                ChoiceChip(stringResource(Res.string.habit_less_often), false, { onLessOften(habit) }, icon = HearthIcons.Later)
                ChoiceChip(stringResource(Res.string.habit_easier), false, { onEasier(habit) }, icon = HearthIcons.Idea)
            }
        }
    }
}

/** A habit's colour: green runs easily, amber asks for attention. Never red. */
@Composable
fun HabitDot(health: HabitHealth?) {
    val color = when (health) {
        HabitHealth.EASY -> MaterialTheme.colorScheme.secondary
        HabitHealth.NEEDS_ATTENTION -> Section.RHYTHM.style().ink
        else -> return
    }
    Box(Modifier.size(10.dp).background(color, CircleShape))
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
                    Text(line.substring(0, colon), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(190.dp).padding(top = 2.dp, end = 10.dp))
                    Text(line.substring(colon + 2), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            } else {
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
