package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hearth.core.model.Anchor
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.BoardStatus
import app.hearth.core.model.HabitReview
import app.hearth.core.model.HabitTip
import app.hearth.core.model.Lang
import app.hearth.core.model.Member
import app.hearth.core.model.TipAction
import app.hearth.core.model.Topic
import app.hearth.core.model.dayLabel
import app.hearth.core.model.dayOf
import app.hearth.core.model.ruleTips
import app.hearth.core.model.title
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.delete
import app.hearth.ui.resources.delete_confirm
import app.hearth.ui.resources.delete_confirm_body
import app.hearth.ui.resources.edit
import app.hearth.ui.resources.goal_celebrate_body
import app.hearth.ui.resources.goal_celebrate_ok
import app.hearth.ui.resources.goal_celebrate_title
import app.hearth.ui.resources.goal_celebrate_win
import app.hearth.ui.resources.goal_place
import app.hearth.ui.resources.goal_reached
import app.hearth.ui.resources.goal_reached_on
import app.hearth.ui.resources.goal_reopen
import app.hearth.ui.resources.item_rename_hint
import app.hearth.ui.resources.next_step
import app.hearth.ui.resources.pairing_line
import app.hearth.ui.resources.rule_helped
import app.hearth.ui.resources.rule_moment_title
import app.hearth.ui.resources.rule_small_hint
import app.hearth.ui.resources.rule_small_title
import app.hearth.ui.resources.save
import app.hearth.ui.resources.scope_couple
import app.hearth.ui.resources.scope_family
import app.hearth.ui.resources.small_step_line
import app.hearth.ui.theme.style
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** How a house rule is helped along: a moment, something nice, a small version. Null leaves a part as it is. */
data class RuleHelp(val anchor: Anchor? = null, val noMoment: Boolean = false, val pairing: String? = null, val smallStep: String? = null)

/** What the sheet of a Werkbord item can do; each is one tool call in the ViewModel. */
class BoardItemActions(
    val rename: (String) -> Unit,
    val rate: (Int) -> Unit,
    val help: (RuleHelp) -> Unit,
    /** Says it in Wat ging goed. */
    val celebrate: () -> Unit,
    val achieve: () -> Unit,
    val reopen: () -> Unit,
    /** Opens "Een plek in de week" for a goal. */
    val plan: () -> Unit,
    val delete: () -> Unit,
)

/**
 * Tap a house rule, friction, idea or goal on the Werkbord: its sheet. A house rule reads in three parts, like a
 * routine: Nu (its words and what helps it now), Hoe loopt het (its score, 0 to 5, and how it moved) and Verbeteren
 * (the four laws, one suggestion each). A goal says where it lives in the week and is reached with one big button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardItemSheet(
    item: BoardItem,
    review: HabitReview?,
    linked: BacklogItem?,
    member: Member?,
    today: LocalDate,
    lang: Lang,
    actions: BoardItemActions,
    onDismiss: () -> Unit,
) {
    val topic = item.topic()
    val style = topic.style()
    var editing by remember(item.id) { mutableStateOf(false) }
    var words by remember(item.id) { mutableStateOf(item.text) }
    var tab by remember(item.id) { mutableStateOf(SheetTab.NOW) }
    var asking by remember { mutableStateOf<TipAction?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isRule = item.kind == BoardKind.RULE

    fun onTip(tip: HabitTip) = when (tip.action) {
        TipAction.CELEBRATE -> actions.celebrate()
        else -> asking = tip.action
    }

    FormSheet(dirty = editing && words.trim() != item.text, onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 760.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp).padding(bottom = 28.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Its topic, then its words: tap the pencil to say it differently.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(style.tint, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (item.kind == BoardKind.IDEA) HearthIcons.Idea else topic.icon(), null, tint = style.ink, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(topic.title(lang), style = MaterialTheme.typography.labelLarge, color = style.ink, modifier = Modifier.weight(1f))
                member?.let { MemberDot(it) }
            }
            if (editing) {
                OutlinedTextField(words, { words = it }, placeholder = { Text(stringResource(Res.string.item_rename_hint)) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { actions.rename(words.trim()); editing = false }, enabled = words.isNotBlank() && words.trim() != item.text) { Text(stringResource(Res.string.save)) }
                    TextButton(onClick = { words = item.text; editing = false }) { Text(stringResource(Res.string.cancel)) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { words = item.text; editing = true }) { Icon(HearthIcons.Edit, stringResource(Res.string.edit), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            if (isRule) {
                SheetTabs(SheetTab.entries, tab) { tab = it }
                when (tab) {
                    SheetTab.NOW -> {
                        review?.score?.let { ScoreBadge(it, word = true, onClick = { tab = SheetTab.HOW }) }
                        val helpers = listOfNotNull(
                            item.anchor?.title(lang),
                            item.pairing?.let { stringResource(Res.string.pairing_line, it) },
                            item.smallStep?.let { stringResource(Res.string.small_step_line, it) },
                        )
                        if (helpers.isNotEmpty()) {
                            Text(stringResource(Res.string.rule_helped), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                helpers.forEach { ChoiceChip(it, true, { tab = SheetTab.BETTER }) }
                            }
                        }
                    }
                    SheetTab.HOW -> review?.let { HabitCoach(it, lang, onRate = actions.rate, onTip = ::onTip) }
                    SheetTab.BETTER -> LawCards(review?.laws.orEmpty(), ruleTips(item, lang), review?.focus ?: review?.tips?.firstOrNull()?.law, Topic.RULES.style(), lang, ::onTip)
                }
            }

            if (item.kind == BoardKind.GOAL) {
                Text(
                    when {
                        member != null -> member.name
                        item.scope == app.hearth.core.model.GoalScope.COUPLE -> stringResource(Res.string.scope_couple)
                        else -> stringResource(Res.string.scope_family)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.status == BoardStatus.ACHIEVED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(HearthIcons.Star, null, tint = style.ink, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(Res.string.goal_reached_on, item.closedAt?.let { dayLabel(dayOf(it), today, lang) } ?: ""),
                            style = MaterialTheme.typography.titleMedium,
                            color = style.ink,
                        )
                    }
                    TextButton(onClick = { actions.reopen(); onDismiss() }) { Text(stringResource(Res.string.goal_reopen)) }
                } else {
                    Text(stringResource(Res.string.goal_place), style = MaterialTheme.typography.titleMedium)
                    val place = linked?.takeIf { !it.done }?.text ?: item.nextStep?.let { stringResource(Res.string.next_step, it) }
                    if (place != null) Text(place, style = MaterialTheme.typography.bodyLarge, color = style.ink)
                    TextButton(onClick = actions.plan) { Text(stringResource(Res.string.goal_place)) }
                    Button(onClick = { actions.achieve(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(HearthIcons.Star, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.goal_reached))
                    }
                }
            }

            TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(HearthIcons.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(Res.string.delete))
            }
        }
    }

    when (asking) {
        TipAction.MOMENT, TipAction.DAYS -> AnchorDialog(stringResource(Res.string.rule_moment_title), item.anchor, lang, onPick = { a ->
            actions.help(if (a == null) RuleHelp(noMoment = true) else RuleHelp(anchor = a))
        }) { asking = null }
        TipAction.PAIR -> PairingDialog(item.pairing, { actions.help(RuleHelp(pairing = it)) }) { asking = null }
        null -> {}
        else -> TextDialog(stringResource(Res.string.rule_small_title), stringResource(Res.string.rule_small_hint), onDone = { t, _ -> actions.help(RuleHelp(smallStep = t)) }) { asking = null }
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(Res.string.delete_confirm, item.text), stringResource(Res.string.delete_confirm_body), onYes = { actions.delete(); onDismiss() }) { confirmDelete = false }
    }
}

/** The Werkbord topic an item lives under. */
fun BoardItem.topic(): Topic = when (kind) {
    BoardKind.RULE -> Topic.RULES
    BoardKind.GOAL -> Topic.GOALS
    else -> Topic.FRICTION
}

/**
 * A goal reached: a moment, not a checkbox. A big star, the goal, where it now lives (the Bereikt shelf), and one tap
 * to say it in Wat ging goed.
 */
@Composable
fun GoalCelebration(goal: BoardItem, onWin: () -> Unit, onDismiss: () -> Unit) {
    val style = Topic.GOALS.style()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(Modifier.size(72.dp).background(style.tint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(HearthIcons.Star, null, tint = style.ink, modifier = Modifier.size(40.dp))
            }
        },
        title = { Text(stringResource(Res.string.goal_celebrate_title), textAlign = TextAlign.Center) },
        text = { Text(stringResource(Res.string.goal_celebrate_body, goal.text), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { FilledTonalButton(onClick = { onWin(); onDismiss() }) { Text(stringResource(Res.string.goal_celebrate_win)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.goal_celebrate_ok)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
