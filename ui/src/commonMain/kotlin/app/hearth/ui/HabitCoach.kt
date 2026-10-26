package app.hearth.ui

import androidx.compose.foundation.background
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
import app.hearth.core.model.HabitTip
import app.hearth.core.model.Lang
import app.hearth.core.model.LawState
import app.hearth.core.model.TipAction
import app.hearth.core.model.Topic
import app.hearth.core.model.line
import app.hearth.core.model.tag
import app.hearth.core.model.title
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.coach_hint
import app.hearth.ui.resources.coach_not_yet
import app.hearth.ui.resources.coach_said
import app.hearth.ui.resources.coach_title
import app.hearth.ui.resources.coach_weather
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

// The habit coach on screen: the four laws of Atomic Habits for one habit, in its sheet and in the Terugblik.

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
 * In a habit's sheet: where it stands, its weather per meeting, what was said about it, the tips, and the four laws
 * two by two (tap one to fill it in; the last one only shows how often it ran easily).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitCoach(review: HabitReview, lang: Lang, onTip: (HabitTip) -> Unit, onLaw: (HabitLaw) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.coach_title), style = MaterialTheme.typography.titleMedium)
        Text(review.stage.line(lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (review.ratings.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.coach_weather), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                review.ratings.takeLast(8).forEach { WeatherMark(it, 18.dp) }
            }
        }
        review.frictions.lastOrNull()?.let {
            Text(stringResource(Res.string.coach_said, "“$it”"), style = MaterialTheme.typography.bodySmall, color = Topic.FRICTION.style().ink)
        }
        review.tips.forEach { tip -> CoachTip(tip, lang, { onTip(tip) }) }
        FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            review.laws.forEach { state ->
                LawTile(state, lang, Modifier.weight(1f), onClick = { onLaw(state.law) }.takeIf { state.law != HabitLaw.SATISFYING })
            }
        }
        Text(stringResource(Res.string.coach_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One law: its icon and name, then what's in place ("Rond het eten") or "Nog niets". */
@Composable
private fun LawTile(state: LawState, lang: Lang, modifier: Modifier, onClick: (() -> Unit)?) {
    val ink = Topic.RHYTHM.style().ink
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(state.law.icon(), null, tint = ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(state.law.title(lang), style = MaterialTheme.typography.labelMedium, color = ink)
        }
        Text(
            state.now ?: stringResource(Res.string.coach_not_yet),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.now == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
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
