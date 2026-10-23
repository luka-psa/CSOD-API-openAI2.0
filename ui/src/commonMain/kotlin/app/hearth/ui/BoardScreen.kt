package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.core.model.BoardItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.BoardStatus
import app.hearth.core.model.GoalScope
import app.hearth.core.model.Member
import app.hearth.core.model.Section
import app.hearth.core.model.nowMillis
import app.hearth.core.tools.board.FrictionOutcome
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.became_change
import app.hearth.ui.resources.became_rule
import app.hearth.ui.resources.board_subtitle
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.days_ago
import app.hearth.ui.resources.delete_confirm
import app.hearth.ui.resources.delete_confirm_body
import app.hearth.ui.resources.friction_change_title
import app.hearth.ui.resources.friction_let_go
import app.hearth.ui.resources.friction_rule_title
import app.hearth.ui.resources.friction_text_hint
import app.hearth.ui.resources.friction_to_change
import app.hearth.ui.resources.friction_to_rule
import app.hearth.ui.resources.frictions_add_hint
import app.hearth.ui.resources.frictions_empty
import app.hearth.ui.resources.frictions_hint
import app.hearth.ui.resources.frictions_title
import app.hearth.ui.resources.goal_achieved_title
import app.hearth.ui.resources.goal_step_add
import app.hearth.ui.resources.goal_step_title
import app.hearth.ui.resources.goal_step_to_week
import app.hearth.ui.resources.goals_add_hint
import app.hearth.ui.resources.goals_empty
import app.hearth.ui.resources.goals_title
import app.hearth.ui.resources.let_go
import app.hearth.ui.resources.member_add
import app.hearth.ui.resources.member_name_hint
import app.hearth.ui.resources.member_remove_body
import app.hearth.ui.resources.member_remove_confirm
import app.hearth.ui.resources.members_title
import app.hearth.ui.resources.nav_board
import app.hearth.ui.resources.next_step
import app.hearth.ui.resources.no
import app.hearth.ui.resources.resolved_title
import app.hearth.ui.resources.rules_add_hint
import app.hearth.ui.resources.rules_empty
import app.hearth.ui.resources.rules_title
import app.hearth.ui.resources.save
import app.hearth.ui.resources.scope_couple
import app.hearth.ui.resources.scope_family
import app.hearth.ui.resources.scope_personal
import app.hearth.ui.resources.today_word
import app.hearth.ui.resources.yes
import app.hearth.ui.theme.style
import org.jetbrains.compose.resources.stringResource

/** Everything the Werkbord can ask for; each is one tool call in the ViewModel. */
class BoardCallbacks(
    val addMember: (String) -> Unit,
    val removeMember: (Member) -> Unit,
    val addRule: (String) -> Unit,
    val logFriction: (String) -> Unit,
    val resolveFriction: (BoardItem, FrictionOutcome, String?) -> Unit,
    val addGoal: (String, GoalScope, Member?) -> Unit,
    val setGoalStep: (BoardItem, String, Boolean) -> Unit,
    val achieveGoal: (BoardItem) -> Unit,
    val delete: (BoardItem) -> Unit,
)

/** Who lives here, then three columns: Huisregels · Frictie · Doelen. */
@Composable
fun BoardScreen(items: List<BoardItem>, members: List<Member>, wide: Boolean, cb: BoardCallbacks) {
    var deleting by remember { mutableStateOf<BoardItem?>(null) }
    var removing by remember { mutableStateOf<Member?>(null) }
    var asking by remember { mutableStateOf<Ask?>(null) }

    val rules = items.filter { it.kind == BoardKind.RULE && it.isOpen }
    val frictions = items.filter { it.kind == BoardKind.FRICTION }
    val goals = items.filter { it.kind == BoardKind.GOAL }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(stringResource(Res.string.nav_board), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(Res.string.board_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        MembersRow(members, onAdd = { asking = Ask.Member }, onRemove = { removing = it })
        Spacer(Modifier.size(18.dp))

        val rulesCard = @Composable { m: Modifier -> RulesCard(rules, m, cb, onOpen = { deleting = it }) }
        val frictionsCard = @Composable { m: Modifier -> FrictionsCard(frictions, members, m, cb, onAsk = { asking = it }, onOpen = { deleting = it }) }
        val goalsCard = @Composable { m: Modifier -> GoalsCard(goals, members, m, cb, onAsk = { asking = it }, onOpen = { deleting = it }) }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                rulesCard(Modifier.weight(1f))
                frictionsCard(Modifier.weight(1f))
                goalsCard(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                rulesCard(Modifier.fillMaxWidth())
                frictionsCard(Modifier.fillMaxWidth())
                goalsCard(Modifier.fillMaxWidth())
            }
        }
    }

    asking?.let { ask ->
        when (ask) {
            Ask.Member -> TextDialog(stringResource(Res.string.member_add), stringResource(Res.string.member_name_hint), onDone = { text, _ -> cb.addMember(text) }) { asking = null }
            is Ask.Rule -> TextDialog(stringResource(Res.string.friction_rule_title), ask.friction.text, onDone = { text, _ -> cb.resolveFriction(ask.friction, FrictionOutcome.RULE, text.ifBlank { null }) }) { asking = null }
            is Ask.Change -> TextDialog(stringResource(Res.string.friction_change_title), ask.friction.text, onDone = { text, _ -> cb.resolveFriction(ask.friction, FrictionOutcome.CHANGE, text.ifBlank { null }) }) { asking = null }
            is Ask.Step -> TextDialog(
                stringResource(Res.string.goal_step_title), stringResource(Res.string.friction_text_hint),
                toggle = stringResource(Res.string.goal_step_to_week),
                onDone = { text, toWeek -> cb.setGoalStep(ask.goal, text, toWeek) },
            ) { asking = null }
        }
    }
    deleting?.let { item ->
        ConfirmDialog(stringResource(Res.string.delete_confirm, item.text), stringResource(Res.string.delete_confirm_body), onYes = { cb.delete(item) }) { deleting = null }
    }
    removing?.let { member ->
        ConfirmDialog(stringResource(Res.string.member_remove_confirm, member.name), stringResource(Res.string.member_remove_body), onYes = { cb.removeMember(member) }) { removing = null }
    }
}

/** What a dialog is asking for. */
private sealed interface Ask {
    data object Member : Ask
    data class Rule(val friction: BoardItem) : Ask
    data class Change(val friction: BoardItem) : Ask
    data class Step(val goal: BoardItem) : Ask
}

// ---------- members ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MembersRow(members: List<Member>, onAdd: () -> Unit, onRemove: (Member) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.members_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp))
        members.forEach { m ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).clickable { onRemove(m) }
                    .heightIn(min = 44.dp).padding(start = 6.dp, end = 16.dp),
            ) {
                MemberDot(m, 32)
                Spacer(Modifier.width(10.dp))
                Text(m.name, style = MaterialTheme.typography.labelLarge)
            }
        }
        ChoiceChip(stringResource(Res.string.member_add), false, onAdd, icon = HearthIcons.Add)
    }
}

/** A coloured circle with the first letter. */
@Composable
fun MemberDot(member: Member, size: Int = 24) {
    Box(Modifier.size(size.dp).background(Color(member.color.toLong() and 0xFFFFFFFFL), CircleShape), contentAlignment = Alignment.Center) {
        Text(member.name.take(1).uppercase(), style = if (size >= 30) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

// ---------- house rules ----------

@Composable
private fun RulesCard(rules: List<BoardItem>, modifier: Modifier, cb: BoardCallbacks, onOpen: (BoardItem) -> Unit) {
    val s = Section.NOTE.style()
    HearthCard(stringResource(Res.string.rules_title), HearthIcons.Rule, modifier) {
        AddRow(stringResource(Res.string.rules_add_hint), cb.addRule, muted = true, compact = true)
        Spacer(Modifier.size(8.dp))
        if (rules.isEmpty()) EmptyHint(stringResource(Res.string.rules_empty))
        rules.forEachIndexed { i, rule ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onOpen(rule) }.heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                Box(Modifier.size(30.dp).background(s.tint, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = s.ink)
                }
                Spacer(Modifier.width(14.dp))
                Text(rule.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ---------- frictions ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrictionsCard(frictions: List<BoardItem>, members: List<Member>, modifier: Modifier, cb: BoardCallbacks, onAsk: (Ask) -> Unit, onOpen: (BoardItem) -> Unit) {
    val open = frictions.filter { it.isOpen }
    val resolved = frictions.filterNot { it.isOpen }.sortedByDescending { it.closedAt ?: 0 }
    val byId = members.associateBy { it.id }
    HearthCard(stringResource(Res.string.frictions_title), HearthIcons.Bolt, modifier) {
        AddRow(stringResource(Res.string.frictions_add_hint), cb.logFriction, muted = true, compact = true)
        Spacer(Modifier.size(8.dp))
        if (open.isEmpty()) EmptyHint(stringResource(Res.string.frictions_empty))
        else Text(stringResource(Res.string.frictions_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        open.forEach { f ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onOpen(f) }.padding(4.dp)) {
                    f.memberId?.let(byId::get)?.let { MemberDot(it); Spacer(Modifier.width(10.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(f.text, style = MaterialTheme.typography.bodyLarge)
                        Text(ago(f.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.size(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(stringResource(Res.string.friction_to_rule), false, { onAsk(Ask.Rule(f)) }, icon = HearthIcons.Rule)
                    ChoiceChip(stringResource(Res.string.friction_to_change), false, { onAsk(Ask.Change(f)) }, icon = HearthIcons.Next)
                    ChoiceChip(stringResource(Res.string.friction_let_go), false, { cb.resolveFriction(f, FrictionOutcome.LET_GO, null) })
                }
            }
        }
        if (resolved.isNotEmpty()) {
            GroupTitle(stringResource(Res.string.resolved_title))
            resolved.take(5).forEach { f ->
                val outcome = when (f.status) {
                    BoardStatus.BECAME_RULE -> stringResource(Res.string.became_rule)
                    BoardStatus.BECAME_CHANGE -> stringResource(Res.string.became_change)
                    else -> stringResource(Res.string.let_go)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                    Icon(HearthIcons.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(f.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("  ·  $outcome", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun ago(createdAt: Long): String {
    val days = ((nowMillis() - createdAt) / 86_400_000L).toInt()
    return if (days < 1) stringResource(Res.string.today_word) else stringResource(Res.string.days_ago, days)
}

// ---------- goals ----------

/** Who a new goal is for: the family, the two of us, or one person. */
private data class Owner(val scope: GoalScope, val member: Member?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalsCard(goals: List<BoardItem>, members: List<Member>, modifier: Modifier, cb: BoardCallbacks, onAsk: (Ask) -> Unit, onOpen: (BoardItem) -> Unit) {
    var owner by remember { mutableStateOf(Owner(GoalScope.FAMILY, null)) }
    val open = goals.filter { it.isOpen }
    val achieved = goals.filter { it.status == BoardStatus.ACHIEVED }.sortedByDescending { it.closedAt ?: 0 }
    val byId = members.associateBy { it.id }

    HearthCard(stringResource(Res.string.goals_title), HearthIcons.Star, modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(stringResource(Res.string.scope_family), owner.scope == GoalScope.FAMILY, { owner = Owner(GoalScope.FAMILY, null) }, icon = HearthIcons.Home)
            ChoiceChip(stringResource(Res.string.scope_couple), owner.scope == GoalScope.COUPLE, { owner = Owner(GoalScope.COUPLE, null) }, icon = HearthIcons.Heart)
            members.forEach { m -> ChoiceChip(m.name, owner.member?.id == m.id, { owner = Owner(GoalScope.PERSONAL, m) }, icon = HearthIcons.Person) }
        }
        Spacer(Modifier.size(10.dp))
        AddRow(stringResource(Res.string.goals_add_hint), { cb.addGoal(it, owner.scope, owner.member) }, muted = true, compact = true)
        Spacer(Modifier.size(8.dp))
        if (open.isEmpty()) EmptyHint(stringResource(Res.string.goals_empty))

        val groups = listOf(
            stringResource(Res.string.scope_family) to open.filter { it.scope == null || it.scope == GoalScope.FAMILY },
            stringResource(Res.string.scope_couple) to open.filter { it.scope == GoalScope.COUPLE },
        ) + members.map { m -> m.name to open.filter { it.memberId == m.id } } +
            listOf(stringResource(Res.string.scope_personal) to open.filter { it.scope == GoalScope.PERSONAL && (it.memberId == null || it.memberId !in byId) })
        groups.filter { it.second.isNotEmpty() }.forEach { (title, list) ->
            GroupTitle(title)
            list.forEach { goal -> GoalRow(goal, onAchieve = { cb.achieveGoal(goal) }, onStep = { onAsk(Ask.Step(goal)) }, onOpen = { onOpen(goal) }) }
        }
        if (achieved.isNotEmpty()) {
            GroupTitle(stringResource(Res.string.goal_achieved_title))
            achieved.take(5).forEach { goal ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                    Icon(HearthIcons.Star, null, tint = Section.RHYTHM.style().ink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(goal.text, style = MaterialTheme.typography.bodyMedium, textDecoration = TextDecoration.None, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GoalRow(goal: BoardItem, onAchieve: () -> Unit, onStep: () -> Unit, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onOpen).padding(vertical = 4.dp)) {
        RoundCheck(false, onAchieve)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f).padding(top = 10.dp, bottom = 4.dp)) {
            Text(goal.text, style = MaterialTheme.typography.bodyLarge)
            val step = goal.nextStep
            if (step != null) {
                Text(
                    stringResource(Res.string.next_step, step),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onStep).padding(vertical = 4.dp),
                )
            } else {
                TextButton(onClick = onStep, modifier = Modifier.padding(start = 0.dp)) {
                    Icon(HearthIcons.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.goal_step_add), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ---------- dialogs ----------

/** A one-field dialog; [toggle] adds a yes/no option under the field. */
@Composable
fun TextDialog(title: String, hint: String, toggle: String? = null, onDone: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
                if (toggle != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).clickable { checked = !checked }) {
                        Checkbox(checked, { checked = it })
                        Text(toggle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { FilledTonalButton(onClick = { onDone(text.trim(), checked); onDismiss() }, enabled = toggle == null || text.isNotBlank()) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, onYes: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { FilledTonalButton(onClick = { onYes(); onDismiss() }) { Text(stringResource(Res.string.yes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.no)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
