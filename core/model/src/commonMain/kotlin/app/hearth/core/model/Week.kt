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
 * One thing said during Luister mee (what the recognizer heard between two pauses). The lines of the
 * last meeting stay readable until the next meeting ends; its summary stays for good.
 */
@Serializable
data class TranscriptLine(val id: String, val text: String, val huddleId: String?, val createdAt: Long, val sorted: Boolean = false)

/**
 * The two meters of the weekoverleg, agreed together for the household (never per person, never about
 * someone): how the week ran, and what rubbed. Hearth suggests, the family decides; no agreement = 3.
 */
enum class Meter {
    /** Hoe liep het? 1 moeizaam … 5 heel goed. */
    FLOW,
    /** Wat schuurde? 1 niets … 5 veel. */
    STRAIN,
}

/** A meter as the family set it, with what Hearth had suggested ([agreed] false: no consensus, so neutral). */
@Serializable
data class MeterValue(val meter: Meter, val value: Int, val suggested: Int?, val agreed: Boolean, val huddleId: String?)

/** Whether a house rule worked this week. Household-level: it never says who. */
@Serializable
data class RuleCheck(val ruleId: String, val worked: Boolean, val huddleId: String?)

/** The value used when the family doesn't agree. */
const val NEUTRAL_METER = 3

/**
 * Hoe liep het: starts at "goed" (4) because the small things and good habits are always forgotten; "heel goed"
 * with plenty done or celebrated and no habit asking for attention; never suggested below 3.
 */
fun flowSuggestion(done: Int, wins: Int, needingAttention: Int): Int = when {
    done + wins * 2 >= 12 && needingAttention == 0 -> 5
    done + wins == 0 && needingAttention > 1 -> 3
    else -> 4
}

/** Wat schuurde: from frictions noted this week and agreements that didn't work yet (at most two of each count). */
fun strainSuggestion(newFrictions: Int, rulesNotYet: Int): Int =
    (1 + minOf(newFrictions, 2) + minOf(rulesNotYet, 2)).coerceIn(1, 5)

/** "Hoe liep het" / "Wat schuurde". */
fun Meter.title(lang: Lang): String = when (this) {
    Meter.FLOW -> if (lang == Lang.NL) "Hoe liep het" else "How it ran"
    Meter.STRAIN -> if (lang == Lang.NL) "Wat schuurde" else "What rubbed"
}

/** A meter's value in words; kind on purpose (no "bad", no "fail"). */
fun Meter.word(value: Int, lang: Lang): String {
    val nl = lang == Lang.NL
    return when (this) {
        Meter.FLOW -> when (value) {
            1 -> if (nl) "moeizaam" else "hard going"
            2 -> if (nl) "wat stroef" else "a bit stiff"
            3 -> if (nl) "oké" else "okay"
            4 -> if (nl) "goed" else "good"
            else -> if (nl) "heel goed" else "very good"
        }
        Meter.STRAIN -> when (value) {
            1 -> if (nl) "niets" else "nothing"
            2 -> if (nl) "een beetje" else "a little"
            3 -> if (nl) "wat" else "some"
            4 -> if (nl) "best veel" else "quite a lot"
            else -> if (nl) "veel" else "a lot"
        }
    }
}
