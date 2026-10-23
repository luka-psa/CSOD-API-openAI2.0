package app.hearth.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// The Weekoverleg: a short guided meeting, once a week.

/** One weekly meeting that was held. */
@Serializable
data class Huddle(val id: String, val heldOn: String, val createdAt: Long) {
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
