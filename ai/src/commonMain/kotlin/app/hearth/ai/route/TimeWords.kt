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

    // A day anywhere in a sentence, for talk: "zaterdag doen we niks", "we gaan volgende dinsdag naar de tandarts".
    // "Deze week" has no day yet: it goes to Volgende.
    private val anywhere = Regex(
        "(?:^|\\s)(?:(tegen|voor|by|before)\\s+)?(?:(?:op|on|next|volgende|deze|this)\\s+)?" +
            "(vandaag|today|morgen|tomorrow|${weekdays.keys.joinToString("|")}|dit\\s+weekend|this\\s+weekend|weekend|deze\\s+week|this\\s+week|in\\s+de\\s+loop\\s+van\\s+de\\s+week)(?=[\\s,.!?]|$)",
        RegexOption.IGNORE_CASE,
    )

    private val DANGLING = Regex("\\s+(?:is|zijn|op|om|gaat|gaan|staat|are|on|at)$", RegexOption.IGNORE_CASE)

    private val months = linkedMapOf(
        "januari" to 1, "februari" to 2, "maart" to 3, "april" to 4, "mei" to 5, "juni" to 6, "juli" to 7, "augustus" to 8,
        "september" to 9, "oktober" to 10, "november" to 11, "december" to 12,
        "january" to 1, "february" to 2, "march" to 3, "may" to 5, "june" to 6, "july" to 7, "august" to 8, "october" to 10,
        "jan" to 1, "feb" to 2, "mrt" to 3, "apr" to 4, "jun" to 6, "jul" to 7, "aug" to 8, "sept" to 9, "sep" to 9, "okt" to 10,
        "oct" to 10, "nov" to 11, "dec" to 12,
    )
    private val month = months.keys.sortedByDescending { it.length }.joinToString("|")

    // "van 3 tot 7 november", "3-7 nov", "from 3 to 7 November"
    private val range = Regex(
        "(?:^|\\s)(?:(?:van|from)\\s+)?(\\d{1,2})(?:\\s+($month))?\\s*(?:tot en met|t/m|tem|tot|to|until|till|-|–)\\s*(\\d{1,2})\\s+($month)\\.?(?=[\\s,.!?]|$)",
        RegexOption.IGNORE_CASE,
    )

    // "op 3 november", "12/12", "tegen 1 december", "on May 4"
    private val absolute = Regex(
        "(?:^|\\s)(?:(tegen|voor|by|before)\\s+)?(?:(?:op|on)\\s+)?" +
            "(?:(\\d{1,2})\\s+($month)\\.?|(\\d{1,2})[/.](\\d{1,2})|($month)\\s+(\\d{1,2}))(?=[\\s,.!?]|$)",
        RegexOption.IGNORE_CASE,
    )

    // "volgende week", "volgende maand", "over 3 weken", "next month", "in 2 weeks"
    private val relative = Regex(
        "(?:^|\\s)(?:(volgende|next)\\s+(week|maand|month)|(?:over|in)\\s+(\\d{1,2}|twee|drie|vier|two|three|four)\\s+(dagen|weken|maanden|days|weeks|months))(?=[\\s,.!?]|$)",
        RegexOption.IGNORE_CASE,
    )

    private fun cut(text: String, range: IntRange): String =
        (text.substring(0, range.first) + " " + text.substring(range.last + 1)).replace(Regex("\\s+"), " ").trim().trim(',').trim()
            .replace(DANGLING, "").trim()

    /** A day later than this week: a date, a stretch of days, or "volgende maand". Past dates mean next year. */
    private fun calendarDay(text: String, today: LocalDate): TimeHint? {
        range.find(text)?.let { m ->
            val endMonth = months.getValue(m.groupValues[4].lowercase())
            val startMonth = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { months.getValue(it.lowercase()) } ?: endMonth
            val start = dateOf(m.groupValues[1].toInt(), startMonth, today) ?: return@let
            val end = dateOf(m.groupValues[3].toInt(), endMonth, today) ?: return@let
            val rest = cut(text, m.range).ifEmpty { return@let }
            return TimeHint("$rest (${start.day}/${start.month.ordinal + 1}–${end.day}/${end.month.ordinal + 1})", When.DAY, start, false)
        }
        absolute.find(text)?.let { m ->
            val g = m.groupValues
            val date = when {
                g[2].isNotEmpty() -> dateOf(g[2].toInt(), months.getValue(g[3].lowercase()), today)
                g[4].isNotEmpty() -> g[5].toInt().takeIf { it in 1..12 }?.let { dateOf(g[4].toInt(), it, today) }
                else -> dateOf(g[7].toInt(), months.getValue(g[6].lowercase()), today)
            } ?: return@let
            val rest = cut(text, m.range).ifEmpty { return@let }
            return TimeHint(rest, When.DAY, date, g[1].isNotEmpty())
        }
        relative.find(text)?.let { m ->
            val g = m.groupValues
            val date = when {
                g[2].isNotEmpty() && g[2].lowercase() == "week" -> today.plusDays(7 - today.dayOfWeek.ordinal)
                g[2].isNotEmpty() -> LocalDate(if (today.month.ordinal == 11) today.year + 1 else today.year, today.month.ordinal % 12 + 2 - (if (today.month.ordinal == 11) 12 else 0), 1)
                else -> {
                    val n = g[3].toIntOrNull() ?: mapOf("twee" to 2, "drie" to 3, "vier" to 4, "two" to 2, "three" to 3, "four" to 4).getValue(g[3].lowercase())
                    today.plusDays(n * when (g[4].lowercase()) { "dagen", "days" -> 1; "weken", "weeks" -> 7; else -> 30 })
                }
            }
            val rest = cut(text, m.range).ifEmpty { return@let }
            return TimeHint(rest, When.DAY, date, false)
        }
        return null
    }

    private fun dateOf(day: Int, month: Int, today: LocalDate): LocalDate? {
        val thisYear = runCatching { LocalDate(today.year, month, day) }.getOrNull() ?: return null
        return if (thisYear < today) runCatching { LocalDate(today.year + 1, month, day) }.getOrNull() else thisYear
    }

    /** Like [extract], but the day may stand anywhere (and may be a date further ahead); it is taken out of the text. */
    fun extractAnywhere(text: String, today: LocalDate): TimeHint? {
        calendarDay(text, today)?.let { return it }
        extract(text, today)?.let { return it.copy(text = it.text.replace(DANGLING, "").trim().ifEmpty { it.text }) }
        val m = anywhere.find(text) ?: return null
        val rest = (text.substring(0, m.range.first) + " " + text.substring(m.range.last + 1))
            .replace(Regex("\\s+"), " ").trim().trim(',').trim()
            // "de tandarts is volgende dinsdag" -> "de tandarts", not "de tandarts is".
            .replace(DANGLING, "").trim().ifEmpty { return null }
        val finishBy = m.groupValues[1].isNotEmpty()
        val word = m.groupValues[2].lowercase().replace(Regex("\\s+"), " ")
        return when {
            word == "vandaag" || word == "today" -> TimeHint(rest, When.TODAY, null, finishBy)
            word == "morgen" || word == "tomorrow" -> TimeHint(rest, When.TOMORROW, null, finishBy)
            word.endsWith(" week") -> TimeHint(rest, When.NEXT, null, false)
            word.endsWith("weekend") -> {
                val delta = (5 - today.dayOfWeek.ordinal + 7) % 7
                TimeHint(rest, When.DAY, today.plusDays(delta), finishBy)
            }
            else -> {
                val delta = (weekdays.getValue(word) - today.dayOfWeek.ordinal + 7) % 7
                TimeHint(rest, When.DAY, today.plusDays(if (delta == 0) 7 else delta), finishBy)
            }
        }
    }
}
