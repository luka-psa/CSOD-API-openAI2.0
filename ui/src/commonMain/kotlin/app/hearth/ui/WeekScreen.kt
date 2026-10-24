package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.Huddle
import app.hearth.core.model.Member
import app.hearth.core.model.TranscriptLine
import app.hearth.core.model.WeekFeeling
import app.hearth.core.model.Win
import app.hearth.ai.route.Place
import app.hearth.ui.resources.earlier_meetings
import app.hearth.ui.resources.feeling_heavy_hint
import app.hearth.ui.resources.last_time
import app.hearth.ui.resources.transcript_last
import app.hearth.ui.resources.transcript_title
import app.hearth.ui.resources.week_summary
import app.hearth.core.model.Lang
import app.hearth.core.model.Meal
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.TaskKind
import app.hearth.core.model.dayLabel
import app.hearth.core.model.deadlinesThisWeek
import app.hearth.core.model.dueRoutines
import app.hearth.core.model.oudjes
import app.hearth.core.model.plusDays
import app.hearth.core.model.weekdayName
import app.hearth.core.tools.backlog.When
import app.hearth.core.tools.board.FrictionOutcome
import app.hearth.core.tools.week.WeekAgenda
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.delete_confirm
import app.hearth.ui.resources.friction_change_title
import app.hearth.ui.resources.friction_let_go
import app.hearth.ui.resources.friction_rule_title
import app.hearth.ui.resources.friction_to_change
import app.hearth.ui.resources.friction_to_rule
import app.hearth.ui.resources.goal_step_add
import app.hearth.ui.resources.goal_step_title
import app.hearth.ui.resources.goal_step_to_week
import app.hearth.ui.resources.friction_text_hint
import app.hearth.ui.resources.mark_done
import app.hearth.ui.resources.meal_add
import app.hearth.ui.resources.meal_dish_hint
import app.hearth.ui.resources.meal_earlier
import app.hearth.ui.resources.meal_ingredients_hint
import app.hearth.ui.resources.meal_remove_body
import app.hearth.ui.resources.meal_title
import app.hearth.ui.resources.nav_week
import app.hearth.ui.resources.open_stock
import app.hearth.ui.resources.oudje_since
import app.hearth.ui.resources.oudjes_more
import app.hearth.ui.resources.plan_later
import app.hearth.ui.resources.plan_weekend
import app.hearth.ui.resources.save
import app.hearth.ui.resources.shopping_add_hint
import app.hearth.ui.resources.shopping_summary_empty
import app.hearth.ui.resources.step_board
import app.hearth.ui.resources.step_board_hint
import app.hearth.ui.resources.step_board_empty
import app.hearth.ui.resources.step_look_back
import app.hearth.ui.resources.step_look_back_calm
import app.hearth.ui.resources.step_look_back_count
import org.jetbrains.compose.resources.pluralStringResource
import app.hearth.ui.resources.step_look_back_since
import app.hearth.ui.resources.step_look_back_week
import app.hearth.ui.resources.step_meals
import app.hearth.ui.resources.step_meals_hint
import app.hearth.ui.resources.step_of
import app.hearth.ui.resources.step_oudjes
import app.hearth.ui.resources.step_oudjes_empty
import app.hearth.ui.resources.step_oudjes_hint
import app.hearth.ui.resources.step_shopping
import app.hearth.ui.resources.step_shopping_hint
import app.hearth.ui.resources.step_week
import app.hearth.ui.resources.step_week_calm
import app.hearth.ui.resources.step_week_deadlines
import app.hearth.ui.resources.step_week_projects
import app.hearth.ui.resources.step_week_routines
import app.hearth.ui.resources.this_week
import app.hearth.ui.resources.week_back
import app.hearth.ui.resources.week_done
import app.hearth.ui.resources.week_done_recap
import app.hearth.ui.resources.week_finish
import app.hearth.ui.resources.week_last_held
import app.hearth.ui.resources.week_next
import app.hearth.ui.resources.week_subtitle
import app.hearth.ui.resources.board_won
import app.hearth.ui.theme.style
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Everything the Weekoverleg can ask for; each is one tool call in the ViewModel. */
class WeekCallbacks(
    val loadAgenda: () -> Unit,
    val planMeal: (LocalDate, String, String) -> Unit,
    val removeMeal: (Meal) -> Unit,
    val addShopping: (String) -> Unit,
    val goShopping: () -> Unit,
    val plan: (BacklogItem, When, LocalDate?) -> Unit,
    val complete: (BacklogItem) -> Unit,
    val resolveFriction: (BoardItem, FrictionOutcome, String?) -> Unit,
    val setGoalStep: (BoardItem, String, Boolean) -> Unit,
    val finish: () -> Unit,
    val addWin: (String) -> Unit,
    val removeWin: (Win) -> Unit,
    val setFeeling: (Int, Member?) -> Unit,
    val heard: (String) -> Unit,
    val clearTranscript: () -> Unit,
    val openVoorstel: () -> Unit,
    val closeVoorstel: () -> Unit,
    val moveVoorstel: (Int, Place) -> Unit,
    val toggleVoorstel: (Int) -> Unit,
    val addVoorstel: () -> Unit,
)

/** The Weekoverleg's own notes: the look back, Luister mee and the summaries. */
class MeetingState(
    val wins: List<Win>,
    val feelings: List<WeekFeeling>,
    val members: List<Member>,
    val transcript: List<TranscriptLine>,
    val lastTranscript: List<TranscriptLine>,
    val huddles: List<Huddle>,
    val voorstel: List<VoorstelRow>?,
)

enum class WeekStep(val title: StringResource) {
    LOOK_BACK(Res.string.step_look_back),
    MEALS(Res.string.step_meals),
    SHOPPING(Res.string.step_shopping),
    THIS_WEEK(Res.string.step_week),
    OUDJES(Res.string.step_oudjes),
    BOARD(Res.string.step_board),
}

/**
 * The weekly meeting, about 15 minutes together: look back (celebrate) → meals → shopping → this week →
 * oudjes (pick a few of the longest-open) → frictions and goals. "Klaar" records it.
 */
@Composable
fun WeekScreen(
    agenda: WeekAgenda?,
    lastHuddle: Huddle?,
    backlog: List<BacklogItem>,
    steps: List<BacklogItem>,
    board: List<BoardItem>,
    meals: List<Meal>,
    dishes: List<Meal>,
    shopping: List<ShopSection>,
    today: LocalDate,
    lang: Lang,
    meeting: MeetingState,
    cb: WeekCallbacks,
) {
    var step by rememberSaveable { mutableStateOf(WeekStep.LOOK_BACK) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var showing by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { cb.loadAgenda() }
    val transcriber = rememberTranscriber(
        language = if (lang == Lang.NL) "nl-BE" else "en-GB",
        onLine = cb.heard,
        onPartial = { partial = it },
        onActive = { listening = it; if (!it) partial = "" },
    )

    Column(Modifier.fillMaxSize()) {
        Text(stringResource(Res.string.nav_week), style = MaterialTheme.typography.displaySmall)
        val held = lastHuddle?.date?.let { stringResource(Res.string.week_last_held, dayLabel(it, today, lang).removePrefix("sinds ").removePrefix("since ")) }
        Text(
            listOfNotNull(stringResource(Res.string.week_subtitle), held).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
            itemsIndexed(WeekStep.entries) { i, s ->
                ChoiceChip("${i + 1}  ${stringResource(s.title)}", s == step, { step = s; finished = false })
            }
        }
        Spacer(Modifier.size(10.dp))
        ListenBar(
            available = transcriber.available,
            listening = listening,
            partial = partial,
            lines = meeting.transcript,
            onToggle = { if (listening) transcriber.stop() else transcriber.start() },
            onShow = { showing = true },
            onSort = cb.openVoorstel,
        )
        Spacer(Modifier.size(10.dp))

        ListSurface(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                if (finished) {
                    Finished(agenda, meals, meeting.huddles)
                } else {
                    Text(stringResource(Res.string.step_of, step.ordinal + 1, WeekStep.entries.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(step.title), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.size(14.dp))
                    when (step) {
                        WeekStep.LOOK_BACK -> {
                            LastTime(lastHuddle, meeting.lastTranscript, onShow = { showing = false })
                            LookBack(agenda, lastHuddle, today, lang)
                            Spacer(Modifier.size(18.dp))
                            WinsSection(meeting.wins, meeting.members, cb.addWin, cb.removeWin)
                            Spacer(Modifier.size(18.dp))
                            FeelingMeter(meeting.members, meeting.feelings, cb.setFeeling)
                        }
                        WeekStep.MEALS -> Meals(meals, dishes, today, lang, cb)
                        WeekStep.SHOPPING -> ShoppingStep(shopping, cb)
                        WeekStep.THIS_WEEK -> ThisWeek(backlog, steps, today, lang, cb)
                        WeekStep.OUDJES -> Oudjes(backlog, today, lang, cb)
                        WeekStep.BOARD -> BoardStep(board, heavy = meeting.feelings.any { it.value >= 4 }, cb)
                    }
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step != WeekStep.LOOK_BACK && !finished) {
                OutlinedButton(onClick = { step = WeekStep.entries[step.ordinal - 1] }) { Text(stringResource(Res.string.week_back)) }
            }
            Spacer(Modifier.weight(1f))
            when {
                finished -> {}
                step == WeekStep.BOARD -> Button(onClick = { if (listening) transcriber.stop(); cb.finish(); finished = true }) {
                    Icon(HearthIcons.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.week_finish))
                }
                else -> Button(onClick = { step = WeekStep.entries[step.ordinal + 1] }) {
                    Text(stringResource(Res.string.week_next))
                    Spacer(Modifier.width(6.dp))
                    Icon(HearthIcons.ChevronRight, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    when (showing) {
        true -> TranscriptSheet(stringResource(Res.string.transcript_title), meeting.transcript, onClear = cb.clearTranscript) { showing = null }
        false -> TranscriptSheet(stringResource(Res.string.transcript_last), meeting.lastTranscript, onClear = null) { showing = null }
        null -> {}
    }
    meeting.voorstel?.let { rows ->
        VoorstelSheet(rows, today, lang, cb.moveVoorstel, cb.toggleVoorstel, cb.addVoorstel, cb.closeVoorstel)
    }
}

/** "Vorige keer": the last meeting's summary, and its conversation while it's still kept. */
@Composable
private fun LastTime(last: Huddle?, transcript: List<TranscriptLine>, onShow: () -> Unit) {
    val summary = last?.summary?.takeIf { it.isNotBlank() } ?: return
    val s = Section.NOTE.style()
    Column(Modifier.fillMaxWidth().background(s.tint, MaterialTheme.shapes.medium).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.last_time), style = MaterialTheme.typography.labelLarge, color = s.ink, modifier = Modifier.weight(1f))
            if (transcript.isNotEmpty()) TextButton(onClick = onShow) { Text(stringResource(Res.string.transcript_last)) }
        }
        SummaryText(summary)
    }
    Spacer(Modifier.size(18.dp))
}

// ---------- 1. look back ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookBack(agenda: WeekAgenda?, last: Huddle?, today: LocalDate, lang: Lang) {
    if (agenda == null) return
    val done = agenda.done
    val since = last?.date?.let { stringResource(Res.string.step_look_back_since, weekdayName(it.dayOfWeek, lang)) } ?: stringResource(Res.string.step_look_back_week)
    if (done.isEmpty() && agenda.boardWins.isEmpty()) {
        Hint(stringResource(Res.string.step_look_back_calm))
        return
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${done.size}", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(12.dp))
        Text(pluralStringResource(Res.plurals.step_look_back_count, done.size, since), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
    }
    Spacer(Modifier.size(14.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        done.distinctBy { it.id }.take(30).forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(HearthIcons.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(item.text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (agenda.boardWins.isNotEmpty()) {
        Spacer(Modifier.size(18.dp))
        GroupTitle(stringResource(Res.string.board_won))
        agenda.boardWins.forEach { win ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(if (win.kind == BoardKind.GOAL) HearthIcons.Star else HearthIcons.Bolt, null, tint = Section.RHYTHM.style().ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(win.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---------- 2. meals ----------

@Composable
private fun Meals(meals: List<Meal>, dishes: List<Meal>, today: LocalDate, lang: Lang, cb: WeekCallbacks) {
    var editing by remember { mutableStateOf<LocalDate?>(null) }
    var removing by remember { mutableStateOf<Meal?>(null) }
    Text(stringResource(Res.string.step_meals_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.size(10.dp))
    (0 until 7).map { today.plusDays(it) }.forEach { day ->
        val meal = meals.firstOrNull { it.date == day.toString() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { editing = day }.heightIn(min = 60.dp).padding(horizontal = 6.dp),
        ) {
            Text(
                dayName(day, today, lang),
                style = MaterialTheme.typography.labelLarge,
                color = if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(110.dp),
            )
            if (meal == null) {
                Icon(HearthIcons.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.meal_add), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(meal.dish, style = MaterialTheme.typography.bodyLarge)
                    if (meal.ingredients.isNotEmpty()) {
                        Text(meal.ingredients.joinToString(", "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                DeleteButton({ removing = meal })
            }
        }
    }
    editing?.let { day ->
        MealDialog(day, today, lang, meals.firstOrNull { it.date == day.toString() }, dishes, onSave = { dish, ingredients -> cb.planMeal(day, dish, ingredients) }) { editing = null }
    }
    removing?.let { meal ->
        ConfirmDialog(stringResource(Res.string.delete_confirm, meal.dish), stringResource(Res.string.meal_remove_body), onYes = { cb.removeMeal(meal) }) { removing = null }
    }
}

private fun dayName(day: LocalDate, today: LocalDate, lang: Lang): String =
    if (day.toEpochDays() - today.toEpochDays() < 2) dayLabel(day, today, lang).replaceFirstChar { it.uppercase() }
    else "${weekdayName(day.dayOfWeek, lang).replaceFirstChar { it.uppercase() }} ${day.day}"

/** Dish + what to buy; dishes from before fill both in one tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealDialog(day: LocalDate, today: LocalDate, lang: Lang, current: Meal?, dishes: List<Meal>, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var dish by remember { mutableStateOf(current?.dish ?: "") }
    var ingredients by remember { mutableStateOf(current?.ingredients?.joinToString(", ") ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.meal_title, dayName(day, today, lang))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(dish, { dish = it }, placeholder = { Text(stringResource(Res.string.meal_dish_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ingredients, { ingredients = it }, placeholder = { Text(stringResource(Res.string.meal_ingredients_hint)) }, modifier = Modifier.fillMaxWidth())
                if (dishes.isNotEmpty()) {
                    Text(stringResource(Res.string.meal_earlier), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dishes.take(12).forEach { d ->
                            ChoiceChip(d.dish, dish.equals(d.dish, ignoreCase = true), { dish = d.dish; ingredients = d.ingredients.joinToString(", ") })
                        }
                    }
                }
            }
        },
        confirmButton = { FilledTonalButton(onClick = { onSave(dish.trim(), ingredients.trim()); onDismiss() }, enabled = dish.isNotBlank()) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

// ---------- 3. shopping ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShoppingStep(shopping: List<ShopSection>, cb: WeekCallbacks) {
    val open = shopping.flatMap { s -> s.items.filterNot { it.bought } }
    Text(stringResource(Res.string.step_shopping_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.size(12.dp))
    if (open.isEmpty()) EmptyHint(stringResource(Res.string.shopping_summary_empty))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        open.forEach { item ->
            Text(item.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
    Spacer(Modifier.size(14.dp))
    AddRow(stringResource(Res.string.shopping_add_hint), cb.addShopping, muted = true)
    Spacer(Modifier.size(8.dp))
    CardAction(stringResource(Res.string.open_stock), cb.goShopping)
}

// ---------- 4. this week ----------

@Composable
private fun ThisWeek(backlog: List<BacklogItem>, steps: List<BacklogItem>, today: LocalDate, lang: Lang, cb: WeekCallbacks) {
    val names = backlog.associate { it.id to it.text }
    val deadlines = deadlinesThisWeek(backlog, today)
    val routines = dueRoutines(backlog, today)
    val saturday = today.plusDays((DayOfWeek.SATURDAY.ordinal - today.dayOfWeek.ordinal + 7) % 7)
    // One step per project, for the weekend: the first open one that has no day yet.
    val projectSteps = backlog.filter { it.kind == TaskKind.PROJECT && !it.isStep }.mapNotNull { project ->
        steps.firstOrNull { it.parentId == project.id && !it.done && it.plan != Plan.DAY } ?: project.takeIf { it.plan != Plan.DAY && steps.none { s -> s.parentId == project.id && !s.done } }
    }
    if (deadlines.isEmpty() && routines.isEmpty() && projectSteps.isEmpty()) {
        Hint(stringResource(Res.string.step_week_calm))
        return
    }
    if (deadlines.isNotEmpty()) {
        GroupTitle(stringResource(Res.string.step_week_deadlines))
        deadlines.forEach { BacklogRow(it, today, lang, onDone = { cb.complete(it) }, onOpen = {}) }
    }
    if (routines.isNotEmpty()) {
        GroupTitle(stringResource(Res.string.step_week_routines))
        routines.forEach { BacklogRow(it, today, lang, onDone = { cb.complete(it) }, onOpen = {}, showSection = false) }
    }
    if (projectSteps.isNotEmpty()) {
        GroupTitle(stringResource(Res.string.step_week_projects))
        projectSteps.forEach { item ->
            BacklogRow(item, today, lang, onDone = { cb.complete(item) }, onOpen = {}, projectName = item.parentId?.let(names::get)) {
                TextButton(onClick = { cb.plan(item, When.DAY, saturday) }) { Text(stringResource(Res.string.plan_weekend)) }
            }
        }
    }
}

// ---------- 5. oudjes ----------

@Composable
private fun Oudjes(backlog: List<BacklogItem>, today: LocalDate, lang: Lang, cb: WeekCallbacks) {
    var skip by remember { mutableIntStateOf(0) }
    val shown = oudjes(backlog, count = 3, skip = skip)
    Text(stringResource(Res.string.step_oudjes_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.size(12.dp))
    if (shown.isEmpty()) {
        Hint(stringResource(Res.string.step_oudjes_empty))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        shown.forEach { item ->
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.medium).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTag(item, lang)
                    val since = dateOf(item.createdAt)
                    Text(stringResource(Res.string.oudje_since, "${since.day}/${since.month.ordinal + 1}"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(6.dp))
                Text(item.text, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { cb.plan(item, When.NEXT, null) }) { Text(stringResource(Res.string.this_week)) }
                    OutlinedButton(onClick = { cb.plan(item, When.LATER, null); skip = skip }) { Text(stringResource(Res.string.plan_later)) }
                    TextButton(onClick = { cb.complete(item) }) {
                        Icon(HearthIcons.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.mark_done))
                    }
                }
            }
        }
    }
    Spacer(Modifier.size(12.dp))
    TextButton(onClick = { skip += 3 }) { Text(stringResource(Res.string.oudjes_more)) }
}

// ---------- 6. Werkbord ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardStep(board: List<BoardItem>, heavy: Boolean, cb: WeekCallbacks) {
    var asking by remember { mutableStateOf<Pair<BoardItem, FrictionOutcome?>?>(null) }
    val frictions = board.filter { it.isWorkingPoint && it.isOpen }
    val goals = board.filter { it.kind == BoardKind.GOAL && it.isOpen && it.nextStep == null }
    Text(stringResource(Res.string.step_board_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (heavy && frictions.isNotEmpty()) {
        Spacer(Modifier.size(10.dp))
        Hint(stringResource(Res.string.feeling_heavy_hint))
    }
    Spacer(Modifier.size(12.dp))
    if (frictions.isEmpty() && goals.isEmpty()) Hint(stringResource(Res.string.step_board_empty))
    frictions.forEach { f ->
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val idea = f.kind == BoardKind.IDEA
                Icon(if (idea) HearthIcons.Idea else HearthIcons.Bolt, null, tint = (if (idea) Section.IDEA else Section.FRICTION).style().ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(f.text, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.size(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(Res.string.friction_to_rule), false, { asking = f to FrictionOutcome.RULE }, icon = HearthIcons.Rule)
                ChoiceChip(stringResource(Res.string.friction_to_change), false, { asking = f to FrictionOutcome.CHANGE }, icon = HearthIcons.Next)
                ChoiceChip(stringResource(Res.string.friction_let_go), false, { cb.resolveFriction(f, FrictionOutcome.LET_GO, null) })
            }
        }
    }
    goals.forEach { g ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Icon(HearthIcons.Star, null, tint = Section.GOAL.style().ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(g.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { asking = g to null }) { Text(stringResource(Res.string.goal_step_add)) }
        }
    }
    asking?.let { (item, outcome) ->
        when (outcome) {
            FrictionOutcome.RULE -> TextDialog(stringResource(Res.string.friction_rule_title), item.text, onDone = { t, _ -> cb.resolveFriction(item, FrictionOutcome.RULE, t.ifBlank { null }) }) { asking = null }
            FrictionOutcome.CHANGE -> TextDialog(stringResource(Res.string.friction_change_title), item.text, onDone = { t, _ -> cb.resolveFriction(item, FrictionOutcome.CHANGE, t.ifBlank { null }) }) { asking = null }
            else -> TextDialog(stringResource(Res.string.goal_step_title), stringResource(Res.string.friction_text_hint), toggle = stringResource(Res.string.goal_step_to_week), onDone = { t, toWeek -> cb.setGoalStep(item, t, toWeek) }) { asking = null }
        }
    }
}

// ---------- done ----------

@Composable
private fun Finished(agenda: WeekAgenda?, meals: List<Meal>, huddles: List<Huddle>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)) {
        Icon(HearthIcons.Flame, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.size(16.dp))
        Text(stringResource(Res.string.week_done), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(Res.string.week_done_recap, agenda?.done?.size ?: 0, meals.size),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (meals.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            Text(meals.joinToString("  ·  ") { it.dish }, style = MaterialTheme.typography.bodyLarge, color = Section.PROTECTED_TIME.style().ink)
        }
    }
    huddles.firstOrNull()?.summary?.takeIf { it.isNotBlank() }?.let {
        GroupTitle(stringResource(Res.string.week_summary))
        Column(Modifier.padding(horizontal = 10.dp)) { SummaryText(it) }
    }
    val earlier = huddles.drop(1).filter { it.summary.isNotBlank() }.take(3)
    if (earlier.isNotEmpty()) {
        Spacer(Modifier.size(12.dp))
        GroupTitle(stringResource(Res.string.earlier_meetings))
        earlier.forEach { h ->
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) { SummaryText(h.summary) }
        }
    }
}

@Composable
private fun Hint(text: String) {
    val s = Section.PROTECTED_TIME.style()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(s.tint, MaterialTheme.shapes.medium).padding(16.dp)) {
        Icon(HearthIcons.Sun, null, tint = s.ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = s.ink)
    }
}
