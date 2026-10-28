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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.core.model.BoardItem
import app.hearth.core.model.dayLabel
import app.hearth.core.model.title
import app.hearth.ui.resources.board_tap_hint
import app.hearth.ui.resources.pairing_line
import app.hearth.ui.resources.small_step_line
import kotlinx.datetime.LocalDate
import app.hearth.core.model.isRoutine
import app.hearth.core.model.Ease
import app.hearth.core.model.BoardKind
import app.hearth.core.model.BoardStatus
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.FrictionDirection
import app.hearth.core.model.GoalScope
import app.hearth.core.model.Member
import app.hearth.core.model.Section
import app.hearth.core.model.nowMillis
import app.hearth.core.tools.board.FrictionOutcome
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.discard_body
import app.hearth.ui.resources.discard_keep
import app.hearth.ui.resources.discard_title
import app.hearth.ui.resources.discard_yes
import app.hearth.ui.resources.became_change
import app.hearth.ui.resources.became_routine
import app.hearth.ui.resources.became_rule
import app.hearth.ui.resources.board_subtitle
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.days_ago
import app.hearth.ui.resources.delete_confirm
import app.hearth.ui.resources.delete_confirm_body
import app.hearth.ui.resources.friction_let_go
import app.hearth.ui.resources.friction_rule_title
import app.hearth.ui.resources.friction_text_hint
import app.hearth.ui.resources.frictions_add_hint
import app.hearth.ui.resources.frictions_empty
import app.hearth.ui.resources.frictions_hint
import app.hearth.ui.resources.goal_achieved_title
import app.hearth.ui.resources.goal_step_add
import app.hearth.ui.resources.goal_step_title
import app.hearth.ui.resources.goal_step_to_week
import app.hearth.ui.resources.goals_add_hint
import app.hearth.ui.resources.goals_empty
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
import app.hearth.ui.resources.save
import app.hearth.ui.resources.scope_couple
import app.hearth.ui.resources.scope_family
import app.hearth.ui.resources.scope_personal
import app.hearth.ui.resources.today_word
import app.hearth.ui.resources.yes
import app.hearth.ui.theme.style
import app.hearth.core.model.Topic
import org.jetbrains.compose.resources.stringResource

/** Everything the Werkbord can ask for; each is one tool call in the ViewModel. */
class BoardCallbacks(
    val addMember: (String) -> Unit,
    val removeMember: (Member) -> Unit,
    val addRule: (String) -> Unit,
    val logFriction: (String) -> Unit,
    val resolveFriction: (BoardItem, FrictionWrite) -> Unit,
    val markFriction: (BoardItem, FrictionDirection?) -> Unit,
    val addGoal: (String, GoalScope, Member?) -> Unit,
    val setGoalStep: (BoardItem, GoalPlan) -> Unit,
    val rateRule: (BoardItem, Int) -> Unit,
    val achieveGoal: (BoardItem) -> Unit,
    val delete: (BoardItem) -> Unit,
    val rename: (BoardItem, String) -> Unit = { _, _ -> },
    val ruleHelp: (BoardItem, RuleHelp) -> Unit = { _, _ -> },
    val reopenGoal: (BoardItem) -> Unit = {},
    /** Says something in Wat ging goed, under its topic. */
    val addWin: (String, Topic) -> Unit = { _, _ -> },
)

/** Who lives here, then three columns: Huisregels · Frictie & ideeën · Doelen. [routines] are the ones a friction can change. */
@Composable
fun BoardScreen(
    items: List<BoardItem>,
    members: List<Member>,
    backlog: List<BacklogItem>,
    ruleScores: Map<String, Int>,
    today: LocalDate,
    wide: Boolean,
    cb: BoardCallbacks,
    /** The coach per house rule, by rule id. */
    ruleReviews: Map<String, app.hearth.core.model.HabitReview> = emptyMap(),
) {
    val routines = backlog.filter { it.isRoutine() && !it.done }
    val byId = backlog.associateBy { it.id }
    var deleting by remember { mutableStateOf<BoardItem?>(null) }
    var removing by remember { mutableStateOf<Member?>(null) }
    var asking by remember { mutableStateOf<Ask?>(null) }
    // The item whose sheet is open (by id, so it follows changes), and a goal just reached.
    var opened by remember { mutableStateOf<String?>(null) }
    var reached by remember { mutableStateOf<BoardItem?>(null) }
    val achieve = { goal: BoardItem -> cb.achieveGoal(goal); reached = goal }

    val rules = items.filter { it.kind == BoardKind.RULE && it.isOpen }
    val frictions = items.filter { it.isWorkingPoint }
    val goals = items.filter { it.kind == BoardKind.GOAL }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(stringResource(Res.string.nav_board), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(Res.string.board_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        MembersRow(members, onAdd = { asking = Ask.Member }, onRemove = { removing = it })
        Spacer(Modifier.size(18.dp))

        Text(stringResource(Res.string.board_tap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
        val rulesCard = @Composable { m: Modifier -> RulesCard(rules, ruleScores, m, cb, onOpen = { opened = it.id }) }
        val frictionsCard = @Composable { m: Modifier -> FrictionsCard(frictions, members, routines, m, cb, onDelete = { deleting = it }, onOpen = { opened = it.id }) }
        val goalsCard = @Composable { m: Modifier -> GoalsCard(goals, members, byId, today, m, cb, onAsk = { asking = it }, onDelete = { deleting = it }, onOpen = { opened = it.id }, onAchieve = achieve) }
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
            is Ask.Step -> GoalPlanDialog(ask.goal, today, LocalLang.current, onSave = { cb.setGoalStep(ask.goal, it) }) { asking = null }
        }
    }
    opened?.let { id -> items.firstOrNull { it.id == id } }?.let { item ->
        BoardItemSheet(
            item = item,
            review = ruleReviews[item.id],
            linked = item.linkedId?.let(byId::get),
            member = item.memberId?.let { m -> members.firstOrNull { it.id == m } },
            today = today,
            lang = LocalLang.current,
            actions = BoardItemActions(
                rename = { cb.rename(item, it) },
                rate = { cb.rateRule(item, it) },
                help = { cb.ruleHelp(item, it) },
                celebrate = { cb.addWin(item.text, item.topic()) },
                achieve = { achieve(item) },
                reopen = { cb.reopenGoal(item) },
                plan = { asking = Ask.Step(item) },
                delete = { cb.delete(item) },
            ),
            onDismiss = { opened = null },
        )
    }
    reached?.let { goal -> GoalCelebration(goal, onWin = { cb.addWin(goal.text, Topic.GOALS) }) { reached = null } }
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
private fun RulesCard(rules: List<BoardItem>, scores: Map<String, Int>, modifier: Modifier, cb: BoardCallbacks, onOpen: (BoardItem) -> Unit) {
    val s = Topic.RULES.style()
    TopicCard(Topic.RULES, LocalLang.current, modifier) {
        AddRow(stringResource(Res.string.rules_add_hint), cb.addRule, muted = true, compact = true)
        Spacer(Modifier.size(8.dp))
        if (rules.isEmpty()) EmptyHint(stringResource(Res.string.rules_empty))
        rules.forEachIndexed { i, rule ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onOpen(rule) }.heightIn(min = 56.dp).padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
            ) {
                Box(Modifier.size(30.dp).background(s.tint, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = s.ink)
                }
                Spacer(Modifier.width(14.dp))
                // How it goes, like the routines: rated together in the Terugblik, or right here.
                Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(rule.text, style = MaterialTheme.typography.bodyLarge)
                    // Tap the badge to change the score right here; the row opens everything else.
                    ScoreChooser(scores[rule.id]) { cb.rateRule(rule, it) }
                    listOfNotNull(rule.anchor?.title(LocalLang.current), rule.smallStep?.let { stringResource(Res.string.small_step_line, it) }, rule.pairing?.let { stringResource(Res.string.pairing_line, it) })
                        .takeIf { it.isNotEmpty() }?.let { Text(it.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = s.ink) }
                }
                Icon(HearthIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ---------- frictions ----------

@Composable
private fun FrictionsCard(frictions: List<BoardItem>, members: List<Member>, routines: List<BacklogItem>, modifier: Modifier, cb: BoardCallbacks, onDelete: (BoardItem) -> Unit, onOpen: (BoardItem) -> Unit) {
    val open = frictions.filter { it.isOpen }.sortedBy { it.direction != null }
    val resolved = frictions.filterNot { it.isOpen }.sortedByDescending { it.closedAt ?: 0 }
    val byId = members.associateBy { it.id }
    val lang = LocalLang.current
    TopicCard(Topic.FRICTION, lang, modifier) {
        AddRow(stringResource(Res.string.frictions_add_hint), cb.logFriction, muted = true, compact = true)
        Spacer(Modifier.size(8.dp))
        if (open.isEmpty()) EmptyHint(stringResource(Res.string.frictions_empty))
        else Text(stringResource(Res.string.frictions_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        open.forEach { f ->
            FrictionRow(f, f.memberId?.let(byId::get), routines, lang, FrictionActions(cb.markFriction, cb.resolveFriction), caption = ago(f.createdAt), onDelete = { onDelete(f) }, onOpen = { onOpen(f) })
        }
    if (resolved.isNotEmpty()) {
            GroupTitle(stringResource(Res.string.resolved_title))
            resolved.take(5).forEach { f ->
                val outcome = when (f.status) {
                    BoardStatus.BECAME_RULE -> stringResource(Res.string.became_rule)
                    BoardStatus.BECAME_CHANGE -> stringResource(Res.string.became_change)
                    BoardStatus.BECAME_ROUTINE -> stringResource(Res.string.became_routine)
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
private fun GoalsCard(
    goals: List<BoardItem>,
    members: List<Member>,
    backlog: Map<String, BacklogItem>,
    today: LocalDate,
    modifier: Modifier,
    cb: BoardCallbacks,
    onAsk: (Ask) -> Unit,
    onDelete: (BoardItem) -> Unit,
    onOpen: (BoardItem) -> Unit,
    onAchieve: (BoardItem) -> Unit,
) {
    var owner by remember { mutableStateOf(Owner(GoalScope.FAMILY, null)) }
    val open = goals.filter { it.isOpen }
    val achieved = goals.filter { it.status == BoardStatus.ACHIEVED }.sortedByDescending { it.closedAt ?: 0 }
    val byId = members.associateBy { it.id }

    TopicCard(Topic.GOALS, LocalLang.current, modifier) {
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
            list.forEach { goal ->
                GoalRow(goal, goal.linkedId?.let(backlog::get), today, LocalLang.current, onAchieve = { onAchieve(goal) }, onPlan = { onAsk(Ask.Step(goal)) }, onDelete = { onDelete(goal) }, onOpen = { onOpen(goal) })
            }
        }
        // Bereikt: every goal reached, as a shelf of stars, so done work stays in sight. Tap one to see it again.
        if (achieved.isNotEmpty()) {
            GroupTitle(stringResource(Res.string.goal_achieved_title))
            val g = Topic.GOALS.style()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 6.dp, top = 2.dp)) {
                achieved.forEach { goal ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(g.tint).clickable { onOpen(goal) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(HearthIcons.Star, null, tint = g.ink, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(goal.text, style = MaterialTheme.typography.labelLarge)
                            goal.closedAt?.let {
                                Text(
                                    dayLabel(app.hearth.core.model.dayOf(it), today, LocalLang.current).replaceFirstChar { c -> c.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = g.ink,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- dialogs ----------

/** A one-field dialog; [toggle] adds a yes/no option under the field. A tap outside never loses typed text. */
@Composable
fun TextDialog(title: String, hint: String, toggle: String? = null, onDone: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(true) }
    var asking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = { if (text.isNotBlank()) asking = true else onDismiss() },
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
    if (asking) DiscardDialog(onKeep = { asking = false }, onDiscard = { asking = false; onDismiss() })
}

/**
 * Asked when a tap outside, back or a swipe would close a form with typed text in it: keep typing (the
 * default) or throw it away. Nothing anyone typed disappears without asking.
 */
@Composable
fun DiscardDialog(onKeep: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(Res.string.discard_title)) },
        text = { Text(stringResource(Res.string.discard_body)) },
        confirmButton = { FilledTonalButton(onClick = onKeep) { Text(stringResource(Res.string.discard_keep)) } },
        dismissButton = { TextButton(onClick = onDiscard) { Text(stringResource(Res.string.discard_yes)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/**
 * A bottom sheet that holds a form. While [dirty] (something typed or chosen), a tap outside, back or a
 * swipe down asks first instead of closing, so a half-written recipe is never lost by accident.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(dirty: Boolean, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    var asking by remember { mutableStateOf(false) }
    val isDirty by androidx.compose.runtime.rememberUpdatedState(dirty)
    val state = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == androidx.compose.material3.SheetValue.Hidden && isDirty) {
                asking = true
                false
            } else true
        },
    )
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        androidx.compose.foundation.layout.Column(Modifier.nestedScroll(SheetScrollStop), content = content)
    }
    if (asking) DiscardDialog(onKeep = { asking = false }, onDiscard = { asking = false; onDismiss() })
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
