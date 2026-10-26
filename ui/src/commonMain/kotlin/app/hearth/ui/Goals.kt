package app.hearth.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.hearth.core.model.Anchor
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardItem
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.model.Topic
import app.hearth.core.model.dayLabel
import app.hearth.core.model.intervalLabel
import app.hearth.core.model.plusDays
import app.hearth.core.model.title
import app.hearth.core.model.topic
import app.hearth.core.tools.board.GoalPlanKind
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.anchor_none
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.goal_plan_add
import app.hearth.ui.resources.goal_plan_day
import app.hearth.ui.resources.goal_plan_free
import app.hearth.ui.resources.goal_plan_free_hint
import app.hearth.ui.resources.goal_plan_habit
import app.hearth.ui.resources.goal_plan_habit_hint
import app.hearth.ui.resources.goal_plan_often
import app.hearth.ui.resources.goal_plan_step
import app.hearth.ui.resources.goal_plan_title
import app.hearth.ui.resources.goal_step_to_week
import app.hearth.ui.resources.goal_was
import app.hearth.ui.resources.friction_text_hint
import app.hearth.ui.resources.moment_title
import app.hearth.ui.resources.next_step
import app.hearth.ui.resources.save
import app.hearth.ui.theme.style
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

// A goal comes into the week the way it fits. Often it's a rhythm ("elke dag yoga", in Ritmes & routines) or free
// time for a hobby (in Vrije tijd, on a day); otherwise one small step. The goal row shows that link in the
// colour of where it lives, so a goal is never just a wish on the Werkbord.

/** How the family wants to bring a goal into the week, in their own words. */
data class GoalPlan(
    val how: GoalPlanKind,
    val text: String,
    val anchor: Anchor? = null,
    val intervalDays: Int = 1,
    val date: LocalDate? = null,
    val toWeek: Boolean = false,
)

/**
 * A goal: tick it when it's reached. Under it, how it lives in the week: the rhythm with its moment (and how it
 * goes), the free time with its day, or the next small step. Tap that line to change it.
 */
@Composable
internal fun GoalRow(goal: BoardItem, linked: BacklogItem?, today: LocalDate, lang: Lang, onAchieve: () -> Unit, onPlan: () -> Unit, onDelete: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        RoundCheck(false, onAchieve)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f).padding(top = 10.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(goal.text, style = MaterialTheme.typography.bodyLarge)
            val step = goal.nextStep
            val topic = linked?.takeIf { !it.done }?.topic
            when {
                linked != null && !linked.done && (topic == Topic.RHYTHM || topic == Topic.FREE_TIME) -> {
                    val s = topic.style()
                    val detail = if (topic == Topic.RHYTHM) listOfNotNull(linked.anchor?.title(lang), intervalLabel(linked.intervalDays, lang)).joinToString(" · ")
                    else linked.localDate?.let { dayLabel(it, today, lang) } ?: ""
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onPlan).padding(vertical = 4.dp),
                    ) {
                        Icon(topic.icon(), null, tint = s.ink, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${linked.text}  ·  $detail", style = MaterialTheme.typography.labelMedium, color = s.ink)
                        linked.ease?.let { Spacer(Modifier.width(8.dp)); EasePill(it) }
                    }
                }
                step != null -> Text(
                    stringResource(Res.string.next_step, step),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onPlan).padding(vertical = 4.dp),
                )
                else -> TextButton(onClick = onPlan) {
                    Icon(HearthIcons.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.goal_plan_add), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (onDelete != null) DeleteButton(onDelete, Modifier.padding(top = 2.dp))
    }
}

/**
 * "Hoe krijgt het een plek in de week?" A rhythm (what, at which moment, how often), free time (what, on which day)
 * or one small step. Typed text is never lost by a tap outside.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalPlanDialog(goal: BoardItem, today: LocalDate, lang: Lang, onSave: (GoalPlan) -> Unit, onDismiss: () -> Unit) {
    var how by remember { mutableStateOf(GoalPlanKind.HABIT) }
    var text by remember { mutableStateOf("") }
    var anchor by remember { mutableStateOf<Anchor?>(null) }
    var interval by remember { mutableStateOf(1) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    var toWeek by remember { mutableStateOf(true) }
    var asking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val ready = text.isNotBlank() && (how != GoalPlanKind.FREE_TIME || day != null)
    val save = {
        onSave(GoalPlan(how, text.trim(), anchor, interval, day, toWeek))
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = { if (text.isNotBlank()) asking = true else onDismiss() },
        title = { Text(stringResource(Res.string.goal_plan_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.goal_was, goal.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(stringResource(Res.string.goal_plan_habit), how == GoalPlanKind.HABIT, { how = GoalPlanKind.HABIT }, icon = Topic.RHYTHM.icon())
                    ChoiceChip(stringResource(Res.string.goal_plan_free), how == GoalPlanKind.FREE_TIME, { how = GoalPlanKind.FREE_TIME }, icon = Topic.FREE_TIME.icon())
                    ChoiceChip(stringResource(Res.string.goal_plan_step), how == GoalPlanKind.STEP, { how = GoalPlanKind.STEP }, icon = HearthIcons.Next)
                }
                val hint = when (how) {
                    GoalPlanKind.HABIT -> Res.string.goal_plan_habit_hint
                    GoalPlanKind.FREE_TIME -> Res.string.goal_plan_free_hint
                    GoalPlanKind.STEP -> Res.string.friction_text_hint
                }
                OutlinedTextField(text, { text = it }, placeholder = { Text(stringResource(hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
                when (how) {
                    GoalPlanKind.HABIT -> {
                        Text(stringResource(Res.string.moment_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Anchor.entries.forEach { a -> ChoiceChip(a.title(lang), anchor == a, { anchor = if (anchor == a) null else a }) }
                            ChoiceChip(stringResource(Res.string.anchor_none), anchor == null, { anchor = null })
                        }
                        Text(stringResource(Res.string.goal_plan_often), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 7).forEach { d -> ChoiceChip(intervalLabel(d, lang).replaceFirstChar { it.uppercase() }, interval == d, { interval = d }) }
                        }
                    }
                    GoalPlanKind.FREE_TIME -> {
                        Text(stringResource(Res.string.goal_plan_day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            (0 until 7).map { today.plusDays(it) }.forEach { d ->
                                ChoiceChip(dayLabel(d, today, lang).replaceFirstChar { it.uppercase() }, day == d, { day = d })
                            }
                        }
                    }
                    GoalPlanKind.STEP -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { toWeek = !toWeek }) {
                        Checkbox(toWeek, { toWeek = it })
                        Text(stringResource(Res.string.goal_step_to_week), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { FilledTonalButton(onClick = save, enabled = ready) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
    if (asking) DiscardDialog(onKeep = { asking = false }, onDiscard = { asking = false; onDismiss() })
}
