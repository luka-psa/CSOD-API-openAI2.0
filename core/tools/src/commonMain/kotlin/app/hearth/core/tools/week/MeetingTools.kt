package app.hearth.core.tools.week

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.BoardKind
import app.hearth.core.model.BoardStatus
import app.hearth.core.model.Huddle
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.TranscriptLine
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Ease
import app.hearth.core.model.easeNow
import app.hearth.core.model.isHabit
import app.hearth.core.model.Meter
import app.hearth.core.model.title
import app.hearth.core.model.word
import app.hearth.core.model.MeterValue
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

// ---------- the two meters ----------

@Serializable
data class WeekMeterArgs(
    @Describe("FLOW: how the week ran (1 moeizaam – 5 heel goed). STRAIN: what rubbed (1 niets – 5 veel).")
    val meter: Meter,
    @Describe("What the household agreed on together, 1–5. Leave empty when there is no agreement: it counts as neutral (3).")
    val value: Int? = null,
    @Describe("What Hearth suggested, if it was shown.")
    val suggested: Int? = null,
)

@Serializable
data class WeekMeterResult(val meter: MeterValue, override val summary: String) : ToolResult

/** The household agrees on a meter together; no agreement is a fine answer too (neutral). */
class SetWeekMeterTool(private val meeting: MeetingRepository) : Tool<WeekMeterArgs, WeekMeterResult> {
    override val name = NAME
    override val description =
        "Set one of the weekoverleg's two household meters, agreed together: FLOW (how the week ran) or STRAIN (what " +
            "rubbed). Never about one person. No agreement: leave value empty, it counts as neutral."
    override val argsSerializer = WeekMeterArgs.serializer()
    override val resultSerializer = WeekMeterResult.serializer()

    override suspend fun run(args: WeekMeterArgs, ctx: ToolContext): WeekMeterResult {
        if (args.value != null && args.value !in 1..5) throw ToolException(ctx.t("Kies iets van 1 tot 5.", "Pick 1 to 5."))
        val value = meeting.setMeter(args.meter, args.value, args.suggested)
        val title = meterTitle(args.meter, ctx)
        val summary = if (value.agreed) ctx.t("$title: ${meterWord(args.meter, value.value, ctx)}. Samen afgesproken.", "$title: ${meterWord(args.meter, value.value, ctx)}. Agreed together.")
        else ctx.t("$title: geen akkoord, dus neutraal. Ook dat is een antwoord.", "$title: no agreement, so neutral. That's an answer too.")
        return WeekMeterResult(value, summary)
    }

    companion object {
        const val NAME = "set_week_meter"
    }
}

internal fun meterTitle(meter: Meter, ctx: ToolContext) = meter.title(ctx.lang)

internal fun meterWord(meter: Meter, value: Int, ctx: ToolContext) = meter.word(value, ctx.lang)

@Serializable
data class WeekMetersArgs(@Describe("Show the two meters in the weekoverleg.") val enabled: Boolean)

@Serializable
data class WeekMetersResult(val enabled: Boolean, override val summary: String) : ToolResult

/** The meters can be switched off when they cause more friction than they take away. */
class SetWeekMetersTool(private val settings: SettingsRepository) : Tool<WeekMetersArgs, WeekMetersResult> {
    override val name = NAME
    override val description = "Switch the weekoverleg's two meters on or off (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = WeekMetersArgs.serializer()
    override val resultSerializer = WeekMetersResult.serializer()

    override suspend fun run(args: WeekMetersArgs, ctx: ToolContext): WeekMetersResult {
        settings.put(METERS_SETTING, args.enabled.toString())
        return WeekMetersResult(
            args.enabled,
            if (args.enabled) ctx.t("De meters staan aan.", "The meters are on.") else ctx.t("De meters staan uit. Aanzetten kan altijd.", "The meters are off. You can switch them on any time."),
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
    @Describe("True: it worked this week. False: it didn't work yet.")
    val worked: Boolean,
)

@Serializable
data class CheckHouseRuleResult(val check: RuleCheck, override val summary: String) : ToolResult

/** Did an agreement work this week? For the household: it never says who. */
class CheckHouseRuleTool(private val meeting: MeetingRepository, private val board: BoardRepository) : Tool<CheckHouseRuleArgs, CheckHouseRuleResult> {
    override val name = NAME
    override val description =
        "In the weekoverleg: say whether a house rule worked this week (for the household, never who). A rule that " +
            "doesn't work two meetings in a row comes back as a working point: maybe the agreement needs changing."
    override val argsSerializer = CheckHouseRuleArgs.serializer()
    override val resultSerializer = CheckHouseRuleResult.serializer()

    override suspend fun run(args: CheckHouseRuleArgs, ctx: ToolContext): CheckHouseRuleResult {
        val rules = board.allItems().filter { it.kind == BoardKind.RULE && it.isOpen }
        val key = args.rule?.let { normalizeName(it) }
        val rule = rules.firstOrNull { it.id == args.ruleId }
            ?: key?.let { k -> rules.firstOrNull { normalizeName(it.text) == k } ?: rules.firstOrNull { normalizeName(it.text).contains(k) } }
            ?: throw ToolException(ctx.t("Welke huisregel?", "Which house rule?"))
        val check = meeting.setRuleCheck(rule.id, args.worked)
        val summary = if (args.worked) ctx.t("Fijn: “${rule.text}” liep.", "Nice: “${rule.text}” worked.")
        else ctx.t("“${rule.text}” liep nog niet. Misschien mag de huisregel eenvoudiger.", "“${rule.text}” didn't work yet. Maybe the agreement can be simpler.")
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
    override val description = "Mark what Luister mee heard as sorted, so it isn't proposed again (tablet only)."
    override val exposeOverMcp = false
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
 * Ends the meeting and writes its summary (kept for good): what went well, the two meters (with Hearth's
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
        val meters = if (metersOn(settings)) meeting.currentMeters() else emptyList()
        val checks = meeting.currentRuleChecks()
        val checkedBefore = last?.let { meeting.ruleChecksOf(it.id) }.orEmpty()
        val attention = needingAttention(backlog, ctx.today)
        val habits = backlog.openItems().filter { it.isHabit && it.ease != null }
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
            meters.sortedBy { it.meter }.forEach { m ->
                val guess = m.suggested
                val note = when {
                    !m.agreed -> ctx.t(" (geen akkoord: neutraal)", " (no agreement: neutral)")
                    guess != null && guess != m.value -> ctx.t(" (Hearth dacht: ${meterWord(m.meter, guess, ctx)})", " (Hearth thought: ${meterWord(m.meter, guess, ctx)})")
                    else -> ""
                }
                add(meterTitle(m.meter, ctx) + ": " + meterWord(m.meter, m.value, ctx) + note)
            }
            val notYet = checks.filter { !it.worked }.mapNotNull { c -> boardItems.firstOrNull { it.id == c.ruleId } }
            if (notYet.isNotEmpty()) add(ctx.t("Huisregels die nog niet liepen: ", "House rules that didn't work yet: ") + notYet.joinToString("; ") { it.text })
            // Two meetings in a row: the agreement itself becomes a working point (maybe it asks too much).
            if (!args.preview) notYet.filter { r -> checkedBefore.any { it.ruleId == r.id && !it.worked } }.forEach { r ->
                board.add(BoardKind.FRICTION, ctx.t("Huisregel loopt nog niet: ${r.text}", "House rule not working yet: ${r.text}"))
            }
            if (habits.isNotEmpty()) {
                add(ctx.t("Gewoontes: ", "Habits: ") + habits.sortedBy { it.ease }.joinToString("; ") { h ->
                    "${h.text}: ${easeWord(h.ease!!, ctx)}" + (h.smallStep?.let { ctx.t(" (begin klein: $it)", " (start small: $it)") } ?: "")
                })
            }
            if (attention.isNotEmpty()) add(ctx.t("Vraagt aandacht: ", "Asks for attention: ") + attention.joinToString("; ") { (item, ease) -> attentionText(item, ease, ctx) })
            if (rules.isNotEmpty()) add(ctx.t("Nieuwe huisregels: ", "New house rules: ") + rules.joinToString("; ") { it.text })
            if (decided.isNotEmpty()) {
                add(ctx.t("Besproken: ", "Talked through: ") + decided.joinToString("; ") { d ->
                    d.text + " → " + when (d.status) {
                        BoardStatus.BECAME_RULE -> ctx.t("huisregel", "house rule")
                        BoardStatus.BECAME_CHANGE -> ctx.t("we veranderen het", "we change it")
                        else -> ctx.t("losgelaten", "let go")
                    }
                })
            }
            if (newPoints.isNotEmpty()) add(ctx.t("Nieuwe werkpunten: ", "New working points: ") + newPoints.joinToString("; ") { it.text })
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
