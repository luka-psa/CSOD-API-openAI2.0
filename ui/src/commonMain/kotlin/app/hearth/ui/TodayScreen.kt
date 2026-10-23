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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.Meal
import androidx.compose.material3.Button
import app.hearth.core.model.DayLoad
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.TaskKind
import app.hearth.core.model.dayLabel
import app.hearth.core.model.dayLoad
import app.hearth.core.model.dontForget
import app.hearth.core.model.dueRoutines
import app.hearth.core.model.isWeekend
import app.hearth.core.model.plannedOn
import app.hearth.core.model.plannedToday
import app.hearth.core.model.plusDays
import app.hearth.core.model.weekdayName
import app.hearth.core.tools.backlog.When
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.dash_board
import app.hearth.ui.resources.dash_board_empty
import app.hearth.ui.resources.dash_board_frictions
import org.jetbrains.compose.resources.pluralStringResource
import app.hearth.ui.resources.dash_coming_up
import app.hearth.ui.resources.dash_huddle
import app.hearth.ui.resources.dash_huddle_body
import app.hearth.ui.resources.dash_huddle_start
import app.hearth.ui.resources.dash_tonight
import app.hearth.ui.resources.dash_dont_forget
import app.hearth.ui.resources.dash_dont_forget_empty
import app.hearth.ui.resources.dash_free
import app.hearth.ui.resources.dash_projects
import app.hearth.ui.resources.dash_rhythm
import app.hearth.ui.resources.dash_rhythm_empty
import app.hearth.ui.resources.done_today
import app.hearth.ui.resources.done_today_none
import app.hearth.ui.resources.free_all_done
import app.hearth.ui.resources.free_light
import app.hearth.ui.resources.free_nothing
import app.hearth.ui.resources.free_title
import app.hearth.ui.resources.free_weekend
import app.hearth.ui.resources.greeting_afternoon
import app.hearth.ui.resources.greeting_evening
import app.hearth.ui.resources.greeting_morning
import app.hearth.ui.resources.nav_board
import app.hearth.ui.resources.nav_connect
import app.hearth.ui.resources.next_step
import app.hearth.ui.resources.no_steps
import app.hearth.ui.resources.open_list
import app.hearth.ui.resources.open_tasks
import app.hearth.ui.resources.plan_today
import app.hearth.ui.resources.shopping_list
import app.hearth.ui.resources.shopping_summary_empty
import app.hearth.ui.resources.steps_progress
import app.hearth.ui.resources.today_empty
import app.hearth.ui.resources.today_next
import app.hearth.ui.resources.today_planned
import app.hearth.ui.theme.style
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * The tablet dashboard, readable from across the room:
 * don't forget · today (with the rhythm) · coming up (with free days) · projects · shopping.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    backlog: List<BacklogItem>,
    steps: List<BacklogItem>,
    shopping: List<ShopSection>,
    board: List<BoardItem>,
    meals: List<Meal>,
    suggestHuddle: Boolean,
    doneToday: Int,
    today: LocalDate,
    lang: Lang,
    wide: Boolean,
    onComplete: (BacklogItem) -> Unit,
    onPlan: (BacklogItem, When) -> Unit,
    onOpenItem: (BacklogItem) -> Unit,
    onGoTasks: () -> Unit,
    onGoProjects: () -> Unit,
    onGoShopping: () -> Unit,
    onGoBoard: () -> Unit,
    onGoWeek: () -> Unit,
    onGoConnect: () -> Unit,
) {
    val names = backlog.associate { it.id to it.text }
    val urgent = dontForget(backlog, today)
    val planned = plannedToday(backlog, today)
    val rhythm = dueRoutines(backlog, today)
    val next = backlog.filter { it.plan == Plan.NEXT && !it.waiting }
    val projects = backlog.filter { it.kind == TaskKind.PROJECT && !it.isStep }
    val dueToday = planned.size + urgent.count { it.urgent || (it.localDate?.let { d -> d <= today } ?: false) }
    val upcoming = (1..6).map { today.plusDays(it) }.map { it to plannedOn(backlog, it) }

    @Composable
    fun ItemRow(item: BacklogItem) = BacklogRow(item, today, lang, onDone = { onComplete(item) }, onOpen = { onOpenItem(item) }, projectName = item.parentId?.let(names::get))

    val dontForgetCard = @Composable { m: Modifier ->
        HearthCard(stringResource(Res.string.dash_dont_forget), HearthIcons.Flag, m) {
            if (urgent.isEmpty()) EmptyHint(stringResource(Res.string.dash_dont_forget_empty))
            urgent.take(6).forEach { ItemRow(it) }
        }
    }
    val todayCard = @Composable { m: Modifier ->
        HearthCard(stringResource(Res.string.today_planned), HearthIcons.Sun, m) {
            meals.firstOrNull { it.date == today.toString() }?.let { Tonight(it.dish) }
            FreeTime(dueToday, doneToday, lang)
            // With nothing due, the free-time note above says it all.
            if (planned.isEmpty() && rhythm.isEmpty() && dueToday > 0) EmptyHint(stringResource(Res.string.today_empty))
            planned.forEach { ItemRow(it) }
            if (rhythm.isNotEmpty() || planned.isNotEmpty()) {
                SubHeader(stringResource(Res.string.dash_rhythm))
                if (rhythm.isEmpty()) EmptyHint(stringResource(Res.string.dash_rhythm_empty))
                rhythm.take(6).forEach { ItemRow(it) }
            }
            if (next.isNotEmpty()) {
                SubHeader(stringResource(Res.string.today_next))
                next.take(4).forEach { item ->
                    BacklogRow(item, today, lang, onDone = { onComplete(item) }, onOpen = { onOpenItem(item) }, projectName = item.parentId?.let(names::get)) {
                        TextButton(onClick = { onPlan(item, When.TODAY) }) { Text(stringResource(Res.string.plan_today)) }
                    }
                }
            }
        }
    }
    val comingUpCard = @Composable { m: Modifier ->
        HearthCard(stringResource(Res.string.dash_coming_up), HearthIcons.Calendar, m, action = { CardAction(stringResource(Res.string.open_tasks), onGoTasks, compact = wide) }) {
            upcoming.forEach { (day, items) -> DayLine(day, items, today, lang, names, onOpenItem) }
            upcoming.firstOrNull { (day, items) -> day.isWeekend && items.isEmpty() }?.let { (day, _) ->
                Spacer(Modifier.size(10.dp))
                Hint(stringResource(Res.string.free_weekend, weekdayName(day.dayOfWeek, lang).replaceFirstChar { it.uppercase() }))
            }
        }
    }
    val projectsCard = @Composable { m: Modifier ->
        HearthCard(stringResource(Res.string.dash_projects), HearthIcons.Backlog, m, action = { CardAction(stringResource(Res.string.open_tasks), onGoProjects, compact = wide) }) {
            projects.take(4).forEach { project -> ProjectLine(project, steps.filter { it.parentId == project.id }, today, lang) { onOpenItem(project) } }
        }
    }
    val boardCard = @Composable { m: Modifier ->
        val rules = board.filter { it.kind == BoardKind.RULE && it.isOpen }
        val frictions = board.count { it.kind == BoardKind.FRICTION && it.isOpen }
        HearthCard(stringResource(Res.string.dash_board), HearthIcons.Rule, m, action = { CardAction(stringResource(Res.string.nav_board), onGoBoard, compact = wide) }) {
            if (rules.isEmpty() && frictions == 0) EmptyHint(stringResource(Res.string.dash_board_empty))
            rules.take(3).forEach { rule ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                    Box(Modifier.size(8.dp).background(Section.NOTE.style().ink, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(rule.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (frictions > 0) {
                Spacer(Modifier.size(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onGoBoard).padding(vertical = 4.dp),
                ) {
                    Icon(HearthIcons.Bolt, null, tint = Section.FRICTION.style().ink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(pluralStringResource(Res.plurals.dash_board_frictions, frictions, frictions), style = MaterialTheme.typography.labelLarge, color = Section.FRICTION.style().ink)
                }
            }
        }
    }
    val shoppingCard = @Composable { m: Modifier ->
        HearthCard(stringResource(Res.string.shopping_list), HearthIcons.Cart, m, action = { CardAction(stringResource(Res.string.open_list), onGoShopping, compact = wide) }) {
            val open = shopping.flatMap { s -> s.items.filterNot { it.bought } }
            if (open.isEmpty()) EmptyHint(stringResource(Res.string.shopping_summary_empty))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                open.forEach { item ->
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Header(lang, doneToday, wide, onGoConnect)
        Spacer(Modifier.size(20.dp))
        val gap = if (wide) 20.dp else 16.dp
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                    dontForgetCard(Modifier.fillMaxWidth())
                    shoppingCard(Modifier.fillMaxWidth())
                    boardCard(Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(gap)) {
                    if (suggestHuddle) HuddleCard(onGoWeek)
                    todayCard(Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                    comingUpCard(Modifier.fillMaxWidth())
                    if (projects.isNotEmpty()) projectsCard(Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                if (suggestHuddle) HuddleCard(onGoWeek)
                if (urgent.isNotEmpty()) dontForgetCard(Modifier.fillMaxWidth())
                todayCard(Modifier.fillMaxWidth())
                comingUpCard(Modifier.fillMaxWidth())
                if (projects.isNotEmpty()) projectsCard(Modifier.fillMaxWidth())
                shoppingCard(Modifier.fillMaxWidth())
                boardCard(Modifier.fillMaxWidth())
            }
        }
    }
}

/** "Vanavond: spaghetti", from the meal plan. */
@Composable
private fun Tonight(dish: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)) {
        Text(stringResource(Res.string.dash_tonight), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(dish, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A gentle suggestion near the weekend when no meeting was held this week. Never a ping. */
@Composable
private fun HuddleCard(onStart: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.onBackground, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
            Icon(HearthIcons.People, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.dash_huddle), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.surface)
                Text(stringResource(Res.string.dash_huddle_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            }
            Spacer(Modifier.width(10.dp))
            Button(onClick = onStart) { Text(stringResource(Res.string.dash_huddle_start)) }
        }
    }
}

/** Free time, generated from how full today is. No durations needed: nothing left means free. */
@Composable
private fun FreeTime(dueToday: Int, doneToday: Int, lang: Lang) {
    val load = dayLoad(dueToday)
    val text = when {
        load == DayLoad.FREE && doneToday > 0 -> stringResource(Res.string.free_all_done)
        load == DayLoad.FREE -> stringResource(Res.string.free_nothing)
        load == DayLoad.LIGHT -> stringResource(Res.string.free_light)
        else -> return
    }
    val s = Section.PROTECTED_TIME.style()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).background(s.tint, MaterialTheme.shapes.medium).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(HearthIcons.Sun, null, tint = s.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(Res.string.free_title), style = MaterialTheme.typography.titleSmall, color = s.ink)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** One day ahead: its name, then what's on it as compact lines, or a calm "vrij". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayLine(day: LocalDate, items: List<BacklogItem>, today: LocalDate, lang: Lang, names: Map<String, String>, onOpen: (BacklogItem) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        val label = if (day == today.plusDays(1)) dayLabel(day, today, lang).replaceFirstChar { it.uppercase() }
        else "${weekdayName(day.dayOfWeek, lang, short = true).replaceFirstChar { it.uppercase() }} ${day.day}"
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (day.isWeekend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp).padding(top = 6.dp),
        )
        if (items.isEmpty()) {
            val s = Section.PROTECTED_TIME.style()
            Text(
                stringResource(Res.string.dash_free),
                style = MaterialTheme.typography.labelMedium,
                color = s.ink,
                modifier = Modifier.padding(top = 4.dp).background(s.tint, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onOpen(item) }.padding(vertical = 5.dp, horizontal = 4.dp),
                    ) {
                        Box(Modifier.size(8.dp).background(item.section.style().ink, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        val prefix = item.parentId?.let(names::get)?.let { "$it: " } ?: ""
                        Text("$prefix${item.text}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (item.finishBy) Icon(HearthIcons.Flag, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/** A project with its progress bar and the next step. */
@Composable
private fun ProjectLine(project: BacklogItem, steps: List<BacklogItem>, today: LocalDate, lang: Lang, onOpen: () -> Unit) {
    val done = steps.count { it.done }
    val nextStep = steps.filterNot { it.done }.sortedWith(compareBy({ it.plan != Plan.DAY }, { it.date ?: "" })).firstOrNull()
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onOpen).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(project.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            statusText(project, today, lang)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (steps.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { done.toFloat() / steps.size },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = Section.PROJECT.style().ink,
                trackColor = Section.PROJECT.style().tint,
                drawStopIndicator = {},
            )
        }
        Text(
            when {
                nextStep != null -> stringResource(Res.string.next_step, nextStep.text) + "  ·  " + stringResource(Res.string.steps_progress, done, steps.size)
                steps.isNotEmpty() -> stringResource(Res.string.steps_progress, done, steps.size)
                else -> stringResource(Res.string.no_steps)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun Hint(text: String) {
    val s = Section.PROTECTED_TIME.style()
    Surface(color = s.tint, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Icon(HearthIcons.Sun, null, tint = s.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = s.ink)
        }
    }
}

@Composable
private fun Header(lang: Lang, doneToday: Int, wide: Boolean, onGoConnect: () -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonePill(doneToday)
                Spacer(Modifier.weight(1f))
                // On a phone there is no rail, so "Verbind AI" lives here.
                androidx.compose.material3.IconButton(onClick = onGoConnect) {
                    Icon(HearthIcons.Link, stringResource(Res.string.nav_connect), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
