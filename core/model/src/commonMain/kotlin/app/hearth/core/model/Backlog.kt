package app.hearth.core.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** How an open item is planned. Unplanned items are the "inbox" part of the backlog. */
enum class Plan {
    /** Just captured; nobody decided when yet. */
    UNPLANNED,
    /** Next up: do this soon, no fixed day. */
    NEXT,
    /** On a specific day (see [BacklogItem.date]). */
    DAY,
    /** Consciously parked for later. */
    LATER,
}

/**
 * Anything that still needs doing, from any section, until that section has its own home
 * (appointments, projects, chores...). Nothing a person says is ever lost: it lands here.
 */
@Serializable
data class BacklogItem(
    val id: String,
    val text: String,
    val section: Section,
    val plan: Plan = Plan.UNPLANNED,
    /** ISO date (yyyy-MM-dd) when [plan] is DAY. */
    val date: String? = null,
    /** True: "must be finished by [date]"; false: "do it on [date]". */
    val finishBy: Boolean = false,
    val done: Boolean = false,
    val createdAt: Long,
    val addedById: String? = null,
) {
    val localDate: LocalDate? get() = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

@OptIn(ExperimentalTime::class)
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))

/** "vandaag", "morgen", "vrijdag 24/10" (or English). Never "overdue": past days read "sinds dinsdag". */
fun dayLabel(date: LocalDate, today: LocalDate, lang: Lang): String {
    val diff = date.toEpochDays() - today.toEpochDays()
    val name = weekdayName(date.dayOfWeek, lang)
    return when {
        diff == 0L -> if (lang == Lang.NL) "vandaag" else "today"
        diff == 1L -> if (lang == Lang.NL) "morgen" else "tomorrow"
        diff == -1L -> if (lang == Lang.NL) "sinds gisteren" else "since yesterday"
        diff < 0 -> if (lang == Lang.NL) "sinds $name" else "since $name"
        diff < 7 -> name
        else -> "$name ${date.day}/${date.month.ordinal + 1}"
    }
}

fun weekdayName(day: DayOfWeek, lang: Lang, short: Boolean = false): String {
    val nl = listOf("maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag")
    val en = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val name = (if (lang == Lang.NL) nl else en)[day.ordinal]
    return if (short) name.take(if (lang == Lang.NL) 2 else 3) else name
}
