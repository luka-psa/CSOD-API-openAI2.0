package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.core.model.Anchor
import app.hearth.core.model.HabitLaw
import app.hearth.core.model.HabitTip
import app.hearth.core.model.Lang
import app.hearth.core.model.LawState
import app.hearth.core.model.title
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.anchor_none
import app.hearth.ui.resources.better_hint
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.law_not_yet
import app.hearth.ui.resources.law_now
import app.hearth.ui.resources.law_suggested
import app.hearth.ui.resources.tab_better
import app.hearth.ui.resources.tab_how
import app.hearth.ui.resources.tab_now
import app.hearth.ui.theme.SectionStyle
import org.jetbrains.compose.resources.stringResource

// An item's sheet reads in three calm parts instead of one long form: what it is now (and change it), how it goes
// (rate it, see it move), and how to make it better (the four laws, one card each, one suggestion each). Used by
// routines (Ritmes & routines, Huishouden) and house rules alike.

enum class SheetTab { NOW, HOW, BETTER }

/** "Nu · Hoe loopt het · Verbeteren": only the parts this item has. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetTabs(tabs: List<SheetTab>, selected: SheetTab, onSelect: (SheetTab) -> Unit) {
    if (tabs.size < 2) return
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { i, tab ->
            SegmentedButton(selected = tab == selected, onClick = { onSelect(tab) }, shape = SegmentedButtonDefaults.itemShape(i, tabs.size)) {
                Text(
                    stringResource(
                        when (tab) {
                            SheetTab.NOW -> Res.string.tab_now
                            SheetTab.HOW -> Res.string.tab_how
                            SheetTab.BETTER -> Res.string.tab_better
                        },
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The four laws as four small cards: what is in place now ("Rond het eten", or "Nog niets"), one suggestion and the
 * button that does it. The law the coach would pick ([focus]) is marked, so it's clear where to start; the family
 * picks one, never all four.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LawCards(laws: List<LawState>, tips: Map<HabitLaw, HabitTip>, focus: HabitLaw?, style: SectionStyle, lang: Lang, onTip: (HabitTip) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.better_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HabitLaw.entries.forEach { law ->
            val now = laws.firstOrNull { it.law == law }?.now
            val tip = tips[law]
            val marked = law == focus
            Column(
                Modifier.fillMaxWidth()
                    .background(if (marked) style.tint else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .then(if (marked) Modifier.border(1.5.dp, style.ink.copy(alpha = 0.5f), RoundedCornerShape(18.dp)) else Modifier)
                    .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(law.icon(), null, tint = style.ink, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(law.title(lang), style = MaterialTheme.typography.titleSmall)
                        Text(
                            now?.let { stringResource(Res.string.law_now, it) } ?: stringResource(Res.string.law_not_yet),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (now != null) style.ink else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (marked) Text(stringResource(Res.string.law_suggested), style = MaterialTheme.typography.labelSmall, color = style.ink, modifier = Modifier.padding(end = 8.dp))
                }
                if (tip != null) {
                    Text(tip.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 40.dp, top = 4.dp))
                    TextButton(onClick = { onTip(tip) }, modifier = Modifier.align(Alignment.End)) { Text(tip.action.label()) }
                }
            }
        }
    }
}

/** "Wanneer telt het?": one of the moments of the day, or none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnchorDialog(title: String, current: Anchor?, lang: Lang, onPick: (Anchor?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Anchor.entries.forEach { a -> ChoiceChip(a.title(lang), current == a, { onPick(a); onDismiss() }) }
                ChoiceChip(stringResource(Res.string.anchor_none), current == null, { onPick(null); onDismiss() })
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
