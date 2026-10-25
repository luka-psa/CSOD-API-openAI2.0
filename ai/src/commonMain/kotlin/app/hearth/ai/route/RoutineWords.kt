package app.hearth.ai.route

import app.hearth.core.model.Anchor

/** A routine said in one go: its name, the moment of the day, and its steps in order. */
data class RoutineHint(val name: String, val anchor: Anchor?, val steps: List<String>)

/**
 * "avondroutine: boodschappen, Mia ophalen, koken en pyjama aan", "paklijst opvang: luiers, flesjes,
 * reservekleren", "evening routine: groceries, pick up Mia, cook, pyjamas". The name before the colon
 * says it is a routine; the rest are its steps, in the order they were said.
 */
object RoutineWords {
    private val head = Regex(
        "^\\s*(" +
            "(?:ochtend|avond|middag|namiddag|slaap|bedtijd|weekend)?routine(?:\\s+[^:]{1,40})?" +
            "|routine\\s+[^:]{1,40}" +
            "|(?:pak|check)lijst(?:je)?(?:\\s+[^:]{1,40})?" +
            "|checklist(?:\\s+[^:]{1,40})?" +
            "|lijstje\\s+(?:voor\\s+)?[^:]{1,40}" +
            "|(?:morning|afternoon|evening|bedtime|weekend)\\s+routine(?:\\s+[^:]{1,40})?" +
            "|packing\\s+list(?:\\s+[^:]{1,40})?" +
            ")\\s*:\\s*(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val split = Regex(
        "\\s*(?:,|;|→|->|\\s+en\\s+dan\\s+|\\s+daarna\\s+|\\s+dan\\s+|\\s+en\\s+|\\s+and\\s+then\\s+|\\s+then\\s+|\\s+and\\s+)\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val nameMoments = listOf(
        Regex("ochtend|morning", RegexOption.IGNORE_CASE) to Anchor.MORNING,
        Regex("middag|na school|na de opvang|afternoon|after school", RegexOption.IGNORE_CASE) to Anchor.AFTERNOON,
        Regex("slaap|bedtijd|bedtime", RegexOption.IGNORE_CASE) to Anchor.BEDTIME,
        Regex("avond|evening", RegexOption.IGNORE_CASE) to Anchor.EVENING,
    )

    fun extract(text: String): RoutineHint? {
        val m = head.find(text) ?: return null
        val name = m.groupValues[1].trim().replace(Regex("\\s+"), " ").replaceFirstChar { it.uppercase() }
        val steps = m.groupValues[2].trim().trimEnd('.', '!').split(split)
            .map { it.trim().trim('.', ',').replaceFirstChar { c -> c.uppercase() } }
            .filter { it.isNotEmpty() }
        if (steps.isEmpty()) return null
        val anchor = TaskWords.extract(name).anchor ?: nameMoments.firstOrNull { (r, _) -> r.containsMatchIn(name) }?.second
        return RoutineHint(name, anchor, steps)
    }
}
