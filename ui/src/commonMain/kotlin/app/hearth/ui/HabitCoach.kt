package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.hearth.core.model.HabitLaw
import app.hearth.core.model.HabitReview
import app.hearth.core.model.HabitStage
import app.hearth.core.model.HabitTip
import app.hearth.core.model.Lang
import app.hearth.core.model.TipAction
import app.hearth.core.model.Topic
import app.hearth.core.model.line
import app.hearth.core.model.tag
import app.hearth.core.model.title
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.coach_said
import app.hearth.ui.resources.coach_better
import app.hearth.ui.resources.coach_same
import app.hearth.ui.resources.coach_start
import app.hearth.ui.resources.coach_try
import app.hearth.ui.resources.coach_worse
import app.hearth.ui.resources.coach_title
import app.hearth.ui.resources.habit_less_often
import app.hearth.ui.resources.pairing_hint
import app.hearth.ui.resources.pairing_music
import app.hearth.ui.resources.pairing_title
import app.hearth.ui.resources.pairing_together
import app.hearth.ui.resources.pairing_treat
import app.hearth.ui.resources.save
import app.hearth.ui.resources.tip_add_step
import app.hearth.ui.resources.tip_celebrate
import app.hearth.ui.resources.tip_days
import app.hearth.ui.resources.tip_fewer
import app.hearth.ui.resources.tip_moment
import app.hearth.ui.resources.tip_more
import app.hearth.ui.resources.tip_pair
import app.hearth.ui.resources.tip_small
import app.hearth.ui.resources.tip_whole
import app.hearth.ui.theme.style
import org.jetbrains.compose.resources.stringResource

// The habit coach on screen: how a habit goes and, when it needs one, the one thing to try (chosen along the four
// laws of Atomic Habits), in its sheet and in the Terugblik.

fun HabitLaw.icon(): ImageVector = when (this) {
    HabitLaw.OBVIOUS -> HearthIcons.Eye
    HabitLaw.ATTRACTIVE -> HearthIcons.Smile
    HabitLaw.EASY -> HearthIcons.Stairs
    // Satisfying is said in Wat ging goed, so it has that heart.
    HabitLaw.SATISFYING -> HearthIcons.Heart
}

/** Tips that change the habit in one tap, without words: the view model does them. */
val TipAction.oneTap: Boolean get() = this == TipAction.LESS_OFTEN || this == TipAction.MORE_OFTEN || this == TipAction.WHOLE || this == TipAction.CELEBRATE

@Composable
fun TipAction.label(): String = stringResource(
    when (this) {
        TipAction.MOMENT -> Res.string.tip_moment
        TipAction.DAYS -> Res.string.tip_days
        TipAction.PAIR -> Res.string.tip_pair
        TipAction.SMALL_STEP -> Res.string.tip_small
        TipAction.LESS_OFTEN -> Res.string.habit_less_often
        TipAction.FEWER_STEPS -> Res.string.tip_fewer
        TipAction.CELEBRATE -> Res.string.tip_celebrate
        TipAction.WHOLE -> Res.string.tip_whole
        TipAction.MORE_OFTEN -> Res.string.tip_more
        TipAction.ADD_STEP -> Res.string.tip_add_step
    },
)

/** One tip in the rhythm's colour: its law, what to try, and the button that does it. */
@Composable
fun CoachTip(tip: HabitTip, lang: Lang, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = Topic.RHYTHM.style()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().background(style.tint, RoundedCornerShape(16.dp)).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(tip.law?.icon() ?: HearthIcons.Next, null, tint = style.ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tip.tag(lang), style = MaterialTheme.typography.labelMedium, color = style.ink)
            Text(tip.text, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onClick) { Text(tip.action.label()) }
    }
}

/**
 * In a habit's sheet, first: how it goes. Its score (tap a number to say how it went), how it went per meeting as
 * small bars and whether it goes up or down, what was said about it, and only when it needs one, the one thing to try
 * ("Wat kan beter?"). Setting it up (moment, how often, days) lives further down the sheet.
 */
@Composable
fun HabitCoach(review: HabitReview, lang: Lang, onRate: (Int) -> Unit, onTip: (HabitTip) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.coach_title), style = MaterialTheme.typography.titleMedium)
        val score = review.score
        if (score != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScoreBadge(score, word = true)
                review.trend?.let { t ->
                    Text(
                        stringResource(if (t > 0) Res.string.coach_better else if (t < 0) Res.string.coach_worse else Res.string.coach_same),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (review.ratings.size > 1) ScoreTrail(review.ratings.takeLast(8))
            }
        }
        ScoreScale(score, onRate)
        Text(review.stage.line(lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        review.frictions.lastOrNull()?.let {
            Text(stringResource(Res.string.coach_said, "“$it”"), style = MaterialTheme.typography.bodySmall, color = Topic.FRICTION.style().ink)
        }
        // One thing at a time: what needs changing now, or for a new habit how to start.
        val tip = review.tips.firstOrNull()?.takeIf { review.needsChange || review.stage == HabitStage.NEW }
        if (tip != null) {
            Text(
                stringResource(if (review.stage == HabitStage.NEW) Res.string.coach_start else Res.string.coach_try),
                style = MaterialTheme.typography.labelLarge,
                color = Topic.RHYTHM.style().ink,
                modifier = Modifier.padding(top = 4.dp),
            )
            CoachTip(tip, lang, { onTip(tip) })
        }
    }
}

/** "Wat maakt het leuker?": a few to tap, or the family's own words. Typed text is never lost by a tap outside. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PairingDialog(current: String?, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current.orEmpty()) }
    var asking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val presets = listOf(stringResource(Res.string.pairing_music), stringResource(Res.string.pairing_together), stringResource(Res.string.pairing_treat))
    AlertDialog(
        onDismissRequest = { if (text.isNotBlank() && text != current) asking = true else onDismiss() },
        title = { Text(stringResource(Res.string.pairing_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p -> ChoiceChip(p, text.equals(p, ignoreCase = true), { text = p }) }
                }
                OutlinedTextField(text, { text = it }, placeholder = { Text(stringResource(Res.string.pairing_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
            }
        },
        confirmButton = { FilledTonalButton(onClick = { onDone(text.trim()); onDismiss() }) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
    if (asking) DiscardDialog(onKeep = { asking = false }, onDiscard = { asking = false; onDismiss() })
}
