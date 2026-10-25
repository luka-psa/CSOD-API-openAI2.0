package app.hearth.ai.route

import app.hearth.core.model.Anchor

/**
 * Rhythm, urgency and a moment of the day read from a sentence: "lakens verversen om de 2 weken",
 * "dringend: verzekering bellen", "'s avonds de tassen klaarzetten".
 */
data class TaskHint(val text: String, val intervalDays: Int?, val urgent: Boolean, val anchor: Anchor? = null)

object TaskWords {
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

    fun extract(text: String): TaskHint {
        var rest = text
        var interval: Int? = null
        counted.find(rest)?.let { m ->
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
        return TaskHint(shown.ifEmpty { text.trim() }, interval, urgent, anchor)
    }
}
