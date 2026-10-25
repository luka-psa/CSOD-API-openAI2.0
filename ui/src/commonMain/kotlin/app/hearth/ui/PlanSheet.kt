package app.hearth.ui

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
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** Everything you can do with one item, shaped by its kind. Every choice is one tap and closes the sheet. */
class PlanActions(
    val plan: (When, LocalDate?, Boolean) -> Unit,
    val done: () -> Unit,
    val delete: () -> Unit,
    val interval: (Int?) -> Unit,
    val urgent: (Boolean) -> Unit,
    val waiting: (LocalDate?, Boolean) -> Unit,
    val move: (Section) -> Unit,
    val anchor: (Anchor?) -> Unit = {},
)

private val INTERVALS = listOf(1, 7, 14, 30, 90, null)

/**
 * Routines: how often, or give it a day. Admin: urgent, waiting for a reply, a deadline or a day.
 * Projects and steps: a day or the weekend. Unsorted items first ask where they belong.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanSheet(item: BacklogItem, today: LocalDate, lang: Lang, actions: PlanActions, onDismiss: () -> Unit) {
    var finishBy by remember(item.id) { mutableStateOf(item.finishBy) }
    var confirmDelete by remember { mutableStateOf(false) }
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
            Modifier.fillMaxWidth().widthIn(max = 760.dp).verticalScroll(rememberScrollState())
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
                    // The moment of the day it belongs to: the morning routine, around dinner, bedtime.
                    Text(stringResource(Res.string.moment_title), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Anchor.entries.forEach { a -> ChoiceChip(a.title(lang), item.anchor == a, { close { actions.anchor(a) } }) }
                        ChoiceChip(stringResource(Res.string.anchor_none), item.anchor == null, { close { actions.anchor(null) } })
                    }
                    Text(stringResource(Res.string.how_often), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        INTERVALS.forEach { days ->
                            ChoiceChip(intervalLabel(days, lang).replaceFirstChar { it.uppercase() }, item.intervalDays == days, { close { actions.interval(days) } })
                        }
                    }
                    Text(stringResource(Res.string.do_on_day), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DayChips(::onPlannedDay, ::planDay)
                        if (item.plan == Plan.DAY) ChoiceChip(stringResource(Res.string.plan_unplanned), false, { choose(When.UNPLANNED) }, icon = HearthIcons.Inbox)
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
