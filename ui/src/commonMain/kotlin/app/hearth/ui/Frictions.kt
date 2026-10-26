package app.hearth.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.hearth.core.model.Anchor
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.FrictionDirection
import app.hearth.core.model.Lang
import app.hearth.core.model.Member
import app.hearth.core.model.Section
import app.hearth.core.model.Topic
import app.hearth.core.model.title
import app.hearth.core.tools.board.FrictionOutcome
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.anchor_none
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.dir_change_routine
import app.hearth.ui.resources.dir_change_routine_marked
import app.hearth.ui.resources.dir_new_routine
import app.hearth.ui.resources.dir_new_routine_marked
import app.hearth.ui.resources.dir_rule
import app.hearth.ui.resources.dir_rule_marked
import app.hearth.ui.resources.dir_task
import app.hearth.ui.resources.dir_task_marked
import app.hearth.ui.resources.friction_let_go
import app.hearth.ui.resources.friction_need
import app.hearth.ui.resources.friction_other
import app.hearth.ui.resources.friction_rule_title
import app.hearth.ui.resources.friction_was
import app.hearth.ui.resources.kind_admin
import app.hearth.ui.resources.kind_project
import app.hearth.ui.resources.moment_title
import app.hearth.core.model.HabitLaw
import app.hearth.core.model.lawOf
import app.hearth.ui.resources.change_how
import app.hearth.ui.resources.change_moment
import app.hearth.ui.resources.change_pair
import app.hearth.ui.resources.change_small
import app.hearth.ui.resources.change_sounds_like
import app.hearth.ui.resources.change_step
import app.hearth.ui.resources.pairing_hint
import app.hearth.ui.resources.small_step_hint
import app.hearth.ui.resources.routine_change_step
import app.hearth.ui.resources.routine_change_title
import app.hearth.ui.resources.routine_new_hint
import app.hearth.ui.resources.routine_new_title
import app.hearth.ui.resources.rule_write_hint
import app.hearth.ui.resources.save
import app.hearth.ui.resources.task_hint
import app.hearth.ui.resources.task_title
import app.hearth.ui.resources.write_change_routine
import app.hearth.ui.resources.write_new_routine
import app.hearth.ui.resources.write_rule
import app.hearth.ui.resources.write_task
import app.hearth.ui.theme.SectionStyle
import app.hearth.ui.theme.style
import org.jetbrains.compose.resources.stringResource

// A friction (or idea) is talked through in two moves. First the meeting chooses what it needs, in one tap:
// a house rule, a new routine, a change to a routine, a task with focus, or letting it go. Then someone writes
// it in their own words, right away or later, because "de vaatwasser is nooit leeg" rarely becomes a rule in
// those words. Used the same way on the Werkbord and in the weekoverleg.

/** What the family wrote for a friction: the outcome in their own words, and where it goes. */
data class FrictionWrite(
    val outcome: FrictionOutcome,
    val text: String? = null,
    val section: Section = Section.PROJECT,
    val routine: BacklogItem? = null,
    val anchor: Anchor? = null,
    /** A routine change: a smaller first step (make it easy). */
    val smallStep: String? = null,
    /** A routine change: something nice with it (make it attractive). */
    val pairing: String? = null,
)

/**
 * How a routine changes after a friction, along the four laws: another moment (obvious), something nice with it
 * (attractive), start smaller (easy), or one step more. The friction's words pick the likely one.
 */
private enum class RoutineChange { MOMENT, PAIR, SMALLER, STEP }

private fun HabitLaw?.change(): RoutineChange = when (this) {
    HabitLaw.OBVIOUS -> RoutineChange.MOMENT
    HabitLaw.ATTRACTIVE -> RoutineChange.PAIR
    HabitLaw.EASY -> RoutineChange.SMALLER
    else -> RoutineChange.STEP
}

/** Choose what it needs, write it, or let it go. */
class FrictionActions(
    val mark: (BoardItem, FrictionDirection?) -> Unit,
    val write: (BoardItem, FrictionWrite) -> Unit,
)

/** The topic a direction leads to: its colour and icon, so "huisregel te maken" already looks like the Huisregels. */
private fun FrictionDirection.topic(): Topic = when (this) {
    FrictionDirection.RULE -> Topic.RULES
    FrictionDirection.NEW_ROUTINE, FrictionDirection.CHANGE_ROUTINE -> Topic.RHYTHM
    FrictionDirection.TASK -> Topic.PROJECTS
}

@Composable
private fun FrictionDirection.choice() = stringResource(
    when (this) {
        FrictionDirection.RULE -> Res.string.dir_rule
        FrictionDirection.NEW_ROUTINE -> Res.string.dir_new_routine
        FrictionDirection.CHANGE_ROUTINE -> Res.string.dir_change_routine
        FrictionDirection.TASK -> Res.string.dir_task
    },
)

@Composable
private fun FrictionDirection.marked() = stringResource(
    when (this) {
        FrictionDirection.RULE -> Res.string.dir_rule_marked
        FrictionDirection.NEW_ROUTINE -> Res.string.dir_new_routine_marked
        FrictionDirection.CHANGE_ROUTINE -> Res.string.dir_change_routine_marked
        FrictionDirection.TASK -> Res.string.dir_task_marked
    },
)

@Composable
private fun FrictionDirection.writeLabel() = stringResource(
    when (this) {
        FrictionDirection.RULE -> Res.string.write_rule
        FrictionDirection.NEW_ROUTINE -> Res.string.write_new_routine
        FrictionDirection.CHANGE_ROUTINE -> Res.string.write_change_routine
        FrictionDirection.TASK -> Res.string.write_task
    },
)

/**
 * One open friction or idea: who noticed it, then either "Wat is er nodig?" with the choices, or the chosen
 * direction in its topic's colour with a button to write it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrictionRow(
    item: BoardItem,
    member: Member?,
    routines: List<BacklogItem>,
    lang: Lang,
    actions: FrictionActions,
    caption: String? = null,
    onDelete: (() -> Unit)? = null,
) {
    var writing by remember { mutableStateOf(false) }
    val friction = Topic.FRICTION.style()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
            member?.let { MemberDot(it); Spacer(Modifier.width(10.dp)) }
            Icon(if (item.kind == BoardKind.IDEA) HearthIcons.Idea else Topic.FRICTION.icon(), null, tint = friction.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(item.text, style = MaterialTheme.typography.bodyLarge)
                caption?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            onDelete?.let { DeleteButton(it) }
        }
        Spacer(Modifier.size(6.dp))
        val direction = item.direction
        if (direction == null) {
            Text(
                stringResource(Res.string.friction_need),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FrictionDirection.entries.forEach { d ->
                    ChoiceChip(d.choice(), false, { actions.mark(item, d) }, icon = d.topic().icon())
                }
                ChoiceChip(stringResource(Res.string.friction_let_go), false, { actions.write(item, FrictionWrite(FrictionOutcome.LET_GO)) })
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                DirectionTag(direction.marked(), direction.topic().style(), direction.topic())
                Button(onClick = { writing = true }) { Text(direction.writeLabel()) }
                TextButton(onClick = { actions.mark(item, null) }) { Text(stringResource(Res.string.friction_other)) }
            }
        }
    }
    if (writing) {
        item.direction?.let { d -> WriteDialog(item, d, routines, lang, onWrite = { actions.write(item, it) }) { writing = false } }
    }
}

/** "Huisregel te maken" in the colour of the topic it leads to. */
@Composable
private fun DirectionTag(text: String, style: SectionStyle, topic: Topic) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(style.tint, RoundedCornerShape(50)).padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(topic.icon(), null, tint = style.ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = style.ink)
    }
}

/**
 * Writing what a friction needs, in the family's own words: the rule, the new routine (with its moment), the
 * change to a routine (a step and/or another moment) or the task (paperwork or a big task). The friction is shown
 * above the field as a reminder, never prefilled. Typed text is never lost by a tap outside.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WriteDialog(item: BoardItem, direction: FrictionDirection, routines: List<BacklogItem>, lang: Lang, onWrite: (FrictionWrite) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var anchor by remember { mutableStateOf<Anchor?>(null) }
    var routine by remember { mutableStateOf<BacklogItem?>(null) }
    var section by remember { mutableStateOf(Section.PROJECT) }
    var asking by remember { mutableStateOf(false) }
    val law = remember(item.text) { lawOf(item.text) }
    var change by remember { mutableStateOf(law.change()) }
    val focus = remember { FocusRequester() }
    // Straight to typing, except when a routine has to be picked first.
    LaunchedEffect(Unit) { if (direction != FrictionDirection.CHANGE_ROUTINE) focus.requestFocus() }
    val dirty = text.isNotBlank() || anchor != null || routine != null
    val (title, hint) = when (direction) {
        FrictionDirection.RULE -> Res.string.friction_rule_title to Res.string.rule_write_hint
        FrictionDirection.NEW_ROUTINE -> Res.string.routine_new_title to Res.string.routine_new_hint
        FrictionDirection.CHANGE_ROUTINE -> Res.string.routine_change_title to Res.string.routine_change_step
        FrictionDirection.TASK -> Res.string.task_title to Res.string.task_hint
    }
    val ready = when (direction) {
        FrictionDirection.CHANGE_ROUTINE -> routine != null && (if (change == RoutineChange.MOMENT) anchor != null else text.isNotBlank())
        else -> text.isNotBlank()
    }
    val write = {
        val words = text.trim().ifEmpty { null }
        onWrite(
            when (direction) {
                FrictionDirection.RULE -> FrictionWrite(FrictionOutcome.RULE, words)
                FrictionDirection.NEW_ROUTINE -> FrictionWrite(FrictionOutcome.ROUTINE, words, anchor = anchor)
                FrictionDirection.CHANGE_ROUTINE -> FrictionWrite(
                    FrictionOutcome.ROUTINE_CHANGE,
                    text = words.takeIf { change == RoutineChange.STEP },
                    routine = routine,
                    anchor = anchor.takeIf { change == RoutineChange.MOMENT },
                    smallStep = words.takeIf { change == RoutineChange.SMALLER },
                    pairing = words.takeIf { change == RoutineChange.PAIR },
                )
                FrictionDirection.TASK -> FrictionWrite(FrictionOutcome.CHANGE, words, section = section)
            },
        )
        onDismiss()
    }

    @Composable
    fun Moments(label: String) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Anchor.entries.forEach { a -> ChoiceChip(a.title(lang), anchor == a, { anchor = if (anchor == a) null else a }) }
            if (direction == FrictionDirection.NEW_ROUTINE) ChoiceChip(stringResource(Res.string.anchor_none), anchor == null, { anchor = null })
        }
    }

    AlertDialog(
        onDismissRequest = { if (dirty) asking = true else onDismiss() },
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.friction_was, item.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (direction == FrictionDirection.CHANGE_ROUTINE) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        routines.forEach { r -> ChoiceChip(r.text, routine?.id == r.id, { routine = r }, icon = Topic.RHYTHM.icon()) }
                    }
                    // What changes, one per law; what the friction says picks the likely one.
                    Text(stringResource(Res.string.change_how), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    law?.let { Text(stringResource(Res.string.change_sounds_like, it.title(lang).lowercase()), style = MaterialTheme.typography.labelMedium, color = Topic.RHYTHM.style().ink) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(stringResource(Res.string.change_moment), change == RoutineChange.MOMENT, { change = RoutineChange.MOMENT }, icon = HabitLaw.OBVIOUS.icon())
                        ChoiceChip(stringResource(Res.string.change_pair), change == RoutineChange.PAIR, { change = RoutineChange.PAIR }, icon = HabitLaw.ATTRACTIVE.icon())
                        ChoiceChip(stringResource(Res.string.change_small), change == RoutineChange.SMALLER, { change = RoutineChange.SMALLER }, icon = HabitLaw.EASY.icon())
                        ChoiceChip(stringResource(Res.string.change_step), change == RoutineChange.STEP, { change = RoutineChange.STEP }, icon = HearthIcons.Add)
                    }
                }
                val changeHint = when (change) {
                    RoutineChange.PAIR -> Res.string.pairing_hint
                    RoutineChange.SMALLER -> Res.string.small_step_hint
                    else -> hint
                }
                if (direction != FrictionDirection.CHANGE_ROUTINE || change != RoutineChange.MOMENT) {
                    OutlinedTextField(
                        text, { text = it },
                        placeholder = { Text(stringResource(if (direction == FrictionDirection.CHANGE_ROUTINE) changeHint else hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                when (direction) {
                    FrictionDirection.NEW_ROUTINE -> Moments(stringResource(Res.string.moment_title))
                    FrictionDirection.CHANGE_ROUTINE -> if (change == RoutineChange.MOMENT) Moments(stringResource(Res.string.moment_title))
                    FrictionDirection.TASK -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(stringResource(Res.string.kind_admin), section == Section.ADMIN, { section = Section.ADMIN }, icon = Topic.ADMIN.icon())
                        ChoiceChip(stringResource(Res.string.kind_project), section == Section.PROJECT, { section = Section.PROJECT }, icon = Topic.PROJECTS.icon())
                    }
                    FrictionDirection.RULE -> {}
                }
            }
        },
        confirmButton = { FilledTonalButton(onClick = write, enabled = ready) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
    if (asking) DiscardDialog(onKeep = { asking = false }, onDiscard = { asking = false; onDismiss() })
}
