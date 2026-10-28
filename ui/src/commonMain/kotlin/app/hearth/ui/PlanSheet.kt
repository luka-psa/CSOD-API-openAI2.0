package app.hearth.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import app.hearth.ui.resources.small_step_line
import app.hearth.ui.resources.pairing_line
import app.hearth.ui.resources.tip_pair
import app.hearth.ui.resources.small_step_add
import app.hearth.ui.resources.routine_setup
import app.hearth.ui.resources.fold
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import app.hearth.core.model.HabitReview
import app.hearth.core.model.isHabit
import app.hearth.core.model.lawTips
import app.hearth.core.model.HabitTip
import app.hearth.core.model.TipAction
import app.hearth.ui.resources.small_step_hint
import app.hearth.ui.resources.small_step_title
import kotlinx.coroutines.launch
import app.hearth.ui.resources.routine_step_add
import app.hearth.ui.resources.routine_steps
import app.hearth.ui.resources.step_down
import app.hearth.ui.resources.step_up
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.rotate
import app.hearth.ui.resources.anchor_none
import app.hearth.ui.resources.moment_title
import app.hearth.core.model.title
import app.hearth.core.model.Anchor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.dp
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.TaskKind
import app.hearth.core.model.intervalLabel
import app.hearth.core.model.plusDays
import app.hearth.core.model.weekdayName
import app.hearth.core.tools.backlog.When
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.delete
import app.hearth.ui.resources.delete_confirm
import app.hearth.ui.resources.delete_confirm_body
import app.hearth.ui.resources.delete_confirm_project
import app.hearth.ui.resources.do_on_day
import app.hearth.ui.resources.follow_up_on
import app.hearth.ui.resources.how_often
import app.hearth.ui.resources.kind_admin
import app.hearth.ui.resources.kind_appointment
import app.hearth.ui.resources.kind_friction
import app.hearth.ui.resources.kind_goal
import app.hearth.ui.resources.kind_note
import app.hearth.ui.resources.kind_project
import app.hearth.ui.resources.kind_routine
import app.hearth.ui.resources.mark_done
import app.hearth.ui.resources.mark_urgent
import app.hearth.ui.resources.move_to
import app.hearth.ui.resources.no
import app.hearth.ui.resources.plan_do_on
import app.hearth.ui.resources.plan_finish_by
import app.hearth.ui.resources.plan_later
import app.hearth.ui.resources.plan_next
import app.hearth.ui.resources.plan_title
import app.hearth.ui.resources.plan_today
import app.hearth.ui.resources.plan_tomorrow
import app.hearth.ui.resources.plan_unplanned
import app.hearth.ui.resources.plan_weekend
import app.hearth.ui.resources.reply_in
import app.hearth.ui.resources.wait_reply
import app.hearth.ui.resources.yes
import kotlinx.datetime.DayOfWeek
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import app.hearth.core.model.Schedule
import app.hearth.core.model.Topic
import app.hearth.ui.theme.style
import app.hearth.ui.resources.fixed_days
import app.hearth.ui.resources.fixed_days_hint
import app.hearth.ui.resources.fixed_time
import app.hearth.ui.resources.fixed_time_title
import app.hearth.ui.resources.fixed_time_none
import app.hearth.ui.resources.save
import app.hearth.ui.resources.cancel
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Everything you can do with one item, shaped by its kind. A plan is one tap and closes the sheet; a routine's
 * moment, rhythm, days and steps change in place, so several can be set in one go.
 */
class PlanActions(
    val plan: (When, LocalDate?, Boolean) -> Unit,
    val done: () -> Unit,
    val delete: () -> Unit,
    val interval: (Int?) -> Unit,
    val urgent: (Boolean) -> Unit,
    val waiting: (LocalDate?, Boolean) -> Unit,
    val move: (Section) -> Unit,
    val anchor: (Anchor?) -> Unit = {},
    val addStep: (String) -> Unit = {},
    val removeStep: (BacklogItem) -> Unit = {},
    /** Puts a step at a new place (1 = first). */
    val moveStep: (BacklogItem, Int) -> Unit = { _, _ -> },
    /** Fixed days (and maybe a time); null: back to roughly every week. */
    val schedule: (Schedule?) -> Unit = {},
    /** Habits: something nice with it (empty removes it). */
    val pairing: (String) -> Unit = {},
    /** Habits: a smaller first step (empty removes it). */
    val smallStep: (String) -> Unit = {},
    /** Habits: a coach tip that needs no words (less or more often, the whole version, Wat ging goed). */
    val tip: (HabitTip) -> Unit = {},
    /** Habits: how it goes, 0–5. */
    val rate: (Int) -> Unit = {},
)

private val INTERVALS = listOf(1, 7, 14, 30, 90, null)

/**
 * Routines: how often, or give it a day. Admin: urgent, waiting for a reply, a deadline or a day.
 * Projects and steps: a day or the weekend. Unsorted items first ask where they belong.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlanSheet(
    item: BacklogItem,
    today: LocalDate,
    lang: Lang,
    actions: PlanActions,
    onDismiss: () -> Unit,
    steps: List<BacklogItem> = emptyList(),
    review: HabitReview? = null,
) {
    var finishBy by remember(item.id) { mutableStateOf(item.finishBy) }
    var pairing by remember { mutableStateOf(false) }
    var smallStep by remember { mutableStateOf(false) }
    // A tip that points at a part of this sheet (a moment, a day, the steps) scrolls to it.
    val scope = rememberCoroutineScope()
    val stepsAt = remember { BringIntoViewRequester() }
    val momentAt = remember { BringIntoViewRequester() }
    val daysAt = remember { BringIntoViewRequester() }
    fun show(at: BringIntoViewRequester) { scope.launch { at.bringIntoView() } }
    // A routine's set-up (moment, how often, days) folds away once it has a place in the day, so the sheet opens on
    // how it goes; a tip that points at it opens it again.
    var setUp by remember(item.id) { mutableStateOf(item.anchor == null && item.schedule == null) }
    // A routine reads in parts: Nu (steps, set-up), Hoe loopt het (habits: the score) and Verbeteren (the four laws).
    var tab by remember(item.id) { mutableStateOf(SheetTab.NOW) }
    fun showSetUp(at: BringIntoViewRequester) { tab = SheetTab.NOW; setUp = true; scope.launch { kotlinx.coroutines.delay(80); at.bringIntoView() } }
    fun showSteps() { tab = SheetTab.NOW; scope.launch { kotlinx.coroutines.delay(80); stepsAt.bringIntoView() } }
    fun onTip(tip: HabitTip) = when (tip.action) {
        TipAction.PAIR -> pairing = true
        TipAction.SMALL_STEP -> smallStep = true
        TipAction.MOMENT -> showSetUp(momentAt)
        TipAction.DAYS -> showSetUp(daysAt)
        TipAction.FEWER_STEPS, TipAction.ADD_STEP -> showSteps()
        else -> actions.tip(tip)
    }
    var removingStep by remember { mutableStateOf<BacklogItem?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val kind = item.kind

    fun close(action: () -> Unit) {
        action()
        onDismiss()
    }

    fun choose(w: When, d: LocalDate? = null) = close {
        actions.plan(w, d, finishBy && kind != TaskKind.ROUTINE && (w == When.TODAY || w == When.TOMORROW || w == When.DAY))
    }

    val saturday = today.plusDays((DayOfWeek.SATURDAY.ordinal - today.dayOfWeek.ordinal + 7) % 7)
    val days = (2 until 7).map { today.plusDays(it) }

    @Composable
    fun DayChips(selectedOn: (LocalDate) -> Boolean, onDay: (LocalDate) -> Unit) {
        ChoiceChip(stringResource(Res.string.plan_today), selectedOn(today), { onDay(today) })
        ChoiceChip(stringResource(Res.string.plan_tomorrow), selectedOn(today.plusDays(1)), { onDay(today.plusDays(1)) })
        days.forEach { d ->
            val label = "${weekdayName(d.dayOfWeek, lang).replaceFirstChar { it.uppercase() }} ${d.day}"
            ChoiceChip(label, selectedOn(d), { onDay(d) })
        }
    }

    fun onPlannedDay(d: LocalDate) = item.plan == Plan.DAY && !item.waiting && item.localDate == d
    fun planDay(d: LocalDate) = when (d) {
        today -> choose(When.TODAY)
        today.plusDays(1) -> choose(When.TOMORROW)
        else -> choose(When.DAY, d)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.nestedScroll(SheetScrollStop).fillMaxWidth().widthIn(max = 760.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp).padding(bottom = 28.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.urgent) UrgentTag(lang)
                SectionTag(item, lang)
                statusText(item, today, lang)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(item.text, style = MaterialTheme.typography.headlineSmall)

            // Unsorted: where does it belong? That decides how it is planned.
            if (!item.isStep && kind == TaskKind.SORT) MoveRow(item, actions, ::close)

            when (kind) {
                TaskKind.ROUTINE -> {
                    val tabs = listOfNotNull(SheetTab.NOW, SheetTab.HOW.takeIf { item.isHabit && review != null }, SheetTab.BETTER.takeIf { review != null })
                    SheetTabs(tabs, tab) { tab = it }
                    when (tab) {
                        // How it goes: its score, how it moved, what was said, and only when it needs one, the one thing to try.
                        SheetTab.HOW -> review?.let { HabitCoach(it, lang, onRate = actions.rate, onTip = ::onTip) }
                        // The four laws, one card each: what is in place and one suggestion.
                        SheetTab.BETTER -> review?.let { r ->
                            LawCards(r.laws, lawTips(item, steps.size, lang), r.focus ?: r.tips.firstOrNull()?.law, Topic.RHYTHM.style(), lang, ::onTip)
                        }
                        SheetTab.NOW -> {}
                    }
                    if (tab == SheetTab.NOW) {
                    // Its steps, in order: a sequence for the evening or a checklist for the bag.
                    Text(stringResource(Res.string.routine_steps), style = MaterialTheme.typography.titleMedium, modifier = Modifier.bringIntoViewRequester(stepsAt))
                    // Numbered, with arrows to put them in the order the evening really goes.
                    steps.forEachIndexed { i, step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                            Text(step.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { actions.moveStep(step, i) }, enabled = i > 0) {
                                Icon(HearthIcons.ChevronRight, stringResource(Res.string.step_up), modifier = Modifier.rotate(-90f))
                            }
                            IconButton(onClick = { actions.moveStep(step, i + 2) }, enabled = i < steps.lastIndex) {
                                Icon(HearthIcons.ChevronRight, stringResource(Res.string.step_down), modifier = Modifier.rotate(90f))
                            }
                            DeleteButton({ removingStep = step })
                        }
                    }
                    AddRow(stringResource(Res.string.routine_step_add), actions.addStep, compact = true)
                    // Setting it up, folded: one line says where it hangs now ("Rond het eten · elke dag").
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { setUp = !setUp }.padding(vertical = 6.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.routine_setup), style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(item.anchor?.title(lang), item.schedule?.label(lang) ?: intervalLabel(item.intervalDays, lang)).joinToString("  ·  ").replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(HearthIcons.ChevronRight, stringResource(Res.string.fold), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(if (setUp) -90f else 90f))
                    }
                    if (setUp) {
                    // The moment of the day it belongs to: the morning routine, around dinner, bedtime.
                    Text(stringResource(Res.string.moment_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.bringIntoViewRequester(momentAt))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Anchor.entries.forEach { a -> ChoiceChip(a.title(lang), item.anchor == a, { actions.anchor(a) }) }
                        ChoiceChip(stringResource(Res.string.anchor_none), item.anchor == null, { actions.anchor(null) })
                    }
                    val schedule = item.schedule
                    Text(stringResource(Res.string.how_often), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        INTERVALS.forEach { days ->
                            ChoiceChip(intervalLabel(days, lang).replaceFirstChar { it.uppercase() }, schedule == null && item.intervalDays == days, { actions.interval(days) })
                        }
                    }
                    // Fixed days, for what really happens on a day (the bins, swimming lessons); a time only if it is fixed.
                    Text(stringResource(Res.string.fixed_days), style = MaterialTheme.typography.titleMedium, modifier = Modifier.bringIntoViewRequester(daysAt))
                    Text(
                        schedule?.label(lang)?.replaceFirstChar { it.uppercase() } ?: stringResource(Res.string.fixed_days_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (schedule != null) Topic.RHYTHM.style().ink else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val weekly = schedule?.takeIf { !it.monthly }?.weekdays.orEmpty()
                        DayOfWeek.entries.forEach { d ->
                            val on = d in weekly
                            ChoiceChip(weekdayName(d, lang, short = true).replaceFirstChar { it.uppercase() }, on, {
                                val days = if (on) weekly - d else weekly + d
                                actions.schedule(if (days.isEmpty()) null else Schedule(days.sorted(), time = schedule?.time))
                            })
                        }
                        if (schedule?.monthly == true) ChoiceChip(schedule.label(lang).substringBefore(" om "), true, { actions.schedule(null) }, icon = HearthIcons.Close)
                        ChoiceChip(schedule?.time ?: stringResource(Res.string.fixed_time), schedule?.time != null, { pickingTime = true }, icon = HearthIcons.Later)
                    }
                    Text(stringResource(Res.string.do_on_day), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DayChips(::onPlannedDay, ::planDay)
                        if (item.plan == Plan.DAY) ChoiceChip(stringResource(Res.string.plan_unplanned), false, { choose(When.UNPLANNED) }, icon = HearthIcons.Inbox)
                    }
                    }
                    }
                }
                else -> {
                    if (kind == TaskKind.ADMIN && !item.isStep) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceChip(stringResource(Res.string.mark_urgent), item.urgent, { close { actions.urgent(!item.urgent) } }, icon = HearthIcons.Flag)
                            if (item.waiting) {
                                ChoiceChip(stringResource(Res.string.reply_in), false, { close { actions.waiting(null, false) } }, icon = HearthIcons.Check)
                            }
                        }
                        Text(stringResource(if (item.waiting) Res.string.follow_up_on else Res.string.wait_reply), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(1, 3, 7).map { today.plusDays(it) }.forEach { d ->
                                val label = if (d == today.plusDays(1)) stringResource(Res.string.plan_tomorrow)
                                else "${weekdayName(d.dayOfWeek, lang).replaceFirstChar { it.uppercase() }} ${d.day}"
                                ChoiceChip(label, item.waiting && item.localDate == d, { close { actions.waiting(d, true) } }, icon = HearthIcons.Later)
                            }
                        }
                    }

                    Text(stringResource(Res.string.plan_title), style = MaterialTheme.typography.titleMedium)
                    if (kind == TaskKind.ADMIN || kind == TaskKind.SORT) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(selected = !finishBy, onClick = { finishBy = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                                Text(stringResource(Res.string.plan_do_on))
                            }
                            SegmentedButton(selected = finishBy, onClick = { finishBy = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                                Text(stringResource(Res.string.plan_finish_by))
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChoiceChip(stringResource(Res.string.plan_next), item.plan == Plan.NEXT, { choose(When.NEXT) }, icon = HearthIcons.Next)
                        if (kind == TaskKind.PROJECT && saturday != today) {
                            ChoiceChip(stringResource(Res.string.plan_weekend), onPlannedDay(saturday), { choose(When.DAY, saturday) }, icon = HearthIcons.Sun)
                        }
                        DayChips(::onPlannedDay, ::planDay)
                        ChoiceChip(stringResource(Res.string.plan_later), item.plan == Plan.LATER, { choose(When.LATER) }, icon = HearthIcons.Later)
                        if (item.plan != Plan.UNPLANNED) {
                            ChoiceChip(stringResource(Res.string.plan_unplanned), false, { choose(When.UNPLANNED) }, icon = HearthIcons.Inbox)
                        }
                    }
                }
            }

            if (!item.isStep && kind != TaskKind.SORT) MoveRow(item, actions, ::close)

            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { close(actions.done) }) {
                    Icon(HearthIcons.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(Res.string.mark_done))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(HearthIcons.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(Res.string.delete))
                }
            }
        }
    }

    if (pairing) PairingDialog(item.pairing, actions.pairing) { pairing = false }
    if (smallStep) {
        TextDialog(stringResource(Res.string.small_step_title), stringResource(Res.string.small_step_hint), onDone = { t, _ -> actions.smallStep(t) }) { smallStep = false }
    }
    if (pickingTime) {
        TimeDialog(
            initial = item.schedule?.time,
            onPick = { t ->
                val current = item.schedule
                // A time without days: every day at that time.
                actions.schedule(
                    when {
                        current != null -> current.copy(time = t).takeIf { it.isSet && (t != null || it.weekdays.size < 7 || it.monthly) }
                        t != null -> Schedule(DayOfWeek.entries.toList(), time = t)
                        else -> null
                    },
                )
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
    removingStep?.let { step ->
        ConfirmDialog(stringResource(Res.string.delete_confirm, step.text), stringResource(Res.string.delete_confirm_body), onYes = { actions.removeStep(step) }) { removingStep = null }
    }
    if (confirmDelete) {
        val isProject = kind == TaskKind.PROJECT && !item.isStep
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.delete_confirm, item.text)) },
            text = {
                Text(
                    if (isProject) stringResource(Res.string.delete_confirm_project) + " " + stringResource(Res.string.delete_confirm_body)
                    else stringResource(Res.string.delete_confirm_body),
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = { confirmDelete = false; close(actions.delete) }) { Text(stringResource(Res.string.yes)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.no)) } },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

/** "Hoort bij": Routine · Admin · Afspraak · Project · Huisregel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveRow(item: BacklogItem, actions: PlanActions, close: (() -> Unit) -> Unit) {
    val choices = listOf(
        Section.CHORE to Res.string.kind_routine,
        Section.ADMIN to Res.string.kind_admin,
        Section.APPOINTMENT to Res.string.kind_appointment,
        Section.PROJECT to Res.string.kind_project,
        Section.NOTE to Res.string.kind_note,
        Section.FRICTION to Res.string.kind_friction,
        Section.GOAL to Res.string.kind_goal,
    )
    Text(stringResource(Res.string.move_to), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.forEach { (section, label) ->
            val selected = item.section == section || (section == Section.CHORE && item.kind == TaskKind.ROUTINE)
            ChoiceChip(stringResource(label), selected, { if (!selected) close { actions.move(section) } })
        }
    }
}

/** A fixed time, typed as hours and minutes; "Geen vast uur" takes it away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val (h, m) = initial?.split(":")?.map { it.toIntOrNull() ?: 0 } ?: listOf(20, 0)
    val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.fixed_time_title)) },
        text = { TimeInput(state) },
        confirmButton = {
            FilledTonalButton(onClick = { onPick("${state.hour.toString().padStart(2, '0')}:${state.minute.toString().padStart(2, '0')}") }) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            if (initial != null) TextButton(onClick = { onPick(null) }) { Text(stringResource(Res.string.fixed_time_none)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
