package app.hearth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.dayLabel
import app.hearth.core.model.plusDays
import app.hearth.core.model.weekdayName
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.backlog_empty
import app.hearth.ui.resources.backlog_subtitle
import app.hearth.ui.resources.filter_all
import app.hearth.ui.resources.group_later
import app.hearth.ui.resources.group_next
import app.hearth.ui.resources.group_unplanned
import app.hearth.ui.resources.nav_backlog
import app.hearth.ui.resources.plan
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** Which slice of the backlog is shown: everything, one bucket, or one day. */
sealed interface BacklogFilter {
    data object All : BacklogFilter
    data object Unplanned : BacklogFilter
    data object Next : BacklogFilter
    data object Later : BacklogFilter
    data class Day(val date: LocalDate) : BacklogFilter
}

@Composable
fun BacklogScreen(
    backlog: List<BacklogItem>,
    today: LocalDate,
    lang: Lang,
    onComplete: (BacklogItem) -> Unit,
    onOpenItem: (BacklogItem) -> Unit,
) {
    var filter by remember { mutableStateOf<BacklogFilter>(BacklogFilter.All) }
    var section by rememberSaveable { mutableStateOf<Section?>(null) }

    val days = (0 until 7).map { today.plusDays(it) }
    fun onDay(item: BacklogItem, d: LocalDate) =
        item.plan == Plan.DAY && item.localDate?.let { if (d == today) it <= today else it == d } == true

    val bySection = backlog.filter { section == null || it.section == section }
    val shown = bySection.filter { item ->
        when (val f = filter) {
            BacklogFilter.All -> true
            BacklogFilter.Unplanned -> item.plan == Plan.UNPLANNED
            BacklogFilter.Next -> item.plan == Plan.NEXT
            BacklogFilter.Later -> item.plan == Plan.LATER
            is BacklogFilter.Day -> onDay(item, f.date)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(stringResource(Res.string.nav_backlog), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(Res.string.backlog_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(18.dp))

        // When: buckets and the next seven days, each with how much is on it.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
            item { ChoiceChip(stringResource(Res.string.filter_all), filter == BacklogFilter.All, { filter = BacklogFilter.All }, badge = bySection.size) }
            item {
                ChoiceChip(
                    stringResource(Res.string.group_unplanned), filter == BacklogFilter.Unplanned, { filter = BacklogFilter.Unplanned },
                    badge = bySection.count { it.plan == Plan.UNPLANNED }, icon = HearthIcons.Inbox,
                )
            }
            item {
                ChoiceChip(
                    stringResource(Res.string.group_next), filter == BacklogFilter.Next, { filter = BacklogFilter.Next },
                    badge = bySection.count { it.plan == Plan.NEXT }, icon = HearthIcons.Next,
                )
            }
            items(days) { d ->
                val label = if (d.toEpochDays() - today.toEpochDays() < 2) dayLabel(d, today, lang).replaceFirstChar { it.uppercase() }
                else "${weekdayName(d.dayOfWeek, lang, short = true).replaceFirstChar { it.uppercase() }} ${d.day}"
                ChoiceChip(label, filter == BacklogFilter.Day(d), { filter = BacklogFilter.Day(d) }, badge = bySection.count { onDay(it, d) })
            }
            item {
                ChoiceChip(
                    stringResource(Res.string.group_later), filter == BacklogFilter.Later, { filter = BacklogFilter.Later },
                    badge = bySection.count { it.plan == Plan.LATER }, icon = HearthIcons.Later,
                )
            }
        }
        Spacer(Modifier.size(10.dp))

        // What: only sections that have something in them.
        val present = backlog.map { it.section }.distinct().sortedBy { it.ordinal }
        if (present.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
                items(present) { s ->
                    ChoiceChip(s.label(lang).replaceFirstChar { it.uppercase() }, section == s, { section = if (section == s) null else s })
                }
            }
            Spacer(Modifier.size(14.dp))
        }

        Surface(Modifier.fillMaxWidth().weight(1f), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
            val grouped = groups(shown, today, lang)
            val planLabel = stringResource(Res.string.plan)
            if (shown.isEmpty()) {
                EmptyHint(stringResource(Res.string.backlog_empty), Modifier.padding(24.dp))
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    grouped.forEach { (title, items) ->
                        if (filter == BacklogFilter.All) {
                            item(key = "h-$title") {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp),
                                )
                            }
                        }
                        items(items, key = { it.id }) { item ->
                            BacklogRow(
                                item, today, lang,
                                onDone = { onComplete(item) },
                                onOpen = { onOpenItem(item) },
                                modifier = Modifier.animateItem(),
                            ) {
                                IconButton(onClick = { onOpenItem(item) }) {
                                    Icon(HearthIcons.Calendar, planLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** To plan · Next up · each day in order · Later. */
@Composable
private fun groups(items: List<BacklogItem>, today: LocalDate, lang: Lang): List<Pair<String, List<BacklogItem>>> {
    val unplanned = stringResource(Res.string.group_unplanned)
    val next = stringResource(Res.string.group_next)
    val later = stringResource(Res.string.group_later)
    val result = mutableListOf<Pair<String, List<BacklogItem>>>()
    items.filter { it.plan == Plan.UNPLANNED }.takeIf { it.isNotEmpty() }?.let { result += unplanned to it }
    items.filter { it.plan == Plan.NEXT }.takeIf { it.isNotEmpty() }?.let { result += next to it }
    items.filter { it.plan == Plan.DAY && it.localDate != null }
        .groupBy { maxOf(it.localDate!!, today) }
        .entries.sortedBy { it.key }
        .forEach { (d, list) -> result += dayLabel(d, today, lang).replaceFirstChar { it.uppercase() } to list }
    items.filter { it.plan == Plan.LATER }.takeIf { it.isNotEmpty() }?.let { result += later to it }
    return result
}
