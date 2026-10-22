package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.tools.backlog.When
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.done_today
import app.hearth.ui.resources.done_today_none
import app.hearth.ui.resources.greeting_afternoon
import app.hearth.ui.resources.greeting_evening
import app.hearth.ui.resources.greeting_morning
import app.hearth.ui.resources.open_backlog
import app.hearth.ui.resources.open_list
import app.hearth.ui.resources.plan_today
import app.hearth.ui.resources.shopping_list
import app.hearth.ui.resources.shopping_summary_empty
import app.hearth.ui.resources.today_empty
import app.hearth.ui.resources.today_next
import app.hearth.ui.resources.today_next_empty
import app.hearth.ui.resources.today_planned
import app.hearth.ui.resources.today_to_plan
import app.hearth.ui.resources.today_to_plan_count
import app.hearth.ui.resources.today_to_plan_none
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** "What matters now": today's plan, what's next, the shopping list at a glance, and what still needs a place. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    backlog: List<BacklogItem>,
    shopping: List<ShopSection>,
    doneToday: Int,
    today: LocalDate,
    lang: Lang,
    wide: Boolean,
    onComplete: (BacklogItem) -> Unit,
    onPlan: (BacklogItem, When) -> Unit,
    onOpenItem: (BacklogItem) -> Unit,
    onGoBacklog: () -> Unit,
    onGoShopping: () -> Unit,
) {
    val planned = backlog.filter { HomeViewModel.isToday(it, today) }.sortedBy { it.date }
    val next = backlog.filter { it.plan == Plan.NEXT }
    val unplanned = backlog.count { it.plan == Plan.UNPLANNED }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Header(lang, doneToday, wide)
        Spacer(Modifier.size(20.dp))

        val todayCard = @Composable { m: Modifier ->
            HearthCard(stringResource(Res.string.today_planned), HearthIcons.Sun, m) {
                if (planned.isEmpty()) EmptyHint(stringResource(Res.string.today_empty))
                planned.forEach { item ->
                    BacklogRow(item, today, lang, onDone = { onComplete(item) }, onOpen = { onOpenItem(item) })
                }
            }
        }
        val nextCard = @Composable { m: Modifier ->
            HearthCard(stringResource(Res.string.today_next), HearthIcons.Next, m) {
                if (next.isEmpty()) EmptyHint(stringResource(Res.string.today_next_empty))
                next.take(6).forEach { item ->
                    BacklogRow(item, today, lang, onDone = { onComplete(item) }, onOpen = { onOpenItem(item) }) {
                        TextButton(onClick = { onPlan(item, When.TODAY) }) { Text(stringResource(Res.string.plan_today)) }
                    }
                }
            }
        }
        val shoppingCard = @Composable { m: Modifier ->
            HearthCard(stringResource(Res.string.shopping_list), HearthIcons.Cart, m, action = { CardAction(stringResource(Res.string.open_list), onGoShopping) }) {
                val open = shopping.map { s -> s.copy(items = s.items.filterNot { it.bought }) }.filter { it.items.isNotEmpty() }
                if (open.isEmpty()) EmptyHint(stringResource(Res.string.shopping_summary_empty))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    open.forEach { section ->
                        section.items.forEach { item ->
                            Text(
                                item.name,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        val toPlanCard = @Composable { m: Modifier ->
            HearthCard(stringResource(Res.string.today_to_plan), HearthIcons.Inbox, m) {
                Text(
                    if (unplanned == 0) stringResource(Res.string.today_to_plan_none) else stringResource(Res.string.today_to_plan_count, unplanned),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (unplanned > 0) {
                    Spacer(Modifier.size(12.dp))
                    FilledTonalButton(onClick = onGoBacklog) { Text(stringResource(Res.string.open_backlog)) }
                }
            }
        }

        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    todayCard(Modifier.fillMaxWidth())
                    nextCard(Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    shoppingCard(Modifier.fillMaxWidth())
                    toPlanCard(Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                todayCard(Modifier.fillMaxWidth())
                nextCard(Modifier.fillMaxWidth())
                shoppingCard(Modifier.fillMaxWidth())
                toPlanCard(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Header(lang: Lang, doneToday: Int, wide: Boolean) {
    val hour = localNow().hour
    val greeting = stringResource(
        when {
            hour < 12 -> Res.string.greeting_morning
            hour < 18 -> Res.string.greeting_afternoon
            else -> Res.string.greeting_evening
        },
    )
    val title = @Composable {
        Text(formatDay(localNow(), lang), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(greeting, style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge)
    }
    if (wide) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { title() }
            DonePill(doneToday)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            title()
            DonePill(doneToday)
        }
    }
}

/** Done work made visible: the counter only ever goes up, and never shames an empty day. */
@Composable
private fun DonePill(doneToday: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(if (doneToday > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(HearthIcons.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (doneToday > 0) stringResource(Res.string.done_today, doneToday) else stringResource(Res.string.done_today_none),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
