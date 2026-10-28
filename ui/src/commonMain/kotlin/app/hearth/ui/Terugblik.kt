package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.core.model.Anchor
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.HabitReview
import app.hearth.core.model.HabitTip
import app.hearth.core.model.Huddle
import app.hearth.core.model.Lang
import app.hearth.core.model.Member
import app.hearth.core.model.TipAction
import app.hearth.core.model.Topic
import app.hearth.core.model.TopicGroup
import app.hearth.core.model.Win
import app.hearth.core.model.dayLabel
import app.hearth.core.model.easeOf
import app.hearth.core.model.isHabit
import app.hearth.core.model.isRoutine
import app.hearth.core.model.scoreNow
import app.hearth.core.model.scoreWord
import app.hearth.core.model.title
import app.hearth.core.model.topic
import app.hearth.core.model.topics
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.add
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.coach_before
import app.hearth.ui.resources.coach_said
import app.hearth.ui.resources.delete
import app.hearth.ui.resources.earlier_meetings
import app.hearth.ui.resources.edit
import app.hearth.ui.resources.history_empty
import app.hearth.ui.resources.history_open
import app.hearth.ui.resources.last_time_line
import app.hearth.ui.resources.pick_empty
import app.hearth.ui.resources.pick_title
import app.hearth.ui.resources.review_add_hint
import app.hearth.ui.resources.review_list_hint
import app.hearth.ui.resources.review_list_title
import app.hearth.ui.resources.review_suggest
import app.hearth.ui.resources.rule_moment_title
import app.hearth.ui.resources.rule_small_hint
import app.hearth.ui.resources.rule_small_title
import app.hearth.ui.resources.save
import app.hearth.ui.resources.small_step_hint
import app.hearth.ui.resources.small_step_title
import app.hearth.ui.resources.transcript_last
import app.hearth.ui.resources.win_remove
import app.hearth.ui.resources.wins_own_words
import app.hearth.ui.theme.AccentStyle
import app.hearth.ui.theme.style
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

// The Terugblik is a short list the family builds together, not a wall of everything: say what went well in a
// sentence, or tap a topic and pick the routine or house rule to talk about, then rate it right there. Hearth only
// suggests the few that ask for attention. What was decided last time is one line; every earlier meeting is a tap
// away and can still be put right.

/** Everything the Terugblik can do; each is one tool call in the ViewModel. */
class TerugblikCallbacks(
    val addWin: (String, Topic?) -> Unit,
    val removeWin: (Win) -> Unit,
    val rateRoutine: (BacklogItem, Int) -> Unit,
    val rateRule: (BoardItem, Int) -> Unit,
    val routineTip: (BacklogItem, HabitTip) -> Unit,
    val smallStep: (BacklogItem, String) -> Unit,
    val pairing: (BacklogItem, String) -> Unit,
    val ruleHelp: (BoardItem, RuleHelp) -> Unit,
    /** Tips that point at a routine's moment, days or steps open its sheet. */
    val openItem: (BacklogItem) -> Unit,
    val editHuddle: (Huddle, String) -> Unit,
    val showLastTranscript: () -> Unit,
)

/** What the Terugblik reads: the meeting so far, the coach per routine and house rule, and the meetings before. */
class TerugblikState(
    val doneCount: Int,
    val since: String,
    val huddles: List<Huddle>,
    val hasLastTranscript: Boolean,
    val wins: List<Win>,
    val members: List<Member>,
    /** Per topic: what got done and what Hearth saw going well, to name in one tap. */
    val perTopic: Map<Topic, TopicLookBack>,
    /** This meeting's score per routine, by id. */
    val routineChecks: Map<String, Int>,
    val routineReviews: Map<String, HabitReview>,
    /** This meeting's score per house rule, by id. */
    val ruleChecks: Map<String, Int>,
    val ruleReviews: Map<String, HabitReview>,
    val week: WeekWeather,
)

/** The things one can rate together: habits, household chores with a rhythm, and house rules. */
private val RATED = setOf(Topic.RHYTHM, Topic.HOME, Topic.RULES)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Terugblik(state: TerugblikState, backlog: List<BacklogItem>, board: List<BoardItem>, today: LocalDate, lang: Lang, cb: TerugblikCallbacks) {
    // Picked to talk about but not rated yet; once rated, it stays in the list by its score of this meeting.
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    var picking by remember { mutableStateOf<Topic?>(null) }
    var history by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Win?>(null) }
    var text by remember { mutableStateOf("") }

    val routines = backlog.filter { (it.isHabit || it.isRoutine()) && !it.done && !it.isStep }
    val rules = board.filter { it.kind == BoardKind.RULE && it.isOpen }
    val shownRoutines = routines.filter { it.id in picked || it.id in state.routineChecks }
    val shownRules = rules.filter { it.id in picked || it.id in state.ruleChecks }
    // Hearth's few: what asks for attention (too much, takes effort, or talked about) and isn't in the list yet.
    val suggested = (
        routines.filter { r -> state.routineReviews[r.id]?.needsChange == true && r.isHabit }.map { it.id to it.text } +
            rules.filter { r -> state.ruleReviews[r.id]?.needsChange == true }.map { it.id to it.text }
        ).filter { (id, _) -> id !in picked && id !in state.routineChecks && id !in state.ruleChecks }.take(4)

    // 1. What got done, in one line, and last time in one line, with every meeting a tap away.
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 10.dp)) {
        Text("${state.doneCount}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(10.dp))
        Text(state.since, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
    }
    val last = state.huddles.firstOrNull()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)).clickable { history = true }
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Icon(HearthIcons.Paper, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            last?.date?.let { stringResource(Res.string.last_time_line, dayLabel(it, today, lang)) } ?: stringResource(Res.string.history_empty),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { history = true }) { Text(stringResource(Res.string.history_open)) }
    }
    Spacer(Modifier.size(12.dp))

    // 2. The week as a whole.
    WeekWeatherRow(state.week, lang)

    // 3. What we talk about: built together, one line at a time.
    GroupTitle(stringResource(Res.string.review_list_title))
    Text(stringResource(Res.string.review_list_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
        OutlinedTextField(
            text, { text = it },
            placeholder = { Text(stringResource(Res.string.review_add_hint)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (text.isNotBlank()) { cb.addWin(text.trim(), null); text = "" } }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { cb.addWin(text.trim(), null); text = "" }, enabled = text.isNotBlank()) { Icon(HearthIcons.Add, stringResource(Res.string.add)) }
    }
    // Every topic as a small chip, in the order of every screen: tap one to pick from what is there.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp)) {
        TopicGroup.entries.flatMap { it.topics }.forEach { t ->
            val s = t.style()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(s.tint).clickable { picking = t }.heightIn(min = 36.dp).padding(horizontal = 12.dp),
            ) {
                Icon(t.icon(), null, tint = s.ink, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(t.title(lang), style = MaterialTheme.typography.labelMedium, color = s.ink, maxLines = 1)
            }
        }
    }
    if (suggested.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 10.dp, top = 6.dp)) {
            Text(stringResource(Res.string.review_suggest), style = MaterialTheme.typography.labelMedium, color = AccentStyle.ink, modifier = Modifier.align(Alignment.CenterVertically))
            suggested.forEach { (id, name) -> ChoiceChip(name, false, { picked = picked + id }, icon = HearthIcons.Add) }
        }
    }
    Spacer(Modifier.size(6.dp))

    shownRoutines.forEach { r ->
        val review = state.routineReviews[r.id]
        val now = state.routineChecks[r.id]
        RatedLine(r.text, r.topic ?: Topic.RHYTHM, now, review, lang, onRate = { cb.rateRoutine(r, it) }, onRemove = { picked = picked - r.id }.takeIf { now == null }) { tip ->
            RoutineTip(r, tip, cb)
        }
    }
    shownRules.forEach { rule ->
        val now = state.ruleChecks[rule.id]
        RatedLine(rule.text, Topic.RULES, now, state.ruleReviews[rule.id], lang, onRate = { cb.rateRule(rule, it) }, onRemove = { picked = picked - rule.id }.takeIf { now == null }) { tip ->
            RuleTip(rule, tip, cb, lang)
        }
    }
    val byId = state.members.associateBy { it.id }
    state.wins.sortedBy { it.topic?.ordinal ?: -1 }.forEach { win ->
        val s = win.topic?.style() ?: AccentStyle
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp)) {
            Icon(win.topic?.icon() ?: HearthIcons.Heart, win.topic?.title(lang), tint = s.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(win.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            win.memberId?.let(byId::get)?.let { MemberDot(it) }
            DeleteButton({ removing = win })
        }
    }

    picking?.let { topic ->
        if (topic in RATED) {
            val items = if (topic == Topic.RULES) rules.map { Triple(it.id, it.text, state.ruleChecks[it.id] ?: state.ruleReviews[it.id]?.score) }
            else routines.filter { it.topic == topic }.map { Triple(it.id, it.text, state.routineChecks[it.id] ?: it.scoreNow) }
            PickSheet(topic, items, state.perTopic[topic] ?: TopicLookBack(), lang, onPick = { picked = picked + it }, onWin = { cb.addWin(it, topic) }) { picking = null }
        } else {
            WinSheet(topic, state.perTopic[topic] ?: TopicLookBack(), state.wins.filter { it.topic == topic }, lang, onAdd = { cb.addWin(it, topic) }, onRemove = { removing = it }) { picking = null }
        }
    }
    if (history) MeetingHistorySheet(state.huddles, state.hasLastTranscript, today, lang, cb.editHuddle, onTranscript = { history = false; cb.showLastTranscript() }) { history = false }
    removing?.let { w -> ConfirmDialog(stringResource(Res.string.win_remove), w.text, onYes = { cb.removeWin(w) }) { removing = null } }
}

/**
 * One routine or house rule in the Terugblik: its name and badge, 0 to 5 with last time as a ring, the meetings
 * before as small bars, and when it asks for one, the one thing to try. Not rated yet: an X takes it off the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatedLine(
    name: String,
    topic: Topic,
    now: Int?,
    review: HabitReview?,
    lang: Lang,
    onRate: (Int) -> Unit,
    onRemove: (() -> Unit)?,
    tip: @Composable (HabitTip) -> Unit,
) {
    val history = review?.ratings.orEmpty().let { if (now != null) it.dropLast(1) else it }
    Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(topic.icon(), null, tint = topic.style().ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
            (now ?: history.lastOrNull())?.let { Spacer(Modifier.width(8.dp)); ScoreBadge(it) }
            Spacer(Modifier.weight(1f))
            onRemove?.let { DeleteButton(it) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
            ScoreScale(now, onRate, guess = history.lastOrNull())
            now?.let { Text(scoreWord(it, lang), style = MaterialTheme.typography.labelMedium, color = easeColors(easeOf(it)).first, modifier = Modifier.align(Alignment.CenterVertically)) }
            if (history.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp)) {
                    Text(stringResource(Res.string.coach_before), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ScoreTrail(history.takeLast(6), height = 18.dp)
                }
            }
        }
        // Only once it is rated: what was said about it, and the one thing to try when it asks for one.
        if (now != null && review != null) {
            review.frictions.lastOrNull()?.let {
                Text(stringResource(Res.string.coach_said, "“$it”"), style = MaterialTheme.typography.bodySmall, color = Topic.FRICTION.style().ink, modifier = Modifier.padding(top = 2.dp))
            }
            review.tips.firstOrNull()?.takeIf { review.needsChange }?.let { tip(it) }
        }
    }
}

/** A routine's tip, right there: one tap, or the few words it needs. */
@Composable
private fun RoutineTip(r: BacklogItem, tip: HabitTip, cb: TerugblikCallbacks) {
    var asking by remember { mutableStateOf<TipAction?>(null) }
    CoachTip(tip, LocalLang.current, onClick = {
        when {
            tip.action.oneTap -> cb.routineTip(r, tip)
            tip.action == TipAction.SMALL_STEP || tip.action == TipAction.PAIR -> asking = tip.action
            else -> cb.openItem(r)
        }
    }, modifier = Modifier.padding(top = 6.dp, end = 4.dp))
    when (asking) {
        TipAction.SMALL_STEP -> TextDialog(stringResource(Res.string.small_step_title), stringResource(Res.string.small_step_hint), onDone = { t, _ -> cb.smallStep(r, t) }) { asking = null }
        TipAction.PAIR -> PairingDialog(r.pairing, { cb.pairing(r, it) }) { asking = null }
        else -> {}
    }
}

/** A house rule's tip, right there: a moment, something nice, a small version, or say it in Wat ging goed. */
@Composable
private fun RuleTip(rule: BoardItem, tip: HabitTip, cb: TerugblikCallbacks, lang: Lang) {
    var asking by remember { mutableStateOf<TipAction?>(null) }
    CoachTip(tip, lang, onClick = {
        if (tip.action == TipAction.CELEBRATE) cb.addWin(rule.text, Topic.RULES) else asking = tip.action
    }, modifier = Modifier.padding(top = 6.dp, end = 4.dp))
    when (asking) {
        TipAction.MOMENT, TipAction.DAYS -> AnchorDialog(stringResource(Res.string.rule_moment_title), rule.anchor, lang, onPick = { a: Anchor? ->
            cb.ruleHelp(rule, if (a == null) RuleHelp(noMoment = true) else RuleHelp(anchor = a))
        }) { asking = null }
        TipAction.PAIR -> PairingDialog(rule.pairing, { cb.ruleHelp(rule, RuleHelp(pairing = it)) }) { asking = null }
        null -> {}
        else -> TextDialog(stringResource(Res.string.rule_small_title), stringResource(Res.string.rule_small_hint), onDone = { t, _ -> cb.ruleHelp(rule, RuleHelp(smallStep = t)) }) { asking = null }
    }
}

/**
 * "Ritmes & routines: welke?": the routines (or house rules) there are, each with its badge; tap one to talk about
 * it. Hearth's one-tap wins and own words still fit below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickSheet(topic: Topic, items: List<Triple<String, String, Int?>>, look: TopicLookBack, lang: Lang, onPick: (String) -> Unit, onWin: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val s = topic.style()
    FormSheet(dirty = text.isNotBlank(), onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(topic.icon(), null, tint = s.ink, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.pick_title, topic.title(lang)), style = MaterialTheme.typography.titleLarge)
            }
            if (items.isEmpty()) Text(stringResource(Res.string.pick_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEach { (id, name, score) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onPick(id); onDismiss() }.heightIn(min = 52.dp).padding(horizontal = 8.dp),
                ) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    score?.let { ScoreBadge(it) }
                    Spacer(Modifier.width(4.dp))
                    Icon(HearthIcons.Add, null, tint = s.ink, modifier = Modifier.size(20.dp))
                }
            }
            if (look.suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    look.suggestions.take(8).forEach { sug -> ChoiceChip(sug, false, { onWin(sug) }, icon = HearthIcons.Heart) }
                }
            }
            OutlinedTextField(text, { text = it }, placeholder = { Text(stringResource(Res.string.wins_own_words)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            FilledTonalButton(onClick = { onWin(text.trim()); text = "" }, enabled = text.isNotBlank()) { Text(stringResource(Res.string.add)) }
        }
    }
}

/**
 * Every weekoverleg so far, newest first: its summary as agreed at Klaar. A pencil puts a mistake right afterwards;
 * the conversation of the last one is there while it's kept.
 */
@Composable
fun MeetingHistorySheet(huddles: List<Huddle>, hasTranscript: Boolean, today: LocalDate, lang: Lang, onSave: (Huddle, String) -> Unit, onTranscript: () -> Unit, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val dirty = editing != null && draft != huddles.firstOrNull { it.id == editing }?.summary
    FormSheet(dirty = dirty, onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(Res.string.earlier_meetings), style = MaterialTheme.typography.titleLarge)
            if (huddles.isEmpty()) Text(stringResource(Res.string.history_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            huddles.forEachIndexed { i, h ->
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), MaterialTheme.shapes.medium).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(h.date?.let { dayLabel(it, today, lang).replaceFirstChar { c -> c.uppercase() } } ?: h.heldOn, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        if (i == 0 && hasTranscript) TextButton(onClick = onTranscript) { Text(stringResource(Res.string.transcript_last)) }
                        if (editing != h.id) IconButton(onClick = { editing = h.id; draft = h.summary }) { Icon(HearthIcons.Edit, stringResource(Res.string.edit), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (editing == h.id) {
                        OutlinedTextField(draft, { draft = it }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                            FilledTonalButton(onClick = { onSave(h, draft); editing = null }, enabled = draft.isNotBlank() && draft != h.summary) { Text(stringResource(Res.string.save)) }
                            TextButton(onClick = { editing = null }) { Text(stringResource(Res.string.cancel)) }
                        }
                    } else if (h.summary.isNotBlank()) {
                        SummaryText(h.summary)
                    }
                }
            }
        }
    }
}
