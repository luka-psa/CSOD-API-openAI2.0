package app.hearth.ai.route

import app.hearth.core.model.Anchor
import app.hearth.core.model.Schedule
import kotlinx.datetime.DayOfWeek

/**
 * Rhythm, fixed days, urgency and a moment of the day read from a sentence: "lakens verversen om de 2 weken",
 * "vuilbak buiten elke donderdag", "zwemles elke woensdag om 14 uur", "dringend: verzekering bellen",
 * "'s avonds de tassen klaarzetten".
 */
data class TaskHint(val text: String, val intervalDays: Int?, val urgent: Boolean, val anchor: Anchor? = null, val schedule: Schedule? = null)

object TaskWords {
    private val dayNames: Map<String, DayOfWeek> = buildMap {
        val full = listOf(
            listOf("maandag", "ma", "monday", "mon"), listOf("dinsdag", "di", "tuesday", "tue", "tues"),
            listOf("woensdag", "wo", "wednesday", "wed"), listOf("donderdag", "do", "thursday", "thu", "thur", "thurs"),
            listOf("vrijdag", "vr", "friday", "fri"), listOf("zaterdag", "za", "saturday", "sat"), listOf("zondag", "zo", "sunday", "sun"),
        )
        full.forEachIndexed { i, names -> names.forEach { put(it, DayOfWeek.entries[i]) } }
    }
    private val dayWord = dayNames.keys.sortedByDescending { it.length }.joinToString("|")
    private const val PART = "avond|ochtend|voormiddag|namiddag|middag|morgen|evening|morning|afternoon|night"
    // "elke donderdag", "iedere ma, wo en vr", "elke week op dinsdag", "every Monday and Thursday", "elke donderdagavond",
    // "'s maandags", "on Mondays". A plain "op maandag" / "on Monday" is one day, not a rhythm.
    private val weekly = Regex(
        "(?<![\\p{L}])(?:(?:(?:elke|iedere) week op|elke|iedere|every|each)\\s+|['’]s\\s+(?=\\p{L}+dags)|on\\s+(?=\\p{L}+days))" +
            "((?:$dayWord)s?(?:\\s?(?:$PART))?(?:\\s*(?:,|en|and|&)\\s*(?:$dayWord)s?(?:\\s?(?:$PART))?)*)(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )
    private val partMoment = mapOf(
        "avond" to "'s avonds", "evening" to "'s avonds", "night" to "'s avonds",
        "ochtend" to "'s ochtends", "voormiddag" to "'s ochtends", "morgen" to "'s ochtends", "morning" to "'s ochtends",
        "middag" to "'s middags", "namiddag" to "'s middags", "afternoon" to "'s middags",
    )
    private val nth = mapOf("eerste" to 1, "1ste" to 1, "first" to 1, "1st" to 1, "tweede" to 2, "2de" to 2, "second" to 2, "2nd" to 2,
        "derde" to 3, "3de" to 3, "third" to 3, "3rd" to 3, "vierde" to 4, "4de" to 4, "fourth" to 4, "4th" to 4, "laatste" to -1, "last" to -1)
    private const val MONTH = "(?:van|of)\\s+(?:de|elke|iedere|the|every|each)\\s+(?:maand|month)"
    // "de eerste zaterdag van de maand", "every last Friday of the month".
    private val monthWeekday = Regex(
        "(?<![\\p{L}])(?:(?:op\\s+)?(?:elke|iedere|de|every|the|on the)\\s+)?(${nth.keys.joinToString("|")})\\s+($dayWord)\\s+$MONTH(?![\\p{L}])",
        RegexOption.IGNORE_CASE,
    )
    // "de 1ste van de maand", "elke 15de van de maand", "the 1st of every month", "elke maand op de 15de", "de eerste van de maand".
    private val monthDate = Regex(
        "(?<![\\p{L}\\d])(?:(?:op\\s+)?(?:elke|iedere|de|every|the|on the)\\s+)?(?:(\\d{1,2})(?:ste|de|e|st|nd|rd|th)?|eerste|first)\\s+$MONTH(?![\\p{L}])" +
            "|(?:elke|iedere) maand op de (\\d{1,2})(?:ste|de|e)?|every month on the (\\d{1,2})(?:st|nd|rd|th)?",
        RegexOption.IGNORE_CASE,
    )
    private val daily = Regex("(?<![\\p{L}])(?:elke|iedere) dag|dagelijks|every day|daily(?![\\p{L}])", RegexOption.IGNORE_CASE)
    // "om 20 uur", "om 8u30", "om 14:00", "at 8pm", "at 20:00".
    private val clock = Regex("(?<![\\p{L}])(?:om|at)\\s+(\\d{1,2})(?:\\s*[:.uh]\\s*(\\d{2}))?\\s*(?:uur|u|h)?\\s*(am|pm)?(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)
    private val eveningWords = Regex("['’]?s avonds|avond|['’]?s middags|namiddag|evening|night|afternoon", RegexOption.IGNORE_CASE)

    private val fixed: List<Pair<Regex, Int>> = listOf(
        "(?:elke|iedere) dag|dagelijks|every day|daily" to 1,
        "(?:elke|iedere) week|wekelijks|every week|weekly" to 7,
        "tweewekelijks|every other week" to 14,
        "(?:elke|iedere) maand|maandelijks|every month|monthly" to 30,
        "(?:elk|ieder) seizoen|every season" to 90,
        "(?:elk|ieder) jaar|jaarlijks|every year|yearly" to 365,
    ).map { (words, days) -> Regex("\\b(?:$words)\\b", RegexOption.IGNORE_CASE) to days }

    private val counted = Regex("\\b(?:om de|elke|iedere|every) (\\d{1,3}) (dag|dagen|week|weken|maand|maanden|days?|weeks?|months?)\\b", RegexOption.IGNORE_CASE)
    // Bedtime and dinner before evening: "elke avond voor het slapen" is the bedtime routine.
    private val anchors: List<Pair<Regex, Anchor>> = listOf(
        "voor het slapen(?:gaan)?|bij het slapengaan|voor bedtijd|before bed(?:time)?|at bedtime" to Anchor.BEDTIME,
        "(?:na|voor|bij|rond) het (?:avond)?eten|na de maaltijd|(?:after|before) (?:dinner|supper)" to Anchor.DINNER,
        "['’]?s ochtends|(?:elke|iedere) ochtend|in de ochtend|(?:bij|na) het opstaan|every morning|in the morning|after (?:getting|waking) up" to Anchor.MORNING,
        "['’]?s middags|(?:elke|iedere) middag|na (?:de )?school|every afternoon|in the afternoon|after school" to Anchor.AFTERNOON,
        "['’]?s avonds|(?:elke|iedere) avond|in de avond|every (?:evening|night)|in the evening" to Anchor.EVENING,
    ).map { (words, anchor) -> Regex("(?<![\\p{L}])(?:$words)(?![\\p{L}])", RegexOption.IGNORE_CASE) to anchor }

    private val urgentWords = Regex("\\b(?:dringend|urgent|asap|zo snel mogelijk)\\b:?", RegexOption.IGNORE_CASE)

    /** Fixed days (and a time, only with them) said in [text], and [text] without those words. */
    private fun schedule(text: String): Pair<Schedule, String>? {
        var rest = text
        var schedule: Schedule? = null
        monthWeekday.find(rest)?.let { m ->
            val day = dayNames[m.groupValues[2].lowercase()] ?: return@let
            schedule = Schedule(listOf(day), monthWeek = nth[m.groupValues[1].lowercase()])
            rest = rest.removeRange(m.range)
        }
        if (schedule == null) monthDate.find(rest)?.let { m ->
            val n = (1..3).firstNotNullOfOrNull { m.groupValues[it].toIntOrNull() } ?: 1
            if (n !in 1..31) return@let
            schedule = Schedule(monthDay = n)
            rest = rest.removeRange(m.range)
        }
        if (schedule == null) weekly.find(rest)?.let { m ->
            val days = Regex(dayWord, RegexOption.IGNORE_CASE).findAll(m.groupValues[1]).mapNotNull { dayNames[it.value.lowercase()] }.distinct().sorted().toList()
            if (days.isEmpty()) return@let
            schedule = Schedule(days)
            // "elke donderdagavond": the part of the day stays for the moment.
            val part = Regex("(?:$PART)$", RegexOption.IGNORE_CASE).find(m.groupValues[1].trim())?.value?.lowercase()
            rest = rest.removeRange(m.range).let { r -> part?.let { "$r ${partMoment[it]}" } ?: r }
        }
        val c = clock.find(rest)
        if (c != null && schedule == null && daily.containsMatchIn(rest)) {
            schedule = Schedule(DayOfWeek.entries.toList())
            rest = daily.replace(rest, "")
        }
        val found = schedule ?: return null
        val time = c?.let { m ->
            var h = m.groupValues[1].toInt()
            val pm = m.groupValues[3].equals("pm", true) || (m.groupValues[3].isEmpty() && eveningWords.containsMatchIn(text))
            if (pm && h in 1..11) h += 12
            // Nobody does the dishes at three at night: 1 to 6 o'clock is the afternoon.
            if (m.groupValues[3].isEmpty() && h in 1..6) h += 12
            Schedule.cleanTime("$h:${m.groupValues[2].ifEmpty { "00" }}")
        }
        if (c != null && time != null) rest = rest.replace(c.value, "")
        return found.copy(time = time) to rest
    }

    fun extract(text: String): TaskHint {
        var rest = text
        var interval: Int? = null
        val onDays = schedule(rest)
        if (onDays != null) {
            rest = onDays.second
            interval = onDays.first.intervalDays
        }
        if (interval == null) counted.find(rest)?.let { m ->
            val n = m.groupValues[1].toInt()
            val unit = m.groupValues[2].lowercase()
            interval = n * when {
                unit.startsWith("w") -> 7
                unit.startsWith("maand") || unit.startsWith("month") -> 30
                else -> 1
            }
            rest = rest.removeRange(m.range)
        }
        if (interval == null) {
            for ((regex, days) in fixed) {
                val m = regex.find(rest) ?: continue
                interval = days
                rest = rest.removeRange(m.range)
                break
            }
        }
        var anchor: Anchor? = null
        // The first moment wins; "elke avond voor het slapen" loses both phrases.
        for ((regex, a) in anchors) {
            if (!regex.containsMatchIn(rest)) continue
            if (anchor == null) anchor = a
            rest = regex.replace(rest, "")
        }
        val urgent = urgentWords.containsMatchIn(rest)
        if (urgent) rest = urgentWords.replace(rest, "")
        val cleaned = rest.replace(Regex("\\s{2,}"), " ").trim(' ', ',', ':', '-', '.')
        // The moment shows as its group ("Avond"), so the rest reads as a sentence on its own.
        val shown = if (anchor != null) cleaned.replaceFirstChar { it.uppercase() } else cleaned
        return TaskHint(shown.ifEmpty { text.trim() }, interval, urgent, anchor, onDays?.first)
    }
}
