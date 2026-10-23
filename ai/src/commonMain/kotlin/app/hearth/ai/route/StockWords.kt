package app.hearth.ai.route

import app.hearth.core.model.StockState

/** "de koffie is op", "wc-papier is bijna op", "we zijn door de melk", "we're out of milk". */
data class StockHint(val name: String, val state: StockState)

object StockWords {
    private val rules: List<Pair<Regex, StockState?>> = listOf(
        Regex("^(?:de |het )?(.+?)\\s+(?:is|zijn|raakt|raken)\\s+bijna\\s+op[.!]?$", RegexOption.IGNORE_CASE) to StockState.LOW,
        Regex("^(?:de |het )?(.+?)\\s+(?:is|zijn)\\s+op[.!]?$", RegexOption.IGNORE_CASE) to StockState.OUT,
        Regex("^we\\s+zijn\\s+(bijna\\s+)?door\\s+(?:de |het |onze |ons )?(.+?)[.!]?$", RegexOption.IGNORE_CASE) to null,
        Regex("^(?:we(?:'re| are)\\s+)?(?:running\\s+low\\s+on|almost\\s+out\\s+of)\\s+(?:the\\s+)?(.+?)[.!]?$", RegexOption.IGNORE_CASE) to StockState.LOW,
        Regex("^(?:we(?:'re| are)\\s+)?out\\s+of\\s+(?:the\\s+)?(.+?)[.!]?$", RegexOption.IGNORE_CASE) to StockState.OUT,
    )

    fun extract(text: String): StockHint? {
        val t = text.trim()
        for ((regex, state) in rules) {
            val m = regex.matchEntire(t) ?: continue
            return if (state == null) {
                // "we zijn (bijna) door de melk"
                StockHint(m.groupValues[2].trim(), if (m.groupValues[1].isNotBlank()) StockState.LOW else StockState.OUT)
            } else {
                StockHint(m.groupValues[1].trim(), state)
            }
        }
        return null
    }
}
