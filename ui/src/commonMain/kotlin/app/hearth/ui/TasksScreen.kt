package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.model.RoutineState
import app.hearth.core.model.Section
import app.hearth.core.model.TaskKind
import app.hearth.core.model.isRoutine
import app.hearth.core.model.routineState
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.add_admin_hint
import app.hearth.ui.resources.add_project_hint
import app.hearth.ui.resources.add_routine_hint
import app.hearth.ui.resources.add_step_hint
import app.hearth.ui.resources.empty_admin
import app.hearth.ui.resources.empty_projects
import app.hearth.ui.resources.empty_routines
import app.hearth.ui.resources.empty_sort
import app.hearth.ui.resources.group_deadline
import app.hearth.ui.resources.group_due
import app.hearth.ui.resources.group_no_rhythm
import app.hearth.ui.resources.group_on_track
import app.hearth.ui.resources.group_open
import app.hearth.ui.resources.group_urgent
import app.hearth.ui.resources.group_waiting
import app.hearth.ui.resources.nav_tasks
import app.hearth.ui.resources.no_steps
import app.hearth.ui.resources.plan
import app.hearth.ui.resources.routine_due
import app.hearth.ui.resources.routine_soon
import app.hearth.ui.resources.sort_hint
import app.hearth.ui.resources.steps_progress
import app.hearth.ui.resources.tab_admin
import app.hearth.ui.resources.tab_projects
import app.hearth.ui.resources.tab_routines
import app.hearth.ui.resources.tab_sort
import app.hearth.ui.resources.tab_week
import app.hearth.ui.resources.tasks_subtitle
import app.hearth.ui.theme.style
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

enum class TaskTab { ROUTINES, ADMIN, PROJECTS, WEEK, SORT }

/** Callbacks shared by every list on the Tasks screen. */
class TaskCallbacks(
    val complete: (BacklogItem) -> Unit,
    val reopen: (BacklogItem) -> Unit,
    val open: (BacklogItem) -> Unit,
    val add: (String, Section) -> Unit,
    val addStep: (BacklogItem, String) -> Unit,
    /** Asks first (the screen shows a confirm), then deletes. */
    val delete: (BacklogItem) -> Unit,
)

/** Routines & habits · Admin · Projects · Week · To sort: each kind of work in the list that suits it. */
@Composable
fun TasksScreen(
    backlog: List<BacklogItem>,
    steps: List<BacklogItem>,
    today: LocalDate,
    lang: Lang,
    tab: TaskTab,
    onTab: (TaskTab) -> Unit,
    callbacks: TaskCallbacks,
) {
    val names = backlog.associate { it.id to it.text }
    val routines = backlog.filter { it.isRoutine() }
    val admin = backlog.filter { !it.isStep && it.kind == TaskKind.ADMIN }
    val projects = backlog.filter { !it.isStep && it.kind == TaskKind.PROJECT }
    val toSort = backlog.filter { !it.isStep && it.kind == TaskKind.SORT }

    Column(Modifier.fillMaxSize()) {
        Text(stringResource(Res.string.nav_tasks), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(Res.string.tasks_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(18.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
            item {
                ChoiceChip(
                    stringResource(Res.string.tab_routines), tab == TaskTab.ROUTINES, { onTab(TaskTab.ROUTINES) },
                    badge = routines.count { it.routineState(today) == RoutineState.DUE }, icon = HearthIcons.Sun,
                )
            }
            item { ChoiceChip(stringResource(Res.string.tab_admin), tab == TaskTab.ADMIN, { onTab(TaskTab.ADMIN) }, badge = admin.size, icon = HearthIcons.Flag) }
            item { ChoiceChip(stringResource(Res.string.tab_projects), tab == TaskTab.PROJECTS, { onTab(TaskTab.PROJECTS) }, badge = projects.size, icon = HearthIcons.Backlog) }
            item { ChoiceChip(stringResource(Res.string.tab_week), tab == TaskTab.WEEK, { onTab(TaskTab.WEEK) }, icon = HearthIcons.Calendar) }
            if (toSort.isNotEmpty() || tab == TaskTab.SORT) {
                item { ChoiceChip(stringResource(Res.string.tab_sort), tab == TaskTab.SORT, { onTab(TaskTab.SORT) }, badge = toSort.size, icon = HearthIcons.Inbox) }
            }
        }
        Spacer(Modifier.size(14.dp))

        val body = Modifier.fillMaxWidth().weight(1f)
        when (tab) {
            TaskTab.ROUTINES -> AddAndList(stringResource(Res.string.add_routine_hint), { callbacks.add(it, Section.CHORE) }, body) {
                routineList(routines, today, lang, callbacks)
            }
            TaskTab.ADMIN -> AddAndList(stringResource(Res.string.add_admin_hint), { callbacks.add(it, Section.ADMIN) }, body) {
                adminList(admin, today, lang, callbacks)
            }
            TaskTab.PROJECTS -> AddAndList(stringResource(Res.string.add_project_hint), { callbacks.add(it, Section.PROJECT) }, body) {
                projectList(projects, steps, today, lang, callbacks)
            }
            TaskTab.WEEK -> WeekPanel(backlog, today, lang, names, callbacks.complete, callbacks.open, callbacks.delete, body)
            TaskTab.SORT -> Column(body) {
                Text(stringResource(Res.string.sort_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(10.dp))
                ListSurface(Modifier.weight(1f)) {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        if (toSort.isEmpty()) item { EmptyHint(stringResource(Res.string.empty_sort), Modifier.padding(10.dp)) }
                        items(toSort, key = { it.id }) { item -> PlanRow(item, today, lang, callbacks, Modifier.animateItem()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddAndList(hint: String, onAdd: (String) -> Unit, modifier: Modifier, content: LazyListScope.() -> Unit) {
    Column(modifier) {
        AddRow(hint, onAdd)
        Spacer(Modifier.size(12.dp))
        ListSurface(Modifier.weight(1f)) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), content = content)
        }
    }
}

// ---------- routines ----------

/** About time · On track · No rhythm yet. The tick logs "done today"; the routine stays. */
private fun LazyListScope.routineList(routines: List<BacklogItem>, today: LocalDate, lang: Lang, cb: TaskCallbacks) {
    if (routines.isEmpty()) {
        item { EmptyHint0(Res.string.empty_routines) }
        return
    }
    val due = routines.filter { it.intervalDays != null && it.routineState(today).let { s -> s == RoutineState.DUE || s == RoutineState.NEW } }
    val noRhythm = routines.filter { it.intervalDays == null }
    val onTrack = routines - due.toSet() - noRhythm.toSet()
    group(Res.string.group_due, due.sortedBy { it.intervalDays }, today, lang, cb)
    group(Res.string.group_on_track, onTrack.sortedBy { it.daysSinceRatio(today) }.reversed(), today, lang, cb)
    group(Res.string.group_no_rhythm, noRhythm, today, lang, cb)
}

private fun BacklogItem.daysSinceRatio(today: LocalDate): Double {
    val interval = intervalDays ?: return 0.0
    val since = lastDoneDate?.let { (today.toEpochDays() - it.toEpochDays()).toDouble() } ?: return 1.0
    return since / interval
}

// ---------- admin ----------

/** Urgent · With a deadline · Waiting for a reply · Open. */
private fun LazyListScope.adminList(admin: List<BacklogItem>, today: LocalDate, lang: Lang, cb: TaskCallbacks) {
    if (admin.isEmpty()) {
        item { EmptyHint0(Res.string.empty_admin) }
        return
    }
    val urgent = admin.filter { it.urgent }
    val waiting = admin.filter { it.waiting && !it.urgent }
    val deadline = admin.filter { !it.urgent && !it.waiting && it.finishBy && it.plan == Plan.DAY }.sortedBy { it.date }
    val open = admin - urgent.toSet() - waiting.toSet() - deadline.toSet()
    group(Res.string.group_urgent, urgent, today, lang, cb)
    group(Res.string.group_deadline, deadline, today, lang, cb)
    group(Res.string.group_waiting, waiting.sortedBy { it.date }, today, lang, cb)
    group(Res.string.group_open, open.sortedWith(compareBy({ it.plan.ordinal }, { it.date ?: "" })), today, lang, cb)
}

// ---------- projects ----------

private fun LazyListScope.projectList(projects: List<BacklogItem>, steps: List<BacklogItem>, today: LocalDate, lang: Lang, cb: TaskCallbacks) {
    if (projects.isEmpty()) {
        item { EmptyHint0(Res.string.empty_projects) }
        return
    }
    items(projects, key = { it.id }) { project ->
        ProjectBlock(project, steps.filter { it.parentId == project.id }, today, lang, cb, Modifier.animateItem())
    }
}

/** A project: title with its slot, progress, its steps (ticked ones stay, struck through), and an add-step field. */
@Composable
private fun ProjectBlock(project: BacklogItem, steps: List<BacklogItem>, today: LocalDate, lang: Lang, cb: TaskCallbacks, modifier: Modifier) {
    val style = Section.PROJECT.style()
    val done = steps.count { it.done }
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp).background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f), MaterialTheme.shapes.medium).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { cb.open(project) }.padding(start = 6.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(project.text, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (project.urgent) UrgentTag(lang)
                    statusText(project, today, lang)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(
                        if (steps.isEmpty()) stringResource(Res.string.no_steps) else stringResource(Res.string.steps_progress, done, steps.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (steps.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else style.ink,
                    )
                }
            }
            IconButton(onClick = { cb.open(project) }) { Icon(HearthIcons.Calendar, stringResource(Res.string.plan), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        AddRow(stringResource(Res.string.add_step_hint), { cb.addStep(project, it) }, muted = true)
    }
}

// ---------- shared ----------

private fun LazyListScope.group(
    title: org.jetbrains.compose.resources.StringResource,
    items: List<BacklogItem>,
    today: LocalDate,
    lang: Lang,
    cb: TaskCallbacks,
) {
    if (items.isEmpty()) return
    item(key = "h-$title") { GroupTitle(stringResource(title)) }
    items(items, key = { it.id }) { item -> PlanRow(item, today, lang, cb, Modifier.animateItem()) }
}

/** A row with a routine's state pill ("aan de beurt") and a plan button. */
@Composable
private fun PlanRow(item: BacklogItem, today: LocalDate, lang: Lang, cb: TaskCallbacks, modifier: Modifier) {
    BacklogRow(item, today, lang, onDone = { cb.complete(item) }, onOpen = { cb.open(item) }, modifier = modifier, showSection = !item.isRoutine(), onDelete = { cb.delete(item) }) {
        if (item.isRoutine() && item.plan != Plan.DAY) {
            when (item.routineState(today)) {
                RoutineState.DUE -> StatePill(stringResource(Res.string.routine_due), strong = true)
                RoutineState.SOON -> StatePill(stringResource(Res.string.routine_soon), strong = false)
                else -> {}
            }
        }
        IconButton(onClick = { cb.open(item) }) { Icon(HearthIcons.Calendar, stringResource(Res.string.plan), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun StatePill(text: String, strong: Boolean) {
    val s = Section.RHYTHM.style()
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (strong) MaterialTheme.colorScheme.surface else s.ink,
        modifier = Modifier.background(if (strong) s.ink else s.tint, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun EmptyHint0(text: org.jetbrains.compose.resources.StringResource) = EmptyHint(stringResource(text), Modifier.padding(10.dp))

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
