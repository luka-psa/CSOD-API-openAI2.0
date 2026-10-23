package app.hearth.ai.route

import app.hearth.core.model.plusDays
import kotlinx.datetime.LocalDate

/** "we eten vrijdag spaghetti", "vanavond eten we frietjes", "dinner on monday: lasagne". */
data class MealHint(val dish: String, val date: LocalDate)

object MealWords {
    private val days = mapOf(
        "maandag" to 0, "dinsdag" to 1, "woensdag" to 2, "donderdag" to 3, "vrijdag" to 4, "zaterdag" to 5, "zondag" to 6,
        "monday" to 0, "tuesday" to 1, "wednesday" to 2, "thursday" to 3, "friday" to 4, "saturday" to 5, "sunday" to 6,
    )
    private val day = "(vandaag|vanavond|morgen|today|tonight|tomorrow|${days.keys.joinToString("|")})"

    private val rules = listOf(
        // we eten vrijdag spaghetti / we eten spaghetti op vrijdag
        Regex("^(?:we|wij)\\s+eten\\s+(?:op\\s+)?$day\\s+(.+?)[.!]?$", RegexOption.IGNORE_CASE) to (1 to 2),
        Regex("^(?:we|wij)\\s+eten\\s+(.+?)\\s+(?:op\\s+)?$day[.!]?$", RegexOption.IGNORE_CASE) to (2 to 1),
        // vrijdag eten we spaghetti
        Regex("^(?:op\\s+)?$day\\s+eten\\s+(?:we|wij)\\s+(.+?)[.!]?$", RegexOption.IGNORE_CASE) to (1 to 2),
        // eten vrijdag: spaghetti / avondeten maandag: lasagne / dinner monday: pizza
        Regex("^(?:eten|avondeten|dinner)\\s+(?:op\\s+|on\\s+)?$day\\s*:\\s*(.+?)[.!]?$", RegexOption.IGNORE_CASE) to (1 to 2),
        // we're having pizza on friday / we have pizza tonight
        Regex("^we(?:'re| are)?\\s+having\\s+(.+?)\\s+(?:on\\s+|for\\s+dinner\\s+on\\s+)?$day[.!]?$", RegexOption.IGNORE_CASE) to (2 to 1),
    )

    fun extract(text: String, today: LocalDate): MealHint? {
        val t = text.trim()
        for ((regex, groups) in rules) {
            val m = regex.matchEntire(t) ?: continue
            val (dayGroup, dishGroup) = groups
            val dish = m.groupValues[dishGroup].trim().ifEmpty { return null }
            return MealHint(dish, resolve(m.groupValues[dayGroup].lowercase(), today))
        }
        return null
    }

    /** For meals, "vrijdag" on a Friday means tonight. */
    private fun resolve(word: String, today: LocalDate): LocalDate = when (word) {
        "vandaag", "vanavond", "today", "tonight" -> today
        "morgen", "tomorrow" -> today.plusDays(1)
        else -> today.plusDays((days.getValue(word) - today.dayOfWeek.ordinal + 7) % 7)
    }
}
