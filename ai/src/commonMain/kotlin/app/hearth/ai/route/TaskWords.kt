package app.hearth.ai.route

/** Rhythm and urgency read from a sentence: "lakens verversen om de 2 weken", "dringend: verzekering bellen". */
data class TaskHint(val text: String, val intervalDays: Int?, val urgent: Boolean)

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
        val urgent = urgentWords.containsMatchIn(rest)
        if (urgent) rest = urgentWords.replace(rest, "")
        val cleaned = rest.replace(Regex("\\s{2,}"), " ").trim(' ', ',', ':', '-', '.')
        return TaskHint(cleaned.ifEmpty { text.trim() }, interval, urgent)
    }
}
