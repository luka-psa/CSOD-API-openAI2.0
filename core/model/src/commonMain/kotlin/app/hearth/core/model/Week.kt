package app.hearth.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// The Weekoverleg: a short guided meeting, once a week.

/** One weekly meeting that was held, with the summary written when it ended (kept). */
@Serializable
data class Huddle(val id: String, val heldOn: String, val createdAt: Long, val summary: String = "") {
    val date: LocalDate? get() = runCatching { LocalDate.parse(heldOn) }.getOrNull()
}

/**
 * Oudjes: what has been open the longest without a plan. A few are picked each week, so a long list
 * shrinks bit by bit instead of feeling endless.
 */
fun oudjes(items: List<BacklogItem>, count: Int = 3, skip: Int = 0): List<BacklogItem> =
    items.filter { !it.done && !it.isStep && !it.isRoutine() && !it.waiting && (it.plan == Plan.UNPLANNED || it.plan == Plan.LATER) }
        .sortedBy { it.createdAt }
        .drop(skip)
        .take(count)

/** Deadlines ("klaar tegen") in the coming week, earlier ones included. */
fun deadlinesThisWeek(items: List<BacklogItem>, today: LocalDate): List<BacklogItem> =
    items.filter { !it.waiting && it.plan == Plan.DAY && it.finishBy && (it.localDate?.let { d -> d < today.plusDays(7) } ?: false) }
        .sortedBy { it.date }

/** Whether it's a good moment to suggest the weekly meeting: none in the last 6 days. */
fun huddleDue(last: Huddle?, today: LocalDate): Boolean {
    val held = last?.date ?: return true
    return today.toEpochDays() - held.toEpochDays() >= 6
}

/** Wat ging goed: a win someone named. Belongs to the next meeting until that one ends. */
@Serializable
data class Win(val id: String, val text: String, val memberId: String?, val huddleId: String?, val createdAt: Long)

/**
 * How heavy the week felt for someone (1 = light, 5 = heavy), asked in the look back. [memberId] null:
 * the household as a whole. Never shown as a score to beat; only "lighter than last time".
 */
@Serializable
data class WeekFeeling(val id: String, val value: Int, val memberId: String?, val huddleId: String?, val createdAt: Long)

/**
 * One thing said during Luister mee (what the recognizer heard between two pauses). The lines of the
 * last meeting stay readable until the next meeting ends; its summary stays for good.
 */
@Serializable
data class TranscriptLine(val id: String, val text: String, val huddleId: String?, val createdAt: Long, val sorted: Boolean = false)
