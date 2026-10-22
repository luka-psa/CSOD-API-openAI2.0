package app.hearth.ui

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
import app.hearth.core.model.plusDays
import app.hearth.core.model.weekdayName
import app.hearth.core.tools.backlog.When
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.delete
import app.hearth.ui.resources.delete_confirm
import app.hearth.ui.resources.delete_confirm_body
import app.hearth.ui.resources.mark_done
import app.hearth.ui.resources.no
import app.hearth.ui.resources.plan_do_on
import app.hearth.ui.resources.plan_finish_by
import app.hearth.ui.resources.plan_later
import app.hearth.ui.resources.plan_next
import app.hearth.ui.resources.plan_title
import app.hearth.ui.resources.plan_today
import app.hearth.ui.resources.plan_tomorrow
import app.hearth.ui.resources.plan_unplanned
import app.hearth.ui.resources.yes
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * One tap to decide when: Next up, Today, Tomorrow, one of the coming days, or Later.
 * "Do on" vs "Finish by" is chosen first; tapping a day applies it and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanSheet(
    item: BacklogItem,
    today: LocalDate,
    lang: Lang,
    onPlan: (When, LocalDate?, Boolean) -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var finishBy by remember(item.id) { mutableStateOf(item.finishBy) }
    var confirmDelete by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun choose(w: When, d: LocalDate? = null) {
        onPlan(w, d, finishBy && (w == When.TODAY || w == When.TOMORROW || w == When.DAY))
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(horizontal = 28.dp).padding(bottom = 28.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTag(item, lang)
                planText(item, today, lang)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(item.text, style = MaterialTheme.typography.headlineSmall)

            Text(stringResource(Res.string.plan_title), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !finishBy, onClick = { finishBy = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(stringResource(Res.string.plan_do_on))
                }
                SegmentedButton(selected = finishBy, onClick = { finishBy = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(stringResource(Res.string.plan_finish_by))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceChip(stringResource(Res.string.plan_next), item.plan == Plan.NEXT, { choose(When.NEXT) }, icon = HearthIcons.Next)
                ChoiceChip(stringResource(Res.string.plan_today), item.plan == Plan.DAY && item.localDate == today, { choose(When.TODAY) })
                ChoiceChip(stringResource(Res.string.plan_tomorrow), item.plan == Plan.DAY && item.localDate == today.plusDays(1), { choose(When.TOMORROW) })
                (2 until 7).map { today.plusDays(it) }.forEach { d ->
                    val label = "${weekdayName(d.dayOfWeek, lang).replaceFirstChar { it.uppercase() }} ${d.day}"
                    ChoiceChip(label, item.plan == Plan.DAY && item.localDate == d, { choose(When.DAY, d) })
                }
                ChoiceChip(stringResource(Res.string.plan_later), item.plan == Plan.LATER, { choose(When.LATER) }, icon = HearthIcons.Later)
                if (item.plan != Plan.UNPLANNED) {
                    ChoiceChip(stringResource(Res.string.plan_unplanned), false, { choose(When.UNPLANNED) }, icon = HearthIcons.Inbox)
                }
            }
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onDone(); onDismiss() }) {
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
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.delete_confirm, item.text)) },
            text = { Text(stringResource(Res.string.delete_confirm_body)) },
            confirmButton = {
                FilledTonalButton(onClick = { confirmDelete = false; onDelete(); onDismiss() }) { Text(stringResource(Res.string.yes)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.no)) } },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}
