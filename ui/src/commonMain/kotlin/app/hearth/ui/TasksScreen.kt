package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hearth.core.model.Anchor
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Ease
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.model.RoutineState
import app.hearth.core.model.Section
import app.hearth.core.model.Topic
import app.hearth.core.model.WeekLook
import app.hearth.core.model.glance
import app.hearth.core.model.isHabit
import app.hearth.core.model.isRoutine
import app.hearth.core.model.isRoutineStep
import app.hearth.core.model.routineState
import app.hearth.core.model.title
import app.hearth.core.model.topic
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.add_step_hint
import app.hearth.ui.resources.admin_add_hint
import app.hearth.ui.resources.admin_empty
import app.hearth.ui.resources.ease_postponed
import app.hearth.ui.resources.ease_rate
import app.hearth.ui.resources.fold
import app.hearth.ui.resources.free_add_hint
import app.hearth.ui.resources.free_empty
import app.hearth.ui.resources.group_deadline
import app.hearth.ui.resources.group_due
import app.hearth.ui.resources.group_habits
import app.hearth.ui.resources.group_lists
import app.hearth.ui.resources.group_no_rhythm
import app.hearth.ui.resources.group_on_track
import app.hearth.ui.resources.group_open
import app.hearth.ui.resources.group_urgent
import app.hearth.ui.resources.group_waiting
import app.hearth.ui.resources.home_add_hint
import app.hearth.ui.resources.home_none
import app.hearth.ui.resources.nav_tasks
import app.hearth.ui.resources.no_steps
import app.hearth.ui.resources.plan
import app.hearth.ui.resources.project_add_hint
import app.hearth.ui.resources.projects_empty
import app.hearth.ui.resources.rhythm_add_hint
import app.hearth.ui.resources.rhythm_empty
import app.hearth.ui.resources.routine_due
import app.hearth.ui.resources.routine_soon
import app.hearth.ui.resources.small_step_add
import app.hearth.ui.resources.small_step_hint
import app.hearth.ui.resources.small_step_title
import app.hearth.ui.resources.sort_count
import app.hearth.ui.resources.sort_hint
import app.hearth.ui.resources.steps_progress
import app.hearth.ui.resources.tab_sort
import app.hearth.ui.resources.tasks_subtitle
import app.hearth.ui.theme.SectionStyle
import app.hearth.ui.theme.UnsortedStyle
import app.hearth.ui.theme.style
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Callbacks shared by every panel on the Tasks screen. */
class TaskCallbacks(
    val complete: (BacklogItem) -> Unit,
    val reopen: (BacklogItem) -> Unit,
    val open: (BacklogItem) -> Unit,
    val add: (String, Section) -> Unit,
    val addStep: (BacklogItem, String) -> Unit,
    /** Asks first (the screen shows a confirm), then deletes. */
    val delete: (BacklogItem) -> Unit,
    val rateHabit: (BacklogItem, Ease) -> Unit = { _, _ -> },
    val smallStep: (BacklogItem, String) -> Unit = { _, _ -> },
)

/** The topics that hold work to do; the others live on their own screens (Boodschappen, Weekmenu, Werkbord). */
private val TASK_TOPICS = listOf(Topic.ADMIN, Topic.PROJECTS, Topic.HOME, Topic.RHYTHM, Topic.FREE_TIME)

/**
 * Taken: everything there is to do, as a dashboard of the household's topics, the same panels (name, icon,
 * colour, glance) as the tiles of the weekoverleg. Each panel shows its work the way that kind of work runs:
 * routines per moment of the day, admin by urgency, big tasks with their steps. What isn't sorted yet sits on top.
 * A panel folds with a tap on its title; on a phone they start folded, so the first look is the overview.
 */
@Composable
fun TasksScreen(
    backlog: List<BacklogItem>,
    steps: List<BacklogItem>,
    today: LocalDate,
    lang: Lang,
    habits: Map<String, Ease>,
    callbacks: TaskCallbacks,
) {
    val routineSteps = steps.filter { it.isRoutineStep }.groupBy { it.parentId!! }
    CompositionLocalProvider(LocalEase provides habits, LocalRoutineSteps provides routineSteps) {
    val open = backlog.filter { !it.isStep }
    val byTopic = TASK_TOPICS.associateWith { t -> open.filter { it.topic == t } }
    val toSort = open.filter { it.topic !in TASK_TOPICS }
    val look = WeekLook(backlog, emptyList(), 0, 0, today)
    val narrowScreen = LocalNarrow.current
    val folded = remember { mutableStateMapOf<Topic, Boolean>() }

    val panels: List<TaskPanel> = TASK_TOPICS.map { topic ->
        val mine = byTopic.getValue(topic)
        TaskPanel(
            topic = topic,
            // Rough height in rows, to spread the panels evenly over the columns.
            weight = 3 + when (topic) {
                Topic.PROJECTS -> mine.sumOf { p -> 3 + steps.count { it.parentId == p.id } }
                Topic.RHYTHM -> mine.size + mine.count { it.id in routineSteps } + Anchor.entries.count { a -> mine.any { it.anchor == a } }
                else -> mine.size
            },
        ) {
            when (topic) {
                Topic.ADMIN -> AdminPanel(mine, today, lang, callbacks)
                Topic.PROJECTS -> ProjectsPanel(mine, steps, today, lang, callbacks)
                Topic.HOME, Topic.RHYTHM -> RoutinePanel(topic, mine, today, lang, callbacks, routineSteps.keys)
                else -> FreeTimePanel(mine, today, lang, callbacks)
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(stringResource(Res.string.nav_tasks), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(Res.string.tasks_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(18.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = if (narrowScreen) 16.dp else 20.dp
            val columns = when {
                maxWidth >= 1000.dp -> 3
                maxWidth >= 640.dp -> 2
                else -> 1
            }
            val panelWidth = (maxWidth - gap * (columns - 1)) / columns
            val tight = narrowScreen || maxWidth < 480.dp
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                // Not sorted yet: on top, because it asks for one tap.
                if (toSort.isNotEmpty()) SortPanel(toSort, today, lang, callbacks, tight)
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    spread(panels, columns).forEach { column ->
                        Column(Modifier.width(panelWidth), verticalArrangement = Arrangement.spacedBy(gap)) {
                            column.forEach { panel ->
                                val isOpen = folded[panel.topic]?.not() ?: !narrowScreen
                                PanelCard(panel.topic, panel.topic.glance(look, lang), lang, isOpen, { folded[panel.topic] = isOpen }, panelWidth) {
                                    panel.content()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

private class TaskPanel(val topic: Topic, val weight: Int, val content: @Composable () -> Unit)

/** Each panel into the column that is shortest so far, in order: reading order stays, the columns stay even. */
private fun spread(panels: List<TaskPanel>, columns: Int): List<List<TaskPanel>> {
    val out = List(columns) { mutableListOf<TaskPanel>() }
    val heights = IntArray(columns)
    panels.forEach { p ->
        val i = heights.indices.minBy { heights[it] }
        out[i] += p
        heights[i] += p.weight
    }
    return out
}

/** A topic panel: the topic's card with its glance; the title folds it. Narrow panels give rows their text room. */
@Composable
private fun PanelCard(topic: Topic, glance: String, lang: Lang, open: Boolean, onToggle: () -> Unit, width: Dp, content: @Composable () -> Unit) {
    TopicCard(
        topic, lang, Modifier.fillMaxWidth(), glance = glance,
        action = { FoldIcon(open, onToggle) },
        onTitleClick = onToggle,
    ) {
        if (open) CompositionLocalProvider(LocalNarrow provides (LocalNarrow.current || width < 480.dp)) { content() }
    }
}

@Composable
private fun FoldIcon(open: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(HearthIcons.ChevronRight, stringResource(Res.string.fold), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(if (open) -90f else 90f))
    }
}

/** Te sorteren: what Hearth couldn't place. A tap on a row asks where it belongs. */
@Composable
private fun SortPanel(items: List<BacklogItem>, today: LocalDate, lang: Lang, cb: TaskCallbacks, narrow: Boolean) {
    HearthCard(
        stringResource(Res.string.tab_sort), HearthIcons.Inbox, Modifier.fillMaxWidth(), style = UnsortedStyle,
        subtitle = pluralStringResource(Res.plurals.sort_count, items.size, items.size),
    ) {
        Text(stringResource(Res.string.sort_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CompositionLocalProvider(LocalNarrow provides narrow) {
            items.forEach { PlanRow(it, today, lang, cb) }
        }
    }
}

// ---------- routines (Ritmes & routines, Huishouden) ----------

/**
 * Per moment of the day in the order the day goes (the morning routine first), then checklists (used when
 * needed), habits (rated, never ticked), and what comes back by itself: aan de beurt, loopt goed, nog geen ritme.
 */
@Composable
private fun RoutinePanel(topic: Topic, routines: List<BacklogItem>, today: LocalDate, lang: Lang, cb: TaskCallbacks, withSteps: Set<String>) {
    if (routines.isEmpty()) EmptyHint(stringResource(if (topic == Topic.RHYTHM) Res.string.rhythm_empty else Res.string.home_none))
    Anchor.entries.forEach { a -> Group(a.title(lang), routines.filter { it.anchor == a }, today, lang, cb) }
    Group(stringResource(Res.string.group_lists), routines.filter { it.anchor == null && it.id in withSteps }, today, lang, cb)
    val loose = routines.filter { it.anchor == null && it.id !in withSteps }
    Group(stringResource(Res.string.group_habits), loose.filter { it.isHabit }, today, lang, cb)
    val ticked = loose.filterNot { it.isHabit }
    val due = ticked.filter { it.intervalDays != null && it.routineState(today).let { s -> s == RoutineState.DUE || s == RoutineState.NEW } }
    val noRhythm = ticked.filter { it.intervalDays == null }
    val onTrack = ticked - due.toSet() - noRhythm.toSet()
    Group(stringResource(Res.string.group_due), due.sortedBy { it.intervalDays }, today, lang, cb)
    Group(stringResource(Res.string.group_on_track), onTrack.sortedBy { it.daysSinceRatio(today) }.reversed(), today, lang, cb)
    Group(stringResource(Res.string.group_no_rhythm), noRhythm, today, lang, cb)
    Spacer(Modifier.size(10.dp))
    AddRow(
        stringResource(if (topic == Topic.RHYTHM) Res.string.rhythm_add_hint else Res.string.home_add_hint),
        { cb.add(it, if (topic == Topic.RHYTHM) Section.RHYTHM else Section.CHORE) },
        muted = true, compact = true,
    )
}

private fun BacklogItem.daysSinceRatio(today: LocalDate): Double {
    val interval = intervalDays ?: return 0.0
    val since = lastDoneDate?.let { (today.toEpochDays() - it.toEpochDays()).toDouble() } ?: return 1.0
    return since / interval
}

// ---------- Admin & afspraken ----------

/** Urgent · With an end date · Waiting for a reply · Open. */
@Composable
private fun AdminPanel(admin: List<BacklogItem>, today: LocalDate, lang: Lang, cb: TaskCallbacks) {
    if (admin.isEmpty()) EmptyHint(stringResource(Res.string.admin_empty))
    val urgent = admin.filter { it.urgent }
    val waiting = admin.filter { it.waiting && !it.urgent }
    val deadline = admin.filter { !it.urgent && !it.waiting && it.finishBy && it.plan == Plan.DAY }.sortedBy { it.date }
    val rest = admin - urgent.toSet() - waiting.toSet() - deadline.toSet()
    Group(stringResource(Res.string.group_urgent), urgent, today, lang, cb)
    Group(stringResource(Res.string.group_deadline), deadline, today, lang, cb)
    Group(stringResource(Res.string.group_waiting), waiting.sortedBy { it.date }, today, lang, cb)
    Group(stringResource(Res.string.group_open), rest.sortedWith(compareBy({ it.plan.ordinal }, { it.date ?: "" })), today, lang, cb, titled = urgent.isNotEmpty() || deadline.isNotEmpty() || waiting.isNotEmpty())
    Spacer(Modifier.size(10.dp))
    AddRow(stringResource(Res.string.admin_add_hint), { cb.add(it, Section.ADMIN) }, muted = true, compact = true)
}

// ---------- Grote taken ----------

@Composable
private fun ProjectsPanel(projects: List<BacklogItem>, steps: List<BacklogItem>, today: LocalDate, lang: Lang, cb: TaskCallbacks) {
    if (projects.isEmpty()) EmptyHint(stringResource(Res.string.projects_empty))
    projects.forEach { project -> ProjectBlock(project, steps.filter { it.parentId == project.id }, today, lang, cb, Modifier) }
    Spacer(Modifier.size(10.dp))
    AddRow(stringResource(Res.string.project_add_hint), { cb.add(it, Section.PROJECT) }, muted = true, compact = true)
}

// ---------- Vrije tijd ----------

@Composable
private fun FreeTimePanel(items: List<BacklogItem>, today: LocalDate, lang: Lang, cb: TaskCallbacks) {
    if (items.isEmpty()) EmptyHint(stringResource(Res.string.free_empty))
    items.sortedWith(compareBy({ it.date ?: "9999" }, { it.createdAt })).forEach { PlanRow(it, today, lang, cb, showSection = false) }
    Spacer(Modifier.size(10.dp))
    AddRow(stringResource(Res.string.free_add_hint), { cb.add(it, Section.PROTECTED_TIME) }, muted = true, compact = true)
}

/** A project: title with its slot, progress, its steps (ticked ones stay, struck through), and an add-step field. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProjectBlock(project: BacklogItem, steps: List<BacklogItem>, today: LocalDate, lang: Lang, cb: TaskCallbacks, modifier: Modifier) {
    val style = Topic.PROJECTS.style()
    val done = steps.count { it.done }
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp).background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f), MaterialTheme.shapes.medium).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { cb.open(project) }.padding(start = 6.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(project.text, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    if (project.urgent) UrgentTag(lang)
                    // Pushed back a few times: a softer colour, a hint to make the first step smaller.
                    val pushed = if (project.postponed >= 4) Ease.HARD else if (project.postponed >= 2) Ease.EFFORT else null
                    pushed?.let { EasePill(it, stringResource(Res.string.ease_postponed, project.postponed)) }
                    statusText(project, today, lang)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(
                        if (steps.isEmpty()) stringResource(Res.string.no_steps) else stringResource(Res.string.steps_progress, done, steps.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (steps.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else style.ink,
                    )
                }
            }
            if (!LocalNarrow.current) IconButton(onClick = { cb.open(project) }) { Icon(HearthIcons.Calendar, stringResource(Res.string.plan), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (steps.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            LinearProgressIndicator(
                progress = { done.toFloat() / steps.size },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = style.ink,
                trackColor = style.tint,
                drawStopIndicator = {},
            )
            Spacer(Modifier.size(4.dp))
            steps.sortedBy { it.done }.forEach { step ->
                BacklogRow(
                    step, today, lang,
                    onDone = { if (step.done) cb.reopen(step) else cb.complete(step) },
                    onOpen = { if (!step.done) cb.open(step) },
                    showSection = false,
                    onDelete = { cb.delete(step) },
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        AddRow(stringResource(Res.string.add_step_hint), { cb.addStep(project, it) }, muted = true, compact = true)
    }
}

// ---------- shared ----------

@Composable
private fun Group(title: String, items: List<BacklogItem>, today: LocalDate, lang: Lang, cb: TaskCallbacks, titled: Boolean = true) {
    if (items.isEmpty()) return
    if (titled) GroupTitle(title)
    // Inside its panel the topic is clear; the tag only tells an appointment from paperwork.
    items.forEach { item -> PlanRow(item, today, lang, cb, showSection = item.section == Section.APPOINTMENT) }
}

/** A row with a routine's state pill ("aan de beurt") and a plan button. */
@Composable
private fun PlanRow(item: BacklogItem, today: LocalDate, lang: Lang, cb: TaskCallbacks, modifier: Modifier = Modifier, showSection: Boolean = !item.isRoutine()) {
    val ease = LocalEase.current[item.id]
    val narrow = LocalNarrow.current
    val mySteps = LocalRoutineSteps.current[item.id].orEmpty()
    // A routine with steps is ticked step by step; how it goes is rated in the weekoverleg.
    val rated = item.isHabit && mySteps.isEmpty()
    // Atomic Habits: green runs easily, amber takes effort, orange is too much (make it smaller). Never red.
    val pill = (@Composable { EasePill(ease!!, if (item.postponed >= 2 && !item.isRoutine()) stringResource(Res.string.ease_postponed, item.postponed) else null) })
        .takeIf { !rated && ease != null && ease != Ease.EASY }
    BacklogRow(
        item, today, lang, onDone = { cb.complete(item) }, onOpen = { cb.open(item) }, modifier = modifier, showSection = showSection, onDelete = { cb.delete(item) },
        // In a narrow panel the chips go under the name, so the name keeps its room.
        below = if (mySteps.isEmpty() && !(narrow && (rated || pill != null))) null else ({
            if (narrow && rated) EaseMenu(item, cb)
            if (narrow) pill?.invoke()
            RoutineSteps(mySteps, today, cb.complete)
        }),
    ) {
        if (rated && !narrow) {
            EaseMenu(item, cb)
            Spacer(Modifier.width(6.dp))
        } else if (!narrow && pill != null) {
            pill()
            Spacer(Modifier.width(6.dp))
        }
        if (item.isRoutine() && item.plan != Plan.DAY) {
            when (item.routineState(today)) {
                RoutineState.DUE -> StatePill(stringResource(Res.string.routine_due), strong = true)
                RoutineState.SOON -> StatePill(stringResource(Res.string.routine_soon), strong = false)
                else -> {}
            }
        }
        // On a phone the row itself opens planning; the icon would only squeeze the text.
        if (!LocalNarrow.current) IconButton(onClick = { cb.open(item) }) { Icon(HearthIcons.Calendar, stringResource(Res.string.plan), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Each open item's colour, for every list on this screen. */
private val LocalEase = staticCompositionLocalOf<Map<String, Ease>> { emptyMap() }

/** Each routine's steps, by routine id. */
private val LocalRoutineSteps = staticCompositionLocalOf<Map<String, List<BacklogItem>>> { emptyMap() }

/** A habit's colour, set together: tap to change it or to give it a smaller first step. */
@Composable
private fun EaseMenu(item: BacklogItem, cb: TaskCallbacks) {
    var open by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    Box {
        val ease = item.ease
        if (ease == null) ChoiceChip(stringResource(Res.string.ease_rate), false, { open = true }) else EasePill(ease, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Ease.entries.forEach { e ->
                DropdownMenuItem(text = { Text(stringResource(e.label())) }, onClick = { open = false; cb.rateHabit(item, e) })
            }
            DropdownMenuItem(text = { Text(stringResource(Res.string.small_step_add)) }, onClick = { open = false; asking = true })
        }
    }
    if (asking) {
        TextDialog(stringResource(Res.string.small_step_title), stringResource(Res.string.small_step_hint), onDone = { t, _ -> cb.smallStep(item, t) }) { asking = false }
    }
}

@Composable
private fun StatePill(text: String, strong: Boolean) {
    val s = Topic.RHYTHM.style()
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (strong) MaterialTheme.colorScheme.surface else s.ink,
        modifier = Modifier.background(if (strong) s.ink else s.tint, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
internal fun GroupTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
internal fun ListSurface(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, content = content)
}
