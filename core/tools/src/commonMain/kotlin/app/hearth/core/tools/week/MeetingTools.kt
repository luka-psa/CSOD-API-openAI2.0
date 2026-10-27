package app.hearth.core.tools.week

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.BoardKind
import app.hearth.core.model.BoardStatus
import app.hearth.core.model.FrictionDirection
import app.hearth.core.model.Huddle
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.TranscriptLine
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Ease
import app.hearth.core.model.easeNow
import app.hearth.core.model.isHabit
import app.hearth.core.model.title
import app.hearth.core.model.WeekRating
import app.hearth.core.model.weekWord
import app.hearth.core.model.SCORE_MAX
import app.hearth.core.model.score
import app.hearth.core.model.scoreNow
import app.hearth.core.model.scoreWord
import app.hearth.core.model.RuleCheck
import app.hearth.core.model.dayOf
import app.hearth.core.model.isRoutine
import app.hearth.core.model.normalizeName
import kotlinx.datetime.LocalDate
import app.hearth.core.model.Win
import app.hearth.core.model.Topic
import app.hearth.core.model.topic
import app.hearth.core.model.dayLabel
import app.hearth.core.model.plusDays
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolResult
import app.hearth.core.tools.board.member
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

// The Weekoverleg's look back: what went well, how heavy the week felt, what was said (Luister mee),
// and the summary that stays once the meeting is over.

// ---------- add_win ----------

@Serializable
data class AddWinArgs(
    @Describe("Something that went well, in the person's words, e.g. 'Elke avond samen gegeten'.")
    val text: String,
    @Describe("Who said it or who it's about (name), if known.")
    val member: String? = null,
)

@Serializable
data class WinResult(val win: Win, override val summary: String) : ToolResult

class AddWinTool(private val meeting: MeetingRepository, private val board: BoardRepository) : Tool<AddWinArgs, WinResult> {
    override val name = NAME
    override val description = "Note something that went well this week (wat ging goed). It shows in the next weekoverleg's look back."
    override val argsSerializer = AddWinArgs.serializer()
    override val resultSerializer = WinResult.serializer()

    override suspend fun run(args: AddWinArgs, ctx: ToolContext): WinResult {
        val text = args.text.trim().removePrefix("dat ging goed:").removePrefix("went well:").trim()
            .replaceFirstChar { it.uppercase() }.ifEmpty { throw ToolException(ctx.t("Wat ging er goed?", "What went well?")) }
        val who = board.member(args.member, ctx)?.id ?: ctx.actorMemberId
        val win = meeting.addWin(text, who)
        return WinResult(win, ctx.t("Mooi! Genoteerd bij wat goed ging: “${win.text}”", "Nice! Noted under what went well: “${win.text}”"))
    }

    companion object {
        const val NAME = "add_win"
    }
}

@Serializable
data class RemoveWinArgs(@Describe("Id of the win to take off.") val id: String)

@Serializable
data class RemovedResult(val removed: Boolean, override val summary: String) : ToolResult

class RemoveWinTool(private val meeting: MeetingRepository) : Tool<RemoveWinArgs, RemovedResult> {
    override val name = NAME
    override val description = "Take a win off this week's look back (tablet only)."
    override val destructive = true
    override val exposeOverMcp = false
    override val argsSerializer = RemoveWinArgs.serializer()
    override val resultSerializer = RemovedResult.serializer()

    override fun confirmQuestion(args: RemoveWinArgs, ctx: ToolContext) = ctx.t("Dit weghalen bij wat goed ging?", "Remove this from what went well?")

    override suspend fun run(args: RemoveWinArgs, ctx: ToolContext): RemovedResult {
        val known = meeting.currentWins().any { it.id == args.id }
        if (known) meeting.deleteWin(args.id)
        return RemovedResult(known, ctx.t("Weggehaald.", "Removed."))
    }

    companion object {
        const val NAME = "remove_win"
    }
}

// ---------- how the week went ----------

@Serializable
data class RateWeekArgs(
    @Describe("How the week went, agreed together, from 0 (was heel zwaar) to 5 (liep vanzelf). Leave empty when there is no agreement.")
    val score: Int? = null,
    @Describe("What Hearth suggested (0–5), if it was shown.")
    val suggested: Int? = null,
    @Describe("Older form: EASY, EFFORT or HARD. Use score instead.")
    val ease: Ease? = null,
)

@Serializable
data class RateWeekResult(val week: WeekRating, override val summary: String) : ToolResult

/** The household agrees on the week's weather together; no agreement is a fine answer too. Never about one person. */
class RateWeekTool(private val meeting: MeetingRepository) : Tool<RateWeekArgs, RateWeekResult> {
    override val name = NAME
    override val description =
        "Say how the week went for the household, agreed together, on a scale from 0 (was heel zwaar) to 5 (liep " +
            "vanzelf); its weather follows (bliksem 0–1, wolk 2–3, zon 4–5). Never about one person. No agreement: leave score empty."
    override val argsSerializer = RateWeekArgs.serializer()
    override val resultSerializer = RateWeekResult.serializer()

    override suspend fun run(args: RateWeekArgs, ctx: ToolContext): RateWeekResult {
        val week = meeting.setWeek((args.score ?: args.ease?.score)?.coerceIn(0, SCORE_MAX), args.suggested?.coerceIn(0, SCORE_MAX))
        val score = week.score
        val summary = if (score != null) ctx.t("De week ${weekWord(score, ctx.lang)}. Samen afgesproken.", "The week ${weekWord(score, ctx.lang)}. Agreed together.")
        else ctx.t("De week: geen akkoord. Ook dat is een antwoord.", "The week: no agreement. That's an answer too.")
        return RateWeekResult(week, summary)
    }

    companion object {
        const val NAME = "rate_week"
    }
}

@Serializable
data class WeekMetersArgs(@Describe("Ask how the week went (the weather) in the weekoverleg.") val enabled: Boolean)

@Serializable
data class WeekMetersResult(val enabled: Boolean, override val summary: String) : ToolResult

/** Rating the week can be switched off when it causes more friction than it takes away. */
class SetWeekMetersTool(private val settings: SettingsRepository) : Tool<WeekMetersArgs, WeekMetersResult> {
    override val name = NAME
    override val description = "Switch asking how the week went (its weather) on or off in the weekoverleg (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = WeekMetersArgs.serializer()
    override val resultSerializer = WeekMetersResult.serializer()

    override suspend fun run(args: WeekMetersArgs, ctx: ToolContext): WeekMetersResult {
        settings.put(METERS_SETTING, args.enabled.toString())
        return WeekMetersResult(
            args.enabled,
            if (args.enabled) ctx.t("Het weer van de week staat aan.", "The week's weather is on.") else ctx.t("Het weer van de week staat uit. Aanzetten kan altijd.", "The week's weather is off. You can switch it on any time."),
        )
    }

    companion object {
        const val NAME = "set_week_meters"
        const val METERS_SETTING = "week.meters"
    }
}

/** On unless switched off. */
internal suspend fun metersOn(settings: SettingsRepository) = settings.currentValue(SetWeekMetersTool.METERS_SETTING) != "false"

// ---------- check_house_rule ----------

@Serializable
data class CheckHouseRuleArgs(
    @Describe("Id of the house rule.")
    val ruleId: String? = null,
    @Describe("Its words, when the id isn't known.")
    val rule: String? = null,
    @Describe("How it went this week, like the routines, from 0 (komt er niet van) to 5 (loopt vanzelf).")
    val score: Int? = null,
    @Describe("Older form: EASY, EFFORT or HARD. Use score instead.")
    val ease: Ease? = null,
    @Describe("Older form: true = it worked, false = it didn't work yet. Use score instead.")
    val worked: Boolean? = null,
)

@Serializable
data class CheckHouseRuleResult(val check: RuleCheck, override val summary: String) : ToolResult

/** How did an agreement go this week? Same scale as the routines; for the household, it never says who. */
class CheckHouseRuleTool(private val meeting: MeetingRepository, private val board: BoardRepository) : Tool<CheckHouseRuleArgs, CheckHouseRuleResult> {
    override val name = NAME
    override val description =
        "In the weekoverleg's look back: say how a house rule went this week, like the routines, from 0 (komt er niet " +
            "van) to 5 (loopt vanzelf) (for the household, never who). A rule that is too much (0–1) two meetings in a row " +
            "comes back as a working point: maybe the agreement needs changing."
    override val argsSerializer = CheckHouseRuleArgs.serializer()
    override val resultSerializer = CheckHouseRuleResult.serializer()

    override suspend fun run(args: CheckHouseRuleArgs, ctx: ToolContext): CheckHouseRuleResult {
        val rules = board.allItems().filter { it.kind == BoardKind.RULE && it.isOpen }
        val key = args.rule?.let { normalizeName(it) }
        val rule = rules.firstOrNull { it.id == args.ruleId }
            ?: key?.let { k -> rules.firstOrNull { normalizeName(it.text) == k } ?: rules.firstOrNull { normalizeName(it.text).contains(k) } }
            ?: throw ToolException(ctx.t("Welke huisregel?", "Which house rule?"))
        val score = (args.score ?: args.ease?.score ?: args.worked?.let { if (it) Ease.EASY.score else Ease.HARD.score })?.coerceIn(0, SCORE_MAX)
            ?: throw ToolException(ctx.t("Hoe liep “${rule.text}”, van 0 tot 5?", "How did “${rule.text}” go, from 0 to 5?"))
        val check = meeting.setRuleCheck(rule.id, score)
        val said = scoreText(score, ctx)
        val summary = when (check.ease) {
            Ease.EASY -> ctx.t("Fijn: “${rule.text}” $said.", "Nice: “${rule.text}” $said.")
            Ease.EFFORT -> ctx.t("“${rule.text}”: $said. Wat zou het makkelijker maken?", "“${rule.text}”: $said. What would make it easier?")
            Ease.HARD -> ctx.t("“${rule.text}”: $said. Misschien mag de huisregel eenvoudiger.", "“${rule.text}”: $said. Maybe the agreement can be simpler.")
        }
        return CheckHouseRuleResult(check, summary)
    }

    companion object {
        const val NAME = "check_house_rule"
    }
}

/**
 * What asks for attention (not the family's own habit ratings): chores done much less often than their
 * rhythm, and tasks whose day keeps being pushed back. Each with its colour.
 */
internal suspend fun needingAttention(backlog: BacklogRepository, today: LocalDate): List<Pair<BacklogItem, Ease>> {
    val logs = backlog.routineLogsSince(today.plusDays(-120).toString())
    return backlog.openItems().filter { !it.isHabit && !it.isStep }
        .mapNotNull { item -> item.easeNow(logs[item.id].orEmpty(), dayOf(item.createdAt), today)?.takeIf { it != Ease.EASY }?.let { item to it } }
}

/** "Fietsen 3× verschoven" / "Planten water geven vraagt moeite". */
internal fun attentionText(item: BacklogItem, ease: Ease, ctx: ToolContext): String = when {
    item.postponed >= 2 && !item.isRoutine() -> ctx.t("${item.text} (${item.postponed}× verschoven)", "${item.text} (postponed ${item.postponed}×)")
    ease == Ease.HARD -> ctx.t("${item.text} (te zwaar)", "${item.text} (too much)")
    else -> ctx.t("${item.text} (vraagt moeite)", "${item.text} (takes effort)")
}

/** "loopt goed (4/5)". */
internal fun scoreText(score: Int, ctx: ToolContext) = "${scoreWord(score, ctx.lang)} ($score/$SCORE_MAX)"

internal fun easeWord(ease: Ease, ctx: ToolContext) = when (ease) {
    Ease.EASY -> ctx.t("loopt vanzelf", "runs easily")
    Ease.EFFORT -> ctx.t("vraagt moeite", "takes effort")
    Ease.HARD -> ctx.t("te zwaar", "too much")
}

// ---------- Luister mee ----------

@Serializable
data class TranscriptLineArgs(@Describe("What was heard between two pauses.") val text: String)

@Serializable
data class TranscriptLineResult(val line: TranscriptLine, override val summary: String) : ToolResult

class AddTranscriptLineTool(private val meeting: MeetingRepository) : Tool<TranscriptLineArgs, TranscriptLineResult> {
    override val name = NAME
    override val description = "Keep a line Luister mee heard during the weekoverleg (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = TranscriptLineArgs.serializer()
    override val resultSerializer = TranscriptLineResult.serializer()

    override suspend fun run(args: TranscriptLineArgs, ctx: ToolContext): TranscriptLineResult {
        val text = args.text.trim().ifEmpty { throw ToolException(ctx.t("Niets gehoord.", "Nothing heard.")) }
        return TranscriptLineResult(meeting.addLine(text), ctx.t("Bewaard.", "Kept."))
    }

    companion object {
        const val NAME = "add_transcript_line"
    }
}

@Serializable
data class MarkSortedArgs(@Describe("Ids of transcript lines that went through a Voorstel.") val lineIds: List<String>)

@Serializable
data class MarkSortedResult(val count: Int, override val summary: String) : ToolResult

class MarkTranscriptSortedTool(private val meeting: MeetingRepository) : Tool<MarkSortedArgs, MarkSortedResult> {
    override val name = NAME
    override val description = "Mark lines of the weekoverleg's conversation as sorted once what they say has been added, so the tablet " +
        "doesn't propose them again. Use the ids from read_conversation."
    override val argsSerializer = MarkSortedArgs.serializer()
    override val resultSerializer = MarkSortedResult.serializer()

    override suspend fun run(args: MarkSortedArgs, ctx: ToolContext): MarkSortedResult {
        meeting.markSorted(args.lineIds)
        return MarkSortedResult(args.lineIds.size, ctx.t("Verwerkt.", "Sorted."))
    }

    companion object {
        const val NAME = "mark_transcript_sorted"
    }
}

@Serializable
data class FixLineArgs(
    @Describe("Id of the line, from read_conversation.") val lineId: String,
    @Describe("What was really said.") val text: String,
)

@Serializable
data class FixLineResult(val lineId: String, val text: String, override val summary: String) : ToolResult

/** The recognizer mishears now and then ("oprecht" for "opgehaald"); the family, or their AI, fixes the words. */
class FixTranscriptLineTool(private val meeting: MeetingRepository) : Tool<FixLineArgs, FixLineResult> {
    override val name = NAME
    override val description = "Fix the words of one line Luister mee heard in this weekoverleg, when the recognizer misheard " +
        "(a name, a Flemish word). Only lines of the meeting that is still open."
    override val argsSerializer = FixLineArgs.serializer()
    override val resultSerializer = FixLineResult.serializer()

    override suspend fun run(args: FixLineArgs, ctx: ToolContext): FixLineResult {
        val text = args.text.trim().ifEmpty { throw ToolException(ctx.t("Wat werd er gezegd?", "What was said?")) }
        if (!meeting.fixLine(args.lineId, text)) {
            throw ToolException(ctx.t("Die zin vind ik niet meer in dit weekoverleg.", "I can't find that line in this meeting anymore."))
        }
        return FixLineResult(args.lineId, text, ctx.t("Verbeterd: “$text”", "Fixed: “$text”"))
    }

    companion object {
        const val NAME = "fix_transcript_line"
    }
}

@Serializable
class ReadConversationArgs

@Serializable
data class ConversationLine(val id: String, val time: String, val text: String, val sorted: Boolean)

@Serializable
data class ReadConversationResult(val lines: List<ConversationLine>, override val summary: String) : ToolResult

/**
 * The weekoverleg's conversation for the family's own AI: it understands dialect and half sentences far better than
 * the tablet's quick sorter, so it can draw the conclusions and add them with the regular tools.
 */
class ReadConversationTool(private val meeting: MeetingRepository, private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() }) :
    Tool<ReadConversationArgs, ReadConversationResult> {
    override val name = NAME
    override val readOnly = true
    override val description = "Read what Luister mee heard in this week's weekoverleg (weekly family meeting), line by line as the " +
        "speech recognizer wrote it down: a new line at every pause, so one thought often spans several lines, and words can be " +
        "misheard (Flemish speech). Read it for meaning: draw the conclusions (what, who, which day), add each with the regular " +
        "tools (add_to_backlog, add_shopping_item, plan_meal, add_house_rule, log_friction, add_goal, add_win), then call " +
        "mark_transcript_sorted with the ids you handled. Lines marked sorted were already put in place on the tablet."
    override val argsSerializer = ReadConversationArgs.serializer()
    override val resultSerializer = ReadConversationResult.serializer()

    @OptIn(kotlin.time.ExperimentalTime::class)
    override suspend fun run(args: ReadConversationArgs, ctx: ToolContext): ReadConversationResult {
        val lines = meeting.currentTranscript().map { line ->
            val at = kotlin.time.Instant.fromEpochMilliseconds(line.createdAt).toLocalDateTime(zone())
            ConversationLine(line.id, "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}", line.text, line.sorted)
        }
        val open = lines.count { !it.sorted }
        val summary = when {
            lines.isEmpty() -> ctx.t("Luister mee heeft in dit weekoverleg nog niets gehoord.", "Luister mee hasn't heard anything in this meeting yet.")
            open == 0 -> ctx.t("Alles uit het gesprek staat al op zijn plek.", "Everything from the conversation is already in place.")
            else -> ctx.t("$open zinnen nog niet op hun plek:\n", "$open lines not in place yet:\n") +
                lines.filter { !it.sorted }.joinToString("\n") { "${it.time}  ${it.text}" }
        }
        return ReadConversationResult(lines, summary)
    }

    companion object {
        const val NAME = "read_conversation"
    }
}

@Serializable
class ClearTranscriptArgs

@Serializable
data class ClearTranscriptResult(val removed: Int, override val summary: String) : ToolResult

class ClearTranscriptTool(private val meeting: MeetingRepository) : Tool<ClearTranscriptArgs, ClearTranscriptResult> {
    override val name = NAME
    override val description = "Throw away what Luister mee heard in this meeting so far (tablet only)."
    override val destructive = true
    override val exposeOverMcp = false
    override val argsSerializer = ClearTranscriptArgs.serializer()
    override val resultSerializer = ClearTranscriptResult.serializer()

    override fun confirmQuestion(args: ClearTranscriptArgs, ctx: ToolContext) =
        ctx.t("Het gesprek van dit weekoverleg wissen?", "Clear this meeting's conversation?")

    override suspend fun run(args: ClearTranscriptArgs, ctx: ToolContext): ClearTranscriptResult {
        val n = meeting.currentTranscript().size
        meeting.clearTranscript()
        return ClearTranscriptResult(n, ctx.t("Het gesprek is gewist.", "The conversation is cleared."))
    }

    companion object {
        const val NAME = "clear_transcript"
    }
}

// ---------- list_huddles ----------

@Serializable
data class ListHuddlesArgs(@Describe("How many recent meetings, newest first.") val limit: Int = 6)

@Serializable
data class HuddlesResult(val huddles: List<Huddle>, override val summary: String) : ToolResult

class ListHuddlesTool(private val meeting: MeetingRepository) : Tool<ListHuddlesArgs, HuddlesResult> {
    override val name = NAME
    override val description = "Summaries of the last weekly meetings (weekoverleg): what went well, how heavy, what was agreed and planned."
    override val argsSerializer = ListHuddlesArgs.serializer()
    override val resultSerializer = HuddlesResult.serializer()

    override suspend fun run(args: ListHuddlesArgs, ctx: ToolContext): HuddlesResult {
        val huddles = meeting.recentHuddles(args.limit.coerceIn(1, 52))
        val summary = if (huddles.isEmpty()) ctx.t("Nog geen weekoverleg gehouden.", "No weekly meeting held yet.")
        else huddles.joinToString("\n\n") { it.summary.ifBlank { ctx.t("Weekoverleg van ${it.heldOn}", "Weekly meeting of ${it.heldOn}") } }
        return HuddlesResult(huddles, summary)
    }

    companion object {
        const val NAME = "list_huddles"
    }
}

// ---------- finish_weekoverleg ----------

@Serializable
data class FinishArgs(
    @Describe("True: only write the summary so the family can read and agree on it; nothing is recorded yet.")
    val preview: Boolean = false,
)

@Serializable
data class HuddleResult(
    /** The recorded meeting; null for a preview. */
    val huddle: Huddle?,
    override val summary: String,
    /** The meeting's summary, one line per part ("Admin: …"). */
    val lines: List<String> = emptyList(),
) : ToolResult

/**
 * Ends the meeting and writes its summary (kept for good): what went well, how the week went (with Hearth's
 * guess when the family saw it differently), agreements that didn't work yet, habits asking for attention,
 * what was agreed, the working points, what got a day, and the meals.
 */
class FinishWeekoverlegTool(
    private val week: WeekRepository,
    private val meeting: MeetingRepository,
    private val backlog: BacklogRepository,
    private val board: BoardRepository,
    private val settings: SettingsRepository,
) : Tool<FinishArgs, HuddleResult> {
    override val name = NAME
    override val description = "Mark this week's meeting as held and write its summary, so next week's look back starts from today. " +
        "With preview, only write the summary so the family can agree on it first."
    override val argsSerializer = FinishArgs.serializer()
    override val resultSerializer = HuddleResult.serializer()

    @OptIn(ExperimentalTime::class)
    override suspend fun run(args: FinishArgs, ctx: ToolContext): HuddleResult {
        val last = week.last()
        val since = last?.createdAt ?: ctx.today.plusDays(-7).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val names = board.allMembers().associate { it.id to it.name }
        val wins = meeting.currentWins()
        val weather = if (metersOn(settings)) meeting.currentWeek() else null
        val checks = meeting.currentRuleChecks()
        val checkedBefore = last?.let { meeting.ruleChecksOf(it.id) }.orEmpty()
        val attention = needingAttention(backlog, ctx.today)
        val habits = backlog.openItems().filter { it.isHabit && it.scoreNow != null }
        val done = backlog.doneItemsSince(since)
        val boardItems = board.allItems()
        val rules = boardItems.filter { it.kind == BoardKind.RULE && it.isOpen && it.createdAt >= since }
        val newPoints = boardItems.filter { it.isWorkingPoint && it.isOpen && it.createdAt >= since }
        val decided = boardItems.filter { it.isWorkingPoint && (it.closedAt ?: 0) >= since && it.status != BoardStatus.OPEN }
        val planned = backlog.openItems().filter { it.createdAt >= since && it.plan == Plan.DAY && it.localDate != null }.sortedBy { it.date }
        val meals = week.mealsBetween(ctx.today.toString(), ctx.today.plusDays(7).toString())

        val lines = buildList {
            add(ctx.t("Weekoverleg van ${dayLabel(ctx.today, ctx.today, ctx.lang)} (${ctx.today.day}/${ctx.today.month.ordinal + 1})",
                "Weekly meeting of ${dayLabel(ctx.today, ctx.today, ctx.lang)} (${ctx.today.day}/${ctx.today.month.ordinal + 1})"))
            val good = wins.map { w -> w.text + (w.memberId?.let(names::get)?.let { " ($it)" } ?: "") }
            if (good.isNotEmpty() || done.isNotEmpty()) {
                add(ctx.t("Wat ging goed: ", "What went well: ") + (good + listOfNotNull(
                    done.size.takeIf { it > 0 }?.let { ctx.t("$it ${if (it == 1) "ding" else "dingen"} gedaan", "$it ${if (it == 1) "thing" else "things"} done") },
                )).joinToString("; "))
            }
            weather?.let { w ->
                val score = w.score
                val guess = w.suggested
                add(
                    ctx.t("De week: ", "The week: ") + when {
                        score == null -> ctx.t("geen akkoord", "no agreement")
                        guess != null && guess != score -> weekWord(score, ctx.lang) + ctx.t(" (Hearth dacht: ${weekWord(guess, ctx.lang)})", " (Hearth thought: ${weekWord(guess, ctx.lang)})")
                        else -> weekWord(score, ctx.lang)
                    },
                )
            }
            // How the agreements went, like the routines below; the hardest first.
            val rated = checks.sortedBy { it.score }.mapNotNull { c -> boardItems.firstOrNull { it.id == c.ruleId }?.let { it to c.score } }
            if (rated.isNotEmpty()) add(ctx.t("Huisregels: ", "House rules: ") + rated.joinToString("; ") { (r, n) -> "${r.text}: ${scoreText(n, ctx)}" })
            val notYet = checks.filter { !it.worked }.mapNotNull { c -> boardItems.firstOrNull { it.id == c.ruleId } }
            // Two meetings in a row: the agreement itself becomes a working point (maybe it asks too much).
            if (!args.preview) notYet.filter { r -> checkedBefore.any { it.ruleId == r.id && !it.worked } }.forEach { r ->
                board.add(BoardKind.FRICTION, ctx.t("Huisregel loopt nog niet: ${r.text}", "House rule not working yet: ${r.text}"))
            }
            if (habits.isNotEmpty()) {
                add(Topic.RHYTHM.title(ctx.lang) + ": " + habits.sortedBy { it.scoreNow }.joinToString("; ") { h ->
                    "${h.text}: ${scoreText(h.scoreNow!!, ctx)}" + (h.smallStep?.let { ctx.t(" (begin klein: $it)", " (start small: $it)") } ?: "")
                })
            }
            if (attention.isNotEmpty()) add(ctx.t("Vraagt aandacht: ", "Asks for attention: ") + attention.joinToString("; ") { (item, ease) -> attentionText(item, ease, ctx) })
            if (rules.isNotEmpty()) add(ctx.t("Nieuwe huisregels: ", "New house rules: ") + rules.joinToString("; ") { it.text })
            if (decided.isNotEmpty()) {
                add(ctx.t("Besproken: ", "Talked through: ") + decided.joinToString("; ") { d ->
                    d.text + " → " + when (d.status) {
                        BoardStatus.BECAME_RULE -> ctx.t("huisregel", "house rule")
                        BoardStatus.BECAME_CHANGE -> ctx.t("een taak", "a task")
                        BoardStatus.BECAME_ROUTINE -> if (d.direction == FrictionDirection.CHANGE_ROUTINE) ctx.t("routine aangepast", "routine adjusted") else ctx.t("een routine", "a routine")
                        else -> ctx.t("losgelaten", "let go")
                    }
                })
            }
            // Decided what it needs, still to be written in our own words.
            val chosen = boardItems.filter { it.isWorkingPoint && it.isOpen && it.direction != null }
            if (chosen.isNotEmpty()) add(ctx.t("Nog te doen: ", "Still to do: ") + chosen.joinToString("; ") { "${it.text} → ${app.hearth.core.tools.board.directionWords(it.direction!!, ctx)}" })
            newPoints.filter { it.direction == null }.takeIf { it.isNotEmpty() }?.let { points -> add(ctx.t("Nieuwe werkpunten: ", "New working points: ") + points.joinToString("; ") { it.text }) }
            // What got a day, by topic, so everyone sees where it landed.
            planned.groupBy { it.topic }.entries.sortedBy { it.key?.ordinal ?: Int.MAX_VALUE }.forEach { (topic, items) ->
                add((topic?.title(ctx.lang) ?: ctx.t("Gepland", "Planned")) + ": " + items.joinToString("; ") { "${it.text} (${dayLabel(it.localDate!!, ctx.today, ctx.lang)})" })
            }
            if (meals.isNotEmpty()) add(Topic.MENU.title(ctx.lang) + ": " + meals.joinToString(", ") { it.dish })
        }
        if (args.preview) return HuddleResult(null, lines.joinToString("\n"), lines)
        val huddle = meeting.close(ctx.today.toString(), lines.joinToString("\n"))
        return HuddleResult(huddle, ctx.t("Mooi weekoverleg! Jullie zijn klaar voor de week.", "Nice weekly meeting! You're set for the week."), lines)
    }

    companion object {
        const val NAME = "finish_weekoverleg"
    }
}
