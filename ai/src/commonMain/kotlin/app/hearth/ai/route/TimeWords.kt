package app.hearth.ai.route

import app.hearth.core.model.plusDays
import app.hearth.core.tools.backlog.When
import kotlinx.datetime.LocalDate

/** A plan read from the end of a sentence: "tandarts morgen", "belastingbrief tegen vrijdag". */
data class TimeHint(val text: String, val whenValue: When, val date: LocalDate?, val finishBy: Boolean)

object TimeWords {
    private val weekdays = mapOf(
        "maandag" to 0, "dinsdag" to 1, "woensdag" to 2, "donderdag" to 3, "vrijdag" to 4, "zaterdag" to 5, "zondag" to 6,
        "monday" to 0, "tuesday" to 1, "wednesday" to 2, "thursday" to 3, "friday" to 4, "saturday" to 5, "sunday" to 6,
    )

    // <thing> [tegen|by] [op|on|next|volgende] <vandaag|morgen|later|weekday>
    private val tail = Regex(
        "^(.*?)[\\s,]+(?:(tegen|voor|by|before)\\s+)?(?:(?:op|on|next|volgende)\\s+)?" +
            "(vandaag|today|morgen|tomorrow|later|${weekdays.keys.joinToString("|")})[.!]?$",
        RegexOption.IGNORE_CASE,
    )

    fun extract(text: String, today: LocalDate): TimeHint? {
        val m = tail.matchEntire(text.trim()) ?: return null
        val rest = m.groupValues[1].trim().ifEmpty { return null }
        val finishBy = m.groupValues[2].isNotEmpty()
        return when (val word = m.groupValues[3].lowercase()) {
            "vandaag", "today" -> TimeHint(rest, When.TODAY, null, finishBy)
            "morgen", "tomorrow" -> TimeHint(rest, When.TOMORROW, null, finishBy)
            "later" -> TimeHint(rest, When.LATER, null, false)
            else -> {
                // The next such weekday; "vrijdag" on a Friday means next week's.
                val delta = (weekdays.getValue(word) - today.dayOfWeek.ordinal + 7) % 7
                TimeHint(rest, When.DAY, today.plusDays(if (delta == 0) 7 else delta), finishBy)
            }
        }
    }
}
