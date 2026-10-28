package app.hearth.ui

import app.hearth.core.model.isRoutineStep
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
import app.hearth.core.model.Win
import app.hearth.ui.resources.wins_title
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
import app.hearth.core.model.isRoutine
import app.hearth.core.model.tickedOn
import app.hearth.core.model.isWeekend
import app.hearth.core.model.plannedOn
import app.hearth.core.model.plannedToday
import app.hearth.core.model.plusDays
import app.hearth.core.model.weekdayName
import app.hearth.core.tools.backlog.When
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.dash_coming_up
import app.hearth.ui.resources.dash_huddle
import app.hearth.ui.resources.dash_huddle_body
import app.hearth.ui.resources.dash_huddle_start
import app.hearth.ui.resources.dash_tonight
import app.hearth.ui.resources.dash_dont_forget
import app.hearth.ui.resources.dash_dont_forget_empty
import app.hearth.ui.resources.dash_free
import app.hearth.ui.resources.dash_rhythm
import app.hearth.ui.resources.dash_rhythm_empty
import app.hearth.ui.resources.done_today
import app.hearth.ui.resources.done_today_none
import app.hearth.ui.resources.free_all_done
import app.hearth.ui.resources.free_light
import app.hearth.ui.resources.free_nothing
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
import app.hearth.core.model.Topic
import app.hearth.core.model.TopicGroup
import app.hearth.core.model.WeekLook
import app.hearth.core.model.glance
import app.hearth.core.model.topic
import app.hearth.core.model.topics
import androidx.compose.foundation.layout.ColumnScope
import app.hearth.ui.resources.menu_tonight_empty
import app.hearth.ui.resources.nav_menu
import app.hearth.ui.resources.today_nothing
import app.hearth.core.model.isHabit
import app.hearth.core.model.scoreNow
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
    wins: List<Win>,
    suggestHuddle: Boolean,
    doneToday: Int,
    today: LocalDate,
    lang: Lang,
    wide: Boolean,
    onComplete: (BacklogItem) -> Unit,
    onPlan: (BacklogItem, When) -> Unit,
    onOpenItem: (BacklogItem) -> Unit,
    onGoTasks: () -> Unit,
    onGoShopping: () -> Unit,
    onGoBoard: () -> Unit,
    onGoMenu: () -> Unit,
    onGoWeek: () -> Unit,
    onGoConnect: () -> Unit,
    /** How each house rule goes (0–5), by rule id: it shows as its badge. */
    ruleScores: Map<String, Int> = emptyMap(),
) {
    val names = backlog.associate { it.id to it.text }
    val routineSteps = steps.filter { it.isRoutineStep }.groupBy { it.parentId!! }
    val urgent = dontForget(backlog, today)
    val planned = plannedToday(backlog, today)
    // Ticked off today, they stay (ticked, at the end): done work shows, and a tick by mistake is one tap to take back.
    val due = dueRoutines(backlog, today) + backlog.filter { it.isRoutine() && it.intervalDays != null && it.tickedOn(today) }
    // Routines with a moment, in the order the day goes; chores without one belong to the household.
    val rhythm = due.filter { it.topic == Topic.RHYTHM }.sortedBy { it.anchor?.ordinal ?: Int.MAX_VALUE }
    val chores = due.filter { it.topic == Topic.HOME }
    val hasRhythm = backlog.any { it.topic == Topic.RHYTHM }
    val next = backlog.filter { it.plan == Plan.NEXT && !it.waiting }
    val projects = backlog.filter { it.kind == TaskKind.PROJECT && !it.isStep }
    val dueToday = planned.size + urgent.count { it.urgent || (it.localDate?.let { d -> d <= today } ?: false) }
    val upcoming = (1..6).map { today.plusDays(it) }.map { it to plannedOn(backlog, it) }
    // Admin that isn't already above (Niet vergeten, Vandaag, Volgende, Binnenkort): waiting for a reply, still open.
    val above = (urgent + planned + next + upcoming.flatMap { it.second }).map { it.id }.toSet()
    val admin = backlog.filter { it.topic == Topic.ADMIN && it.id !in above }.sortedBy { !it.waiting }
    // Free time this week, on its day.
    val freeSoon = (0..6).flatMap { d -> plannedOn(backlog, today.plusDays(d)) }.filter { it.topic == Topic.FREE_TIME }

    @Composable
    fun ItemRow(item: BacklogItem) = BacklogRow(
        item, today, lang, onDone = { onComplete(item) }, onOpen = { onOpenItem(item) }, projectName = item.parentId?.let(names::get),
        // A routine with steps: tick them as the evening goes.
        below = routineSteps[item.id]?.let { mine -> { RoutineSteps(mine, today, onComplete) } },
    )

    val dontForgetCard = @Composable { m: Modifier ->
        HearthCard(stringResource(Res.string.dash_dont_forget), HearthIcons.Flag, m) {
            if (urgent.isEmpty()) EmptyHint(stringResource(Res.string.dash_dont_forget_empty))
            urgent.take(6).forEach { ItemRow(it) }
        }
    }
    val todayCard = @Composable { m: Modifier ->
        HearthCard(stringResource(Res.string.today_planned), HearthIcons.Sun, m) {
            // What's planned for today; the rhythm, free time and dinner have their own tiles below.
            if (planned.isEmpty() && next.isEmpty()) EmptyHint(stringResource(Res.string.today_nothing))
            planned.forEach { ItemRow(it) }
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
        }
    }

    // The household's topics under the day, every one of them, in the same order and two groups as the weekoverleg:
    // what we do and eat (six cards, two by two), and the rhythm and agreements on the shelf beside them (four), each
    // routine and house rule with how it goes. A topic with nothing for today only shows its few words.
    val look = WeekLook(backlog, board, meals.size, shopping.sumOf { sec -> sec.items.count { !it.bought } }, today, ruleScores)
    @Composable
    fun Card(topic: Topic, m: Modifier, action: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) =
        // The same few words as its weekoverleg tile; free time lists its days itself.
        TopicCard(topic, lang, m, glance = topic.glance(look, lang).takeIf { topic != Topic.FREE_TIME }, action = action, content = content)
    // Topic cards open their home with just the arrow, so a name never has to break (the shelf is narrow on a phone).
    val toTasks = @Composable { CardAction(stringResource(Res.string.open_tasks), onGoTasks, compact = true) }
    val toBoard = @Composable { CardAction(stringResource(Res.string.nav_board), onGoBoard, compact = true) }

    // A line on the shelf: its icon or how it goes, then its words; a tap opens the Werkbord.
    @Composable
    fun ShelfLine(text: String, lead: @Composable () -> Unit) = Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onGoBoard).padding(vertical = 5.dp),
    ) {
        lead()
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }

    val cards: Map<Topic, @Composable (Modifier) -> Unit> = mapOf(
        Topic.ADMIN to { m -> Card(Topic.ADMIN, m, toTasks) { admin.take(4).forEach { ItemRow(it) } } },
        Topic.PROJECTS to { m ->
            Card(Topic.PROJECTS, m, toTasks) { projects.take(3).forEach { project -> ProjectLine(project, steps.filter { it.parentId == project.id }, today, lang) { onOpenItem(project) } } }
        },
        Topic.HOME to { m -> Card(Topic.HOME, m, toTasks) { chores.take(5).forEach { ItemRow(it) } } },
        Topic.FREE_TIME to { m ->
            Card(Topic.FREE_TIME, m, toTasks) {
                FreeTime(dueToday, doneToday)
                freeSoon.forEach { ItemRow(it) }
                upcoming.firstOrNull { (day, items) -> day.isWeekend && items.isEmpty() }?.let { (day, _) ->
                    Spacer(Modifier.size(8.dp))
                    Hint(stringResource(Res.string.free_weekend, weekdayName(day.dayOfWeek, lang).replaceFirstChar { it.uppercase() }))
                }
            }
        },
        Topic.MENU to { m ->
            Card(Topic.MENU, m, { CardAction(stringResource(Res.string.nav_menu), onGoMenu, compact = true) }) {
                meals.firstOrNull { it.date == today.toString() }?.let { Tonight(it.dish) } ?: EmptyHint(stringResource(Res.string.menu_tonight_empty))
                meals.filter { it.date > today.toString() }.sortedBy { it.date }.take(2).forEach { meal ->
                    runCatching { LocalDate.parse(meal.date) }.getOrNull()?.let { day -> MealLine(dayLabel(day, today, lang), meal.dish) }
                }
            }
        },
        Topic.SHOPPING to { m ->
            Card(Topic.SHOPPING, m, { CardAction(stringResource(Res.string.open_list), onGoShopping, compact = true) }) {
                val open = shopping.flatMap { sec -> sec.items.filterNot { it.bought } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    open.take(12).forEach { item ->
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
        },
        Topic.RHYTHM to { m ->
            // What is due today, in the order the day goes, each habit with its score; then how the others go.
            Card(Topic.RHYTHM, m, toTasks) {
                if (hasRhythm && rhythm.isEmpty()) EmptyHint(stringResource(Res.string.dash_rhythm_empty))
                rhythm.take(6).forEach { item ->
                    BacklogRow(
                        item, today, lang, onDone = { onComplete(item) }, onOpen = { onOpenItem(item) },
                        below = routineSteps[item.id]?.let { mine -> { RoutineSteps(mine, today, onComplete) } },
                    ) { item.scoreNow?.takeIf { item.isHabit }?.let { ScoreBadge(it) } }
                }
                val shown = rhythm.map { it.id }.toSet()
                backlog.filter { it.isHabit && !it.done && it.id !in shown && it.scoreNow != null }.sortedBy { it.scoreNow }.take(3).forEach { h ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onOpenItem(h) }.padding(vertical = 5.dp, horizontal = 4.dp),
                    ) {
                        Text(h.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        ScoreBadge(h.scoreNow!!)
                    }
                }
            }
        },
        Topic.RULES to { m ->
            val rules = board.filter { it.kind == BoardKind.RULE && it.isOpen }
            Card(Topic.RULES, m, toBoard) {
                // How it goes: its badge; not rated yet: a quiet dot.
                rules.take(4).forEach { rule -> ShelfLine(rule.text) { ruleScores[rule.id]?.let { ScoreBadge(it) } ?: NoScoreMark(Topic.RULES.style().ink) } }
            }
        },
        Topic.FRICTION to { m ->
            val points = board.filter { it.isWorkingPoint && it.isOpen }.sortedBy { it.direction != null }
            Card(Topic.FRICTION, m, toBoard) {
                points.take(3).forEach { f -> ShelfLine(f.text) { Icon(Topic.FRICTION.icon(), null, tint = Topic.FRICTION.style().ink, modifier = Modifier.size(16.dp)) } }
            }
        },
        Topic.GOALS to { m ->
            val goals = board.filter { it.kind == BoardKind.GOAL && it.isOpen }
            Card(Topic.GOALS, m, toBoard) {
                goals.take(3).forEach { g ->
                    val linked = g.linkedId?.let { id -> backlog.firstOrNull { it.id == id } }
                    ShelfLine(g.text + (g.nextStep?.let { "  ·  $it" } ?: "")) {
                        linked?.scoreNow?.let { ScoreBadge(it) } ?: Icon(Topic.GOALS.icon(), null, tint = Topic.GOALS.style().ink, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
    )
    fun group(g: TopicGroup) = g.topics.mapNotNull { cards[it] }

    @Composable
    fun ShelfCards(m: Modifier) = Shelf(m) {
        GroupLabel(TopicGroup.BOARD, lang)
        group(TopicGroup.BOARD).forEach { it(Modifier.fillMaxWidth()) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Header(lang, doneToday, wide, onGoConnect)
        Spacer(Modifier.size(20.dp))
        val gap = if (wide) 20.dp else 16.dp
        val tasks = group(TopicGroup.TASKS)
        if (wide) {
            // The day first (what must not slip, today, the days ahead), then every topic right under it, as in the
            // weekoverleg: the six of what we do and eat two by two, the rhythm and agreements on the shelf beside them.
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                if (suggestHuddle) HuddleCard(onGoWeek)
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                        dontForgetCard(Modifier.fillMaxWidth())
                        if (wins.isNotEmpty()) WinsCard(wins, onGoWeek, Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1.15f)) { todayCard(Modifier.fillMaxWidth()) }
                    Column(Modifier.weight(1f)) { comingUpCard(Modifier.fillMaxWidth()) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(Modifier.weight(2.15f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GroupLabel(TopicGroup.TASKS, lang, Modifier.padding(top = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                                tasks.filterIndexed { i, _ -> i % 2 == 0 }.forEach { it(Modifier.fillMaxWidth()) }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                                tasks.filterIndexed { i, _ -> i % 2 == 1 }.forEach { it(Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                    ShelfCards(Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                if (suggestHuddle) HuddleCard(onGoWeek)
                if (urgent.isNotEmpty()) dontForgetCard(Modifier.fillMaxWidth())
                todayCard(Modifier.fillMaxWidth())
                comingUpCard(Modifier.fillMaxWidth())
                GroupLabel(TopicGroup.TASKS, lang, Modifier.padding(top = 6.dp))
                tasks.forEach { it(Modifier.fillMaxWidth()) }
                ShelfCards(Modifier.fillMaxWidth())
                if (wins.isNotEmpty()) WinsCard(wins, onGoWeek, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Wat ging goed deze week: done work made visible, until the weekoverleg takes it along. */
@Composable
private fun WinsCard(wins: List<Win>, onOpen: () -> Unit, modifier: Modifier) {
    HearthCard(stringResource(Res.string.wins_title), HearthIcons.Heart, modifier.clickable(onClick = onOpen)) {
        wins.takeLast(5).reversed().forEach { win ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(HearthIcons.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(win.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun FreeTime(dueToday: Int, doneToday: Int) {
    val load = dayLoad(dueToday)
    val text = when {
        load == DayLoad.FREE && doneToday > 0 -> stringResource(Res.string.free_all_done)
        load == DayLoad.FREE -> stringResource(Res.string.free_nothing)
        load == DayLoad.LIGHT -> stringResource(Res.string.free_light)
        else -> return
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
}

/** "Morgen   Curry met kip": a meal later this week, small under tonight's. */
@Composable
private fun MealLine(day: String, dish: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
        Text(day.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(dish, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            maxLines = 1,
            modifier = Modifier.width(84.dp).padding(top = 6.dp),
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
    val s = Topic.FREE_TIME.style()
    Surface(color = s.tint, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Icon(Topic.FREE_TIME.icon(), null, tint = s.ink, modifier = Modifier.size(18.dp))
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
                // On a phone there is no rail, so Instellingen lives here.
                androidx.compose.material3.IconButton(onClick = onGoConnect) {
                    Icon(HearthIcons.Settings, stringResource(Res.string.nav_connect), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
