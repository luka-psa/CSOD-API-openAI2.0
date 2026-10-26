package app.hearth.core.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A rhythm on fixed days: "elke donderdag", "ma, wo en vr", "de 1ste van de maand", "de eerste zaterdag van de
 * maand", with a time only when it really is fixed ("elke donderdag om 20:00", zwemles). A routine without one keeps
 * its moment of the day and "roughly every N days"; most never need a schedule.
 */
@Serializable
data class Schedule(
    /** Weekly on these days; with [monthWeek] the one weekday of the month it falls on. */
    val weekdays: List<DayOfWeek> = emptyList(),
    /** Monthly on this date (1–31; a short month uses its last day). */
    val monthDay: Int? = null,
    /** Monthly on the nth [weekdays] of the month: 1–4, or -1 for the last one. */
    val monthWeek: Int? = null,
    /** "20:00": only for things at a fixed time. */
    val time: String? = null,
) {
    val monthly: Boolean get() = monthDay != null || monthWeek != null

    /** Has at least one day. */
    val isSet: Boolean get() = monthDay != null || weekdays.isNotEmpty()

    /** Roughly how often it comes back, for what still works with days (the colours, "minder vaak"). */
    val intervalDays: Int get() = when {
        monthly -> 30
        weekdays.size >= 7 -> 1
        else -> 7
    }

    fun occursOn(day: LocalDate): Boolean = when {
        monthDay != null -> day.day == minOf(monthDay, daysInMonth(day))
        monthWeek != null -> weekdays.firstOrNull() == day.dayOfWeek &&
            (if (monthWeek < 0) day.plusDays(7).month != day.month else (day.day - 1) / 7 + 1 == monthWeek)
        else -> day.dayOfWeek in weekdays
    }

    /** Its most recent day up to and including [today]. */
    fun lastOn(today: LocalDate): LocalDate? = (0..62).map { today.plusDays(-it) }.firstOrNull(::occursOn)

    /** Its next day after [today]. */
    fun nextAfter(today: LocalDate): LocalDate? = (1..62).map { today.plusDays(it) }.firstOrNull(::occursOn)

    /** "elke donderdag om 20:00", "de eerste zaterdag van de maand", "every Mon, Wed and Fri". */
    fun label(lang: Lang): String {
        val nl = lang == Lang.NL
        val days = when {
            monthDay != null -> if (nl) "de ${ordinalNl(monthDay)} van de maand" else "on the ${ordinalEn(monthDay)} of the month"
            monthWeek != null -> {
                val wd = weekdays.firstOrNull()?.let { weekdayName(it, lang) } ?: ""
                val nth = if (nl) NTH_NL[monthWeek] else NTH_EN[monthWeek]
                if (nl) "de $nth $wd van de maand" else "the $nth $wd of the month"
            }
            weekdays.size >= 7 -> if (nl) "elke dag" else "every day"
            weekdays.size == 1 -> (if (nl) "elke " else "every ") + weekdayName(weekdays.first(), lang)
            else -> {
                val names = weekdays.sorted().map { weekdayName(it, lang, short = true) }
                (if (nl) "elke " else "every ") + names.dropLast(1).joinToString(", ") + (if (nl) " en " else " and ") + names.last()
            }
        }
        return days + (time?.let { if (nl) " om $it" else " at $it" } ?: "")
    }

    fun encode(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        private val NTH_NL = mapOf(1 to "eerste", 2 to "tweede", 3 to "derde", 4 to "vierde", -1 to "laatste")
        private val NTH_EN = mapOf(1 to "first", 2 to "second", 3 to "third", 4 to "fourth", -1 to "last")

        fun decode(text: String?): Schedule? =
            text?.let { runCatching { JSON.decodeFromString(serializer(), it) }.getOrNull() }?.takeIf { it.isSet }

        /** "20:00" from "20", "8u", "20.30", "8:05"; null when it isn't a time. */
        fun cleanTime(text: String?): String? {
            val m = Regex("^\\s*(\\d{1,2})(?:\\s*[:.uh]\\s*(\\d{2})?)?\\s*$", RegexOption.IGNORE_CASE).find(text ?: return null) ?: return null
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (h > 23 || min > 59) return null
            return "${h.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}"
        }

        private fun ordinalNl(n: Int) = "$n" + if (n == 1 || n == 8 || n >= 20) "ste" else "de"

        private fun ordinalEn(n: Int) = "$n" + when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }

        private fun daysInMonth(day: LocalDate): Int {
            val first = LocalDate(day.year, day.month, 1)
            return (first.plus(DatePeriod(months = 1)).toEpochDays() - first.toEpochDays()).toInt()
        }
    }
}

/** "elke donderdag om 20:00" for a routine on fixed days, else "elke week", "af en toe". */
fun rhythmLabel(item: BacklogItem, lang: Lang): String = item.schedule?.label(lang) ?: intervalLabel(item.intervalDays, lang)
