package app.hearth.ui

import app.hearth.ui.resources.group_lists
import app.hearth.core.model.isRoutineStep
import app.hearth.ui.resources.small_step_hint
import app.hearth.ui.resources.small_step_title
import app.hearth.ui.resources.small_step_add
import app.hearth.ui.resources.small_step_line
import app.hearth.ui.resources.anchor_none
import app.hearth.ui.resources.rules_add_hint
import app.hearth.ui.resources.rules_empty
import app.hearth.ui.resources.rhythm_empty
import app.hearth.ui.resources.rhythm_add_hint
import app.hearth.ui.resources.home_add_hint
import app.hearth.ui.resources.home_empty
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import app.hearth.core.model.Anchor
import app.hearth.ui.resources.day_free
import app.hearth.ui.resources.projects_empty
import app.hearth.ui.resources.project_add_hint
import app.hearth.ui.resources.admin_add_hint
import app.hearth.ui.resources.admin_empty
import app.hearth.ui.resources.free_add_hint
import app.hearth.ui.resources.free_empty
import app.hearth.ui.resources.close
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import app.hearth.core.model.isRoutine
import app.hearth.core.model.scoreNow
import app.hearth.core.model.scoreWord
import app.hearth.core.model.topic
import app.hearth.core.model.title
import app.hearth.core.model.glance
import app.hearth.core.model.WeekLook
import app.hearth.core.model.Topic
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
import kotlinx.coroutines.delay
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
import app.hearth.core.model.Ease
import app.hearth.core.model.isHabit
import app.hearth.core.model.HabitReview
import app.hearth.core.model.HabitTip
import app.hearth.core.model.WeekRating
import app.hearth.core.model.RuleCheck
import app.hearth.core.model.Win
import app.hearth.ai.route.Place
import app.hearth.ui.resources.earlier_meetings
import app.hearth.ui.resources.feeling_heavy_hint
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
import app.hearth.core.model.plannedOn
import app.hearth.core.model.plannedToday
import app.hearth.ui.resources.week_ahead
import app.hearth.ui.resources.week_ahead_empty
import app.hearth.ui.resources.week_open_tasks
import app.hearth.ui.resources.week_recipes
import app.hearth.core.model.plusDays
import app.hearth.core.model.weekdayName
import app.hearth.core.tools.backlog.When
import app.hearth.core.tools.board.FrictionOutcome
import app.hearth.core.tools.week.WeekAgenda
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.rules_review_note
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.delete_confirm
import app.hearth.ui.resources.friction_let_go
import app.hearth.ui.resources.friction_rule_title
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
import app.hearth.ui.resources.plan_later
import app.hearth.ui.resources.plan_weekend
import app.hearth.ui.resources.save
import app.hearth.ui.resources.shopping_add_hint
import app.hearth.ui.resources.shopping_summary_empty
import app.hearth.ui.resources.step_board_hint
import app.hearth.ui.resources.step_board_empty
import app.hearth.ui.resources.step_look_back
import app.hearth.ui.resources.step_look_back_count
import org.jetbrains.compose.resources.pluralStringResource
import app.hearth.ui.resources.step_look_back_since
import app.hearth.ui.resources.step_look_back_week
import app.hearth.ui.resources.step_shopping_hint
import app.hearth.ui.resources.step_week
import app.hearth.ui.resources.week_done
import app.hearth.ui.resources.week_done_recap
import app.hearth.ui.resources.week_finish
import app.hearth.ui.resources.week_last_held
import app.hearth.ui.resources.week_subtitle
import app.hearth.ui.resources.goals_meeting_hint
import app.hearth.ui.resources.goals_add_hint
import app.hearth.ui.resources.goals_empty
import app.hearth.ui.theme.style
import app.hearth.ui.theme.AccentStyle
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
    val resolveFriction: (BoardItem, FrictionWrite) -> Unit,
    val markFriction: (BoardItem, app.hearth.core.model.FrictionDirection?) -> Unit,
    val setGoalStep: (BoardItem, GoalPlan) -> Unit,
    val addGoal: (String) -> Unit,
    val achieveGoal: (BoardItem) -> Unit,
    val finish: () -> Unit,
    val goTasks: () -> Unit,
    val goMenu: () -> Unit,
    /** A win, under the topic it was named at (null: in general). */
    val addWin: (String, Topic?) -> Unit,
    val removeWin: (Win) -> Unit,
    /** How the week went, agreed together: (score 0–5 or null for no agreement, Hearth's suggestion). */
    val rateWeek: (Int?, Int) -> Unit,
    val setWeekOn: (Boolean) -> Unit,
    val checkRule: (BoardItem, Int) -> Unit,
    val lessOften: (BacklogItem) -> Unit,
    val makeEasier: (BacklogItem) -> Unit,
    val rateHabit: (BacklogItem, Int) -> Unit,
    val smallStep: (BacklogItem, String) -> Unit,
    val heard: (String) -> Unit,
    val addTo: (String, Section) -> Unit,
    val addRule: (String) -> Unit,
    val setAnchor: (BacklogItem, Anchor?) -> Unit,
    val prepareFinish: () -> Unit,
    val closeAgree: () -> Unit,
    val clearTranscript: () -> Unit,
    val openVoorstel: () -> Unit,
    val closeVoorstel: () -> Unit,
    val moveVoorstel: (Int, Place) -> Unit,
    val toggleVoorstel: (Int) -> Unit,
    val addVoorstel: () -> Unit,
    /** A suggestion of Luister mee changed in its sheet; with true, added right away. */
    val changeSuggestion: (HeardThought, Int, SuggestionChange, Boolean) -> Unit = { _, _, _, _ -> },
    val editVoorstel: (Int, SuggestionChange) -> Unit = { _, _ -> },
    /** A coach tip that needs no words. */
    val habitTip: (BacklogItem, HabitTip) -> Unit = { _, _ -> },
    /** Something nice with a habit. */
    val pairing: (BacklogItem, String) -> Unit = { _, _ -> },
    /** Opens an item's sheet (a routine's moment, days and steps). */
    val openItem: (BacklogItem) -> Unit = {},
    /** A house rule helped along the four laws. */
    val ruleHelp: (BoardItem, RuleHelp) -> Unit = { _, _ -> },
    /** A past meeting's summary, put right afterwards. */
    val editHuddle: (Huddle, String) -> Unit = { _, _ -> },
    /** The conversation and what Hearth made of it, as a file. */
    val exportConversation: (List<TranscriptLine>) -> Unit = {},
)

/** The Weekoverleg's own notes: the look back, Luister mee and the summaries. */
class MeetingState(
    val wins: List<Win>,
    val week: WeekRating?,
    val weekOn: Boolean,
    val ruleChecks: List<RuleCheck>,
    val members: List<Member>,
    val transcript: List<TranscriptLine>,
    val lastTranscript: List<TranscriptLine>,
    val huddles: List<Huddle>,
    val voorstel: List<VoorstelRow>?,
    /** What Luister mee heard lately as thoughts, newest first, with what Hearth concludes. */
    val heard: List<HeardThought> = emptyList(),
    /** The summary to agree on before Klaar, while it's open. */
    val agree: List<String>? = null,
    /** How each house rule goes, by rule id (this week's score or the last one). */
    val ruleScores: Map<String, Int> = emptyMap(),
    /** Each habit's score in this meeting, by habit id (not rated yet: missing). */
    val habitChecks: Map<String, Int> = emptyMap(),
    /** The habit coach per habit (the four laws), by habit id. */
    val habitReviews: Map<String, HabitReview> = emptyMap(),
    /** Each house rule's score per meeting, oldest first, by rule id. */
    val ruleHistory: Map<String, List<Int>> = emptyMap(),
    /** The coach per house rule (the four laws), by rule id. */
    val ruleReviews: Map<String, HabitReview> = emptyMap(),
    /** Every thought of this meeting, newest first: what the summary beside Luister mee groups by topic. */
    val allHeard: List<HeardThought> = emptyList(),
)

/** How long Hearth waits after the last words before it concludes a thought. */
private const val SETTLE_MS = 4_000L

/** Luister mee lives above the screens, so it keeps listening while the family looks at a list. */
class ListenControl(val available: Boolean, val listening: Boolean, val partial: String, val status: ListenStatus? = null, val fix: () -> Unit = {}, val toggle: () -> Unit)

/**
 * The weekly meeting, about 15 minutes together. Luister mee sits on top and sorts what is said into the
 * household's topics, which are all visible at once as calm tiles (tap one to open it). Below: the look
 * back and this week, day by day. Klaar first shows the summary to agree on.
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
    wide: Boolean,
    meeting: MeetingState,
    listen: ListenControl,
    cb: WeekCallbacks,
) {
    var finished by rememberSaveable { mutableStateOf(false) }
    var showing by remember { mutableStateOf<Boolean?>(null) }
    var open by rememberSaveable { mutableStateOf<Topic?>(null) }
    LaunchedEffect(Unit) { cb.loadAgenda() }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.nav_week), style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, maxLines = 1)
                val held = lastHuddle?.date?.let { stringResource(Res.string.week_last_held, dayLabel(it, today, lang).removePrefix("sinds ").removePrefix("since ")) }
                Text(
                    listOfNotNull(stringResource(Res.string.week_subtitle), held).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!finished) {
                Button(onClick = cb.prepareFinish) {
                    Icon(HearthIcons.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.week_finish))
                }
            }
        }
        Spacer(Modifier.size(12.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (finished) {
                ListSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp)) { Finished(agenda, meals, meeting.huddles) }
                }
                return@Column
            }
            val gap = if (wide) 20.dp else 16.dp
            // A thought is only concluded once it has settled: people pause mid-thought, and the next words may go on with it.
            var settled by remember { mutableStateOf(true) }
            LaunchedEffect(meeting.heard.firstOrNull()?.text, listen.partial, listen.listening) {
                settled = false
                if (listen.listening) delay(SETTLE_MS)
                settled = true
            }
            val lit = meeting.heard.drop(if (settled) 0 else 1).firstOrNull()?.proposals?.mapNotNull { it.topic }?.toSet() ?: emptySet()
            ListenStage(
                available = listen.available,
                status = listen.status,
                onFix = listen.fix,
                listening = listen.listening,
                partial = listen.partial,
                heard = meeting.heard,
                all = meeting.allHeard,
                wide = wide,
                settled = settled,
                hasLines = meeting.transcript.isNotEmpty(),
                lit = lit,
                today = today,
                lang = lang,
                onToggle = listen.toggle,
                onShow = { showing = true },
                onSort = cb.openVoorstel,
                onSuggestion = cb.changeSuggestion,
            )
            Spacer(Modifier.size(gap))

            // At a glance: every topic, a few words each. The one just heard about glows.
            val look = WeekLook(backlog, board, meals.count { m -> runCatching { LocalDate.parse(m.date) }.getOrNull()?.let { it >= today && it < today.plusDays(7) } ?: false }, shopping.sumOf { s -> s.items.count { !it.bought } }, today, meeting.ruleScores)
            TopicGrid(Topic.entries.map { it to it.glance(look, lang) }, open, lit, lang) { open = if (open == it) null else it }
            open?.let { topic ->
                Spacer(Modifier.size(gap))
                TopicCard(topic, lang, Modifier.fillMaxWidth(), action = {
                    IconButton(onClick = { open = null }) { Icon(HearthIcons.Close, stringResource(Res.string.close), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }) {
                    TopicDetail(topic, agenda, backlog, steps, board, meals, dishes, shopping, today, lang, meeting, cb)
                }
            }
            Spacer(Modifier.size(gap))

            val lookBack = @Composable { m: Modifier ->
                HearthCard(stringResource(Res.string.step_look_back), HearthIcons.Heart, m) {
                    val doneCount = agenda?.done?.distinctBy { it.id }?.size ?: 0
                    val since = lastHuddle?.date?.let { stringResource(Res.string.step_look_back_since, weekdayName(it.dayOfWeek, lang)) } ?: stringResource(Res.string.step_look_back_week)
                    Terugblik(
                        TerugblikState(
                            doneCount = doneCount,
                            since = pluralStringResource(Res.plurals.step_look_back_count, doneCount, since),
                            huddles = meeting.huddles,
                            hasLastTranscript = meeting.lastTranscript.isNotEmpty(),
                            wins = meeting.wins,
                            members = meeting.members,
                            perTopic = lookBackPerTopic(agenda, backlog, board, meals, lastHuddle, meeting, today, lang),
                            routineChecks = meeting.habitChecks,
                            routineReviews = meeting.habitReviews,
                            ruleChecks = meeting.ruleChecks.associate { it.ruleId to it.score },
                            ruleReviews = meeting.ruleReviews,
                            week = WeekWeather(meeting.weekOn, meeting.week, agenda?.suggestedWeek ?: 4, cb.rateWeek, cb.setWeekOn),
                        ),
                        backlog, board, today, lang,
                        TerugblikCallbacks(
                            addWin = cb.addWin,
                            removeWin = cb.removeWin,
                            rateRoutine = cb.rateHabit,
                            rateRule = cb.checkRule,
                            routineTip = cb.habitTip,
                            smallStep = cb.smallStep,
                            pairing = cb.pairing,
                            ruleHelp = cb.ruleHelp,
                            openItem = cb.openItem,
                            editHuddle = cb.editHuddle,
                            showLastTranscript = { showing = false },
                        ),
                    )
                }
            }
            val thisWeek = @Composable { m: Modifier ->
                Column(m, verticalArrangement = Arrangement.spacedBy(gap)) {
                    HearthCard(stringResource(Res.string.step_week), HearthIcons.Calendar, Modifier.fillMaxWidth(), action = { CardAction(stringResource(Res.string.week_open_tasks), cb.goTasks, compact = true) }) {
                        WeekStrip(backlog, meals, today, lang)
                    }
                    HearthCard(stringResource(Res.string.week_ahead), HearthIcons.Later, Modifier.fillMaxWidth()) { Ahead(backlog, today, lang) }
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    lookBack(Modifier.weight(1f))
                    thisWeek(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    lookBack(Modifier.fillMaxWidth())
                    thisWeek(Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
    when (showing) {
        true -> TranscriptSheet(stringResource(Res.string.transcript_title), meeting.transcript, onClear = cb.clearTranscript, onDismiss = { showing = null }, onExport = { cb.exportConversation(meeting.transcript) })
        false -> TranscriptSheet(stringResource(Res.string.transcript_last), meeting.lastTranscript, onClear = null, onDismiss = { showing = null }, onExport = { cb.exportConversation(meeting.lastTranscript) })
        null -> {}
    }
    meeting.voorstel?.let { rows ->
        VoorstelSheet(rows, today, lang, cb.moveVoorstel, cb.toggleVoorstel, cb.editVoorstel, cb.addVoorstel, cb.closeVoorstel)
    }
    meeting.agree?.let { lines ->
        AgreeSheet(
            lines,
            unsorted = meeting.transcript.any { !it.sorted },
            onSort = { cb.closeAgree(); cb.openVoorstel() },
            onAgree = { if (listen.listening) listen.toggle(); cb.finish(); finished = true },
            onDismiss = cb.closeAgree,
        )
    }
}

/** What opens under a tile: the topic's own list, with the few actions the meeting needs. */
@Composable
private fun TopicDetail(
    topic: Topic,
    agenda: WeekAgenda?,
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
    val mine = backlog.filter { !it.done && it.topic == topic && !it.isStep }.sortedWith(compareBy({ it.date ?: "9999" }, { it.createdAt }))
    when (topic) {
        Topic.FREE_TIME -> {
            if (mine.isEmpty()) Hint(stringResource(Res.string.free_empty))
            mine.forEach { BacklogRow(it, today, lang, onDone = { cb.complete(it) }, onOpen = {}, showSection = false) }
            Spacer(Modifier.size(10.dp))
            AddRow(stringResource(Res.string.free_add_hint), { cb.addTo(it, Section.PROTECTED_TIME) }, muted = true)
        }
        Topic.GOALS -> GoalsStep(board, backlog, today, lang, cb)
        Topic.ADMIN -> {
            if (mine.isEmpty()) Hint(stringResource(Res.string.admin_empty))
            mine.take(8).forEach { BacklogRow(it, today, lang, onDone = { cb.complete(it) }, onOpen = {}) }
            Spacer(Modifier.size(10.dp))
            AddRow(stringResource(Res.string.admin_add_hint), { cb.addTo(it, Section.ADMIN) }, muted = true)
        }
        Topic.PROJECTS -> {
            Projects(backlog, steps, today, lang, cb)
            AttentionList(agenda?.needsAttention.orEmpty().filter { !it.item.isRoutine() }.map { it.item to it.ease }, cb.lessOften, cb.makeEasier, cb.smallStep)
            Spacer(Modifier.size(10.dp))
            AddRow(stringResource(Res.string.project_add_hint), { cb.addTo(it, Section.PROJECT) }, muted = true)
        }
        Topic.HOME -> {
            val due = dueRoutines(backlog, today).filter { it.topic == Topic.HOME }
            if (due.isEmpty()) Hint(stringResource(Res.string.home_empty))
            due.forEach { BacklogRow(it, today, lang, onDone = { cb.complete(it) }, onOpen = {}, showSection = false) }
            AttentionList(agenda?.needsAttention.orEmpty().filter { it.item.topic == Topic.HOME }.map { it.item to it.ease }, cb.lessOften, cb.makeEasier, cb.smallStep)
            Spacer(Modifier.size(10.dp))
            AddRow(stringResource(Res.string.home_add_hint), { cb.addTo(it, Section.CHORE) }, muted = true)
        }
        Topic.RHYTHM -> {
            RhythmBoard(backlog.filter { !it.done && it.topic == Topic.RHYTHM }, backlog.filter { it.isRoutineStep }.groupBy { it.parentId!! }, lang, cb)
            AttentionList(agenda?.needsAttention.orEmpty().filter { it.item.topic == Topic.RHYTHM }.map { it.item to it.ease }, cb.lessOften, cb.makeEasier, cb.smallStep)
            Spacer(Modifier.size(10.dp))
            AddRow(stringResource(Res.string.rhythm_add_hint), { cb.addTo(it, Section.RHYTHM) }, muted = true)
        }
        Topic.SHOPPING -> ShoppingStep(shopping, cb)
        Topic.MENU -> {
            Days(meals, dishes, today, lang, cb)
            CardAction(stringResource(Res.string.week_recipes), cb.goMenu)
        }
        Topic.FRICTION -> BoardStep(board, backlog.filter { it.isRoutine() && !it.done }, meeting.members, lang, heavy = meeting.week?.ease == Ease.HARD, cb)
        Topic.RULES -> {
            val rules = board.filter { it.kind == BoardKind.RULE && it.isOpen }
            if (rules.isEmpty()) Hint(stringResource(Res.string.rules_empty))
            else Text(stringResource(Res.string.rules_review_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rules.forEachIndexed { i, rule -> NumberedLine(i + 1, rule.text, Topic.RULES, meeting.ruleScores[rule.id]) }
            Spacer(Modifier.size(10.dp))
            AddRow(stringResource(Res.string.rules_add_hint), cb.addRule, muted = true)
        }
    }
}

/**
 * Ritmes & routines, laid out per moment of the day in the order the day goes. Habits show how they go (rated in
 * the Terugblik, never ticked); a chore with a fixed moment can be logged for today. Each routine can move to
 * another moment.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RhythmBoard(routines: List<BacklogItem>, steps: Map<String, List<BacklogItem>>, lang: Lang, cb: WeekCallbacks) {
    var asking by remember { mutableStateOf<BacklogItem?>(null) }
    if (routines.isEmpty()) {
        Hint(stringResource(Res.string.rhythm_empty))
        return
    }
    // Per moment in the order of the day, then checklists (used when needed), then the rest.
    val groups = Anchor.entries.map { a -> a.title(lang) to routines.filter { it.anchor == a } } +
        listOf(
            stringResource(Res.string.group_lists) to routines.filter { it.anchor == null && it.id in steps },
            stringResource(Res.string.anchor_none) to routines.filter { it.anchor == null && it.id !in steps },
        )
    groups.forEach { (title, mine) ->
        if (mine.isEmpty()) return@forEach
        GroupTitle(title)
        mine.forEach { item ->
            Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    MomentMenu(item, lang) { cb.setAnchor(item, it) }
                }
                steps[item.id]?.let { mine -> Text(mine.joinToString("  →  ") { it.text }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item.smallStep?.let { Text(stringResource(Res.string.small_step_line, it), style = MaterialTheme.typography.labelMedium, color = Section.HABIT.style().ink) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (item.isHabit) {
                        // Rated together in the Terugblik; here it only shows how it goes.
                        item.scoreNow?.let { ScoreBadge(it, word = true) }
                        if (item.scoreNow?.let { it < 4 } == true) TextButton(onClick = { asking = item }) { Text(stringResource(Res.string.small_step_add)) }
                    } else {
                        ChoiceChip(stringResource(Res.string.mark_done), false, { cb.complete(item) }, icon = HearthIcons.Check)
                    }
                }
            }
        }
    }
    asking?.let { h ->
        TextDialog(stringResource(Res.string.small_step_title), stringResource(Res.string.small_step_hint), onDone = { t, _ -> cb.smallStep(h, t) }) { asking = null }
    }
}

/** The moment chip: tap to move a routine to another moment of the day. */
@Composable
private fun MomentMenu(item: BacklogItem, lang: Lang, onPick: (Anchor?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ChoiceChip(item.anchor?.title(lang) ?: stringResource(Res.string.anchor_none), false, { open = true }, icon = HearthIcons.Repeat)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (Anchor.entries.map { it as Anchor? } + null).forEach { a ->
                DropdownMenuItem(text = { Text(a?.title(lang) ?: stringResource(Res.string.anchor_none)) }, onClick = { open = false; onPick(a) })
            }
        }
    }
}

/** Deze week, day by day: what has a day, each with its section's colour. Meals live in the Weekmenu tile. */
@Composable
private fun WeekStrip(backlog: List<BacklogItem>, meals: List<Meal>, today: LocalDate, lang: Lang) {
    (0 until 7).map { today.plusDays(it) }.forEach { day ->
        val planned = if (day == today) plannedToday(backlog, today) else plannedOn(backlog, day)
        val meal = meals.firstOrNull { it.date == day.toString() }
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp)) {
            val narrow = LocalNarrow.current
            Text(
                dayName(day, today, lang, short = narrow),
                style = MaterialTheme.typography.labelLarge,
                color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.width(if (narrow) 76.dp else 124.dp).padding(top = 2.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (planned.isEmpty()) {
                    Text(stringResource(Res.string.day_free), style = MaterialTheme.typography.bodyMedium, color = Section.PROTECTED_TIME.style().ink)
                }
                planned.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(item.section.style().ink, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(item.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                meal?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(HearthIcons.Restaurant, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(it.dish, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Seven days at a glance: the meal (tap to plan) and what has that day. */
@Composable
private fun Days(meals: List<Meal>, dishes: List<Meal>, today: LocalDate, lang: Lang, cb: WeekCallbacks) {
    var editing by remember { mutableStateOf<LocalDate?>(null) }
    var removing by remember { mutableStateOf<Meal?>(null) }
    (0 until 7).map { today.plusDays(it) }.forEach { day ->
        val meal = meals.firstOrNull { it.date == day.toString() }
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { editing = day }.padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Text(
                dayName(day, today, lang, short = LocalNarrow.current),
                style = MaterialTheme.typography.labelLarge,
                color = if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.width(if (LocalNarrow.current) 76.dp else 124.dp).padding(top = 2.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(HearthIcons.Restaurant, null, tint = if (meal == null) MaterialTheme.colorScheme.primary else Topic.MENU.style().ink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        meal?.dish ?: stringResource(Res.string.meal_add),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (meal == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (meal != null) DeleteButton({ removing = meal })
                }
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

/** Vooruitblik: what already has a day after this week (verlof, trips, birthdays, appointments). */
@Composable
private fun Ahead(backlog: List<BacklogItem>, today: LocalDate, lang: Lang) {
    val later = backlog.filter { it.plan == Plan.DAY && !it.isStep && (it.localDate?.let { d -> d >= today.plusDays(7) } ?: false) }
        .sortedBy { it.date }.take(8)
    if (later.isEmpty()) {
        EmptyHint(stringResource(Res.string.week_ahead_empty))
        return
    }
    later.forEach { item ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
            val d = item.localDate!!
            Text(
                "${weekdayName(d.dayOfWeek, lang, short = true)} ${d.day}/${d.month.ordinal + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = item.section.style().ink,
                modifier = Modifier.width(96.dp),
            )
            Text(item.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

// ---------- 1. look back ----------

/**
 * What each topic brings to Wat ging goed since the last meeting: what got done there, and wins Hearth saw (done work,
 * meals that were eaten, routines and house rules that go well, frictions settled, goals reached) to name in one tap.
 */
private fun lookBackPerTopic(
    agenda: WeekAgenda?,
    backlog: List<BacklogItem>,
    board: List<BoardItem>,
    meals: List<Meal>,
    last: Huddle?,
    meeting: MeetingState,
    today: LocalDate,
    lang: Lang,
): Map<Topic, TopicLookBack> {
    val since = last?.date ?: today.plusDays(-7)
    val done = agenda?.done.orEmpty().distinctBy { it.id }
    val byTopic = done.groupBy { it.topic ?: it.parentId?.let { p -> backlog.firstOrNull { b -> b.id == p }?.topic } }
    val eaten = meals.filter { m -> runCatching { LocalDate.parse(m.date) }.getOrNull()?.let { it >= since && it <= today } ?: false }
    val runs = backlog.filter { it.isHabit && !it.done }.mapNotNull { h -> (meeting.habitChecks[h.id] ?: h.scoreNow)?.takeIf { it >= 4 }?.let { "${h.text}: ${scoreWord(it, lang)}" } }
    val rules = board.filter { it.kind == BoardKind.RULE && it.isOpen }.mapNotNull { r -> meeting.ruleScores[r.id]?.takeIf { it >= 4 }?.let { "${r.text}: ${scoreWord(it, lang)}" } }
    val settled = agenda?.boardWins.orEmpty()
    return Topic.entries.associateWith { topic ->
        val mine = byTopic[topic].orEmpty().map { it.text }
        when (topic) {
            Topic.MENU -> TopicLookBack(eaten.size, eaten.map { it.dish }.distinct())
            Topic.RHYTHM -> TopicLookBack(mine.size, runs + mine)
            Topic.RULES -> TopicLookBack(0, rules)
            Topic.FRICTION -> settled.filter { it.isWorkingPoint }.let { TopicLookBack(it.size, it.map { f -> f.text }) }
            Topic.GOALS -> settled.filter { it.kind == BoardKind.GOAL }.let { TopicLookBack(it.size, it.map { g -> g.text }) }
            else -> TopicLookBack(mine.size, mine.distinct())
        }
    }
}

private fun dayName(day: LocalDate, today: LocalDate, lang: Lang, short: Boolean = false): String =
    if (day.toEpochDays() - today.toEpochDays() < 2) dayLabel(day, today, lang).replaceFirstChar { it.uppercase() }
    else "${weekdayName(day.dayOfWeek, lang, short).replaceFirstChar { it.uppercase() }} ${day.day}"

/** Dish + what to buy; dishes from before fill both in one tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealDialog(day: LocalDate, today: LocalDate, lang: Lang, current: Meal?, dishes: List<Meal>, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var dish by remember { mutableStateOf(current?.dish ?: "") }
    var ingredients by remember { mutableStateOf(current?.ingredients?.joinToString(", ") ?: "") }
    var asking by remember { mutableStateOf(false) }
    val dirty = dish != (current?.dish ?: "") || ingredients != (current?.ingredients?.joinToString(", ") ?: "")
    AlertDialog(
        onDismissRequest = { if (dirty) asking = true else onDismiss() },
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
    if (asking) DiscardDialog(onKeep = { asking = false }, onDiscard = { asking = false; onDismiss() })
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

// ---------- grote taken ----------

/** Each running project with its next step; a step without a day can go to "dit weekend". */
@Composable
private fun Projects(backlog: List<BacklogItem>, steps: List<BacklogItem>, today: LocalDate, lang: Lang, cb: WeekCallbacks) {
    val saturday = today.plusDays((DayOfWeek.SATURDAY.ordinal - today.dayOfWeek.ordinal + 7) % 7)
    val projects = backlog.filter { it.section == Section.PROJECT && !it.isStep && !it.done }
    if (projects.isEmpty()) {
        Hint(stringResource(Res.string.projects_empty))
        return
    }
    projects.forEach { project ->
        val mine = steps.filter { it.parentId == project.id }
        val next = mine.firstOrNull { !it.done }
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            BacklogRow(project, today, lang, onDone = { cb.complete(project) }, onOpen = {}, showSection = false)
            if (next != null) {
                BacklogRow(next, today, lang, onDone = { cb.complete(next) }, onOpen = {}, modifier = Modifier.padding(start = 24.dp), showSection = false) {
                    if (next.plan != Plan.DAY) TextButton(onClick = { cb.plan(next, When.DAY, saturday) }) { Text(stringResource(Res.string.plan_weekend)) }
                }
            }
        }
    }
}

// ---------- 6. Werkbord ----------

@Composable
private fun BoardStep(board: List<BoardItem>, routines: List<BacklogItem>, members: List<Member>, lang: Lang, heavy: Boolean, cb: WeekCallbacks) {
    val frictions = board.filter { it.isWorkingPoint && it.isOpen }
    val byId = members.associateBy { it.id }
    Text(stringResource(Res.string.step_board_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (heavy && frictions.isNotEmpty()) {
        Spacer(Modifier.size(10.dp))
        Hint(stringResource(Res.string.feeling_heavy_hint))
    }
    Spacer(Modifier.size(6.dp))
    if (frictions.isEmpty()) Hint(stringResource(Res.string.step_board_empty))
    // Still to talk about first; those with a direction chosen wait to be written.
    frictions.sortedBy { it.direction != null }.forEach { f ->
        FrictionRow(f, f.memberId?.let(byId::get), routines, lang, FrictionActions(cb.markFriction, cb.resolveFriction))
    }
}

/** "1. Schoenen uit in de gang": a numbered line in its topic's colour, with how it goes when that's known. */
@Composable
internal fun NumberedLine(n: Int, text: String, topic: Topic, score: Int? = null) {
    val s = topic.style()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
        Box(Modifier.size(28.dp).background(s.tint, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = s.ink)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        score?.let { ScoreBadge(it, word = true) }
    }
}

// ---------- Doelen ----------

/**
 * Doelen: what we want to reach, as a family, the two of us or one person. The meeting looks at each one's
 * next small step (or picks one); reached goals are celebrated on the Werkbord.
 */
@Composable
private fun GoalsStep(board: List<BoardItem>, backlog: List<BacklogItem>, today: LocalDate, lang: Lang, cb: WeekCallbacks) {
    var asking by remember { mutableStateOf<BoardItem?>(null) }
    val goals = board.filter { it.kind == BoardKind.GOAL && it.isOpen }
    Text(stringResource(Res.string.goals_meeting_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.size(8.dp))
    if (goals.isEmpty()) Hint(stringResource(Res.string.goals_empty))
    val byId = backlog.associateBy { it.id }
    goals.forEach { g -> GoalRow(g, g.linkedId?.let(byId::get), today, lang, onAchieve = { cb.achieveGoal(g) }, onPlan = { asking = g }) }
    Spacer(Modifier.size(10.dp))
    AddRow(stringResource(Res.string.goals_add_hint), cb.addGoal, muted = true, compact = true)
    asking?.let { g ->
        GoalPlanDialog(g, today, lang, onSave = { cb.setGoalStep(g, it) }) { asking = null }
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
            Text(meals.joinToString("  ·  ") { it.dish }, style = MaterialTheme.typography.bodyLarge, color = Topic.MENU.style().ink)
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
    val s = AccentStyle
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(s.tint, MaterialTheme.shapes.medium).padding(16.dp)) {
        Icon(HearthIcons.Flame, null, tint = s.ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = s.ink)
    }
}
