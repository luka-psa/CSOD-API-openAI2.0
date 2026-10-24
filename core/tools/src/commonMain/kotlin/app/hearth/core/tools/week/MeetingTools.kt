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
import app.hearth.core.model.WeekFeeling
import app.hearth.core.model.Win
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
import kotlin.math.roundToInt
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

// ---------- set_week_feeling ----------

@Serializable
data class WeekFeelingArgs(
    @Describe("How heavy the week felt, 1 (light) to 5 (heavy).")
    val value: Int,
    @Describe("Whose feeling (name). Leave empty for the household as a whole.")
    val member: String? = null,
)

@Serializable
data class WeekFeelingResult(val feeling: WeekFeeling, override val summary: String) : ToolResult

class SetWeekFeelingTool(private val meeting: MeetingRepository, private val board: BoardRepository) : Tool<WeekFeelingArgs, WeekFeelingResult> {
    override val name = NAME
    override val description =
        "Record how heavy the week felt (1 light – 5 heavy), per person or for the household. Used in the weekoverleg " +
            "to see whether weeks get lighter; a heavy week puts frictions first."
    override val argsSerializer = WeekFeelingArgs.serializer()
    override val resultSerializer = WeekFeelingResult.serializer()

    override suspend fun run(args: WeekFeelingArgs, ctx: ToolContext): WeekFeelingResult {
        if (args.value !in 1..5) throw ToolException(ctx.t("Kies iets van 1 (licht) tot 5 (zwaar).", "Pick 1 (light) to 5 (heavy)."))
        val member = board.member(args.member, ctx)
        val feeling = meeting.setFeeling(args.value, member?.id)
        val who = member?.name ?: ctx.t("Jullie week", "Your week")
        val words = feelingWord(args.value, ctx)
        return WeekFeelingResult(feeling, ctx.t("$who: $words. Dank je om het te zeggen.", "$who: $words. Thanks for saying."))
    }

    companion object {
        const val NAME = "set_week_feeling"
    }
}

internal fun feelingWord(value: Int, ctx: ToolContext) = when (value) {
    1 -> ctx.t("licht", "light")
    2 -> ctx.t("vrij licht", "fairly light")
    3 -> ctx.t("gewoon", "ordinary")
    4 -> ctx.t("vrij zwaar", "fairly heavy")
    else -> ctx.t("zwaar", "heavy")
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
data class HuddleResult(val huddle: Huddle, override val summary: String) : ToolResult

/**
 * Ends the meeting and writes its summary (kept for good): what went well, how heavy the week felt
 * (compared kindly with last time), what was agreed, the working points, what got a day, and the meals.
 */
class FinishWeekoverlegTool(
    private val week: WeekRepository,
    private val meeting: MeetingRepository,
    private val backlog: BacklogRepository,
    private val board: BoardRepository,
) : Tool<AgendaArgs, HuddleResult> {
    override val name = NAME
    override val description = "Mark this week's meeting as held and write its summary, so next week's look back starts from today."
    override val argsSerializer = AgendaArgs.serializer()
    override val resultSerializer = HuddleResult.serializer()

    @OptIn(ExperimentalTime::class)
    override suspend fun run(args: AgendaArgs, ctx: ToolContext): HuddleResult {
        val last = week.last()
        val since = last?.createdAt ?: ctx.today.plusDays(-7).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val names = board.allMembers().associate { it.id to it.name }
        val wins = meeting.currentWins()
        val feelings = meeting.currentFeelings()
        val before = last?.let { meeting.feelingsOf(it.id) }.orEmpty()
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
                    done.size.takeIf { it > 0 }?.let { ctx.t("$it dingen gedaan", "$it things done") },
                )).joinToString("; "))
            }
            if (feelings.isNotEmpty()) {
                val each = feelings.joinToString(", ") { f -> listOfNotNull(f.memberId?.let(names::get), feelingWord(f.value, ctx)).joinToString(" ") }
                add(ctx.t("Hoe zwaar: ", "How heavy: ") + each + trend(feelings, before, ctx))
            }
            if (rules.isNotEmpty()) add(ctx.t("Afgesproken: ", "Agreed: ") + rules.joinToString("; ") { it.text })
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
            val free = planned.filter { it.section == Section.PROTECTED_TIME }
            val tasks = planned - free.toSet()
            if (tasks.isNotEmpty()) add(ctx.t("Gepland: ", "Planned: ") + tasks.joinToString("; ") { "${it.text} (${dayLabel(it.localDate!!, ctx.today, ctx.lang)})" })
            if (free.isNotEmpty()) add(ctx.t("Vrije tijd: ", "Free time: ") + free.joinToString("; ") { "${it.text} (${dayLabel(it.localDate!!, ctx.today, ctx.lang)})" })
            if (meals.isNotEmpty()) add(ctx.t("Op het menu: ", "On the menu: ") + meals.joinToString(", ") { it.dish })
        }
        val huddle = meeting.close(ctx.today.toString(), lines.joinToString("\n"))
        return HuddleResult(huddle, ctx.t("Mooi weekoverleg! Jullie zijn klaar voor de week.", "Nice weekly meeting! You're set for the week."))
    }

    /** "lichter dan vorige keer" when it is; never a word when it got heavier, the frictions speak for that. */
    private fun trend(now: List<WeekFeeling>, before: List<WeekFeeling>, ctx: ToolContext): String {
        if (before.isEmpty()) return ""
        val diff = now.map { it.value }.average() - before.map { it.value }.average()
        return when {
            diff <= -0.5 -> ctx.t(" (lichter dan vorige keer)", " (lighter than last time)")
            diff.roundToInt() == 0 -> ctx.t(" (zoals vorige keer)", " (like last time)")
            else -> ""
        }
    }

    companion object {
        const val NAME = "finish_weekoverleg"
    }
}
