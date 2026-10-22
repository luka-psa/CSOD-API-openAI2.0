package app.hearth.ai

import app.hearth.core.model.Lang
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.food.AddShoppingItemArgs
import app.hearth.core.tools.food.AddShoppingItemTool
import app.hearth.core.tools.food.ListShoppingItemsArgs
import app.hearth.core.tools.food.ListShoppingItemsTool
import app.hearth.core.tools.food.MarkBoughtArgs
import app.hearth.core.tools.food.MarkBoughtTool

/** Tier 1: deterministic Flemish/English rules for everyday commands. Works offline, instantly. */
class LocalIntentParser {

    data class Parsed(val calls: List<ToolCall>, val lang: Lang?)

    fun parse(input: String): Parsed? {
        val text = clean(input)
        if (text.isEmpty()) return null
        return parseList(text) ?: parseBought(text) ?: parseAdd(text) ?: parseBare(text)
    }

    // ---- list ----

    private fun parseList(text: String): Parsed? {
        for ((regex, lang) in LIST_RULES) {
            val m = regex.matchEntire(text) ?: continue
            val shop = m.groupValues.getOrNull(1)?.trim()?.ifEmpty { null }
            return Parsed(
                listOf(ToolCall.of(ListShoppingItemsTool.NAME, ListShoppingItemsArgs.serializer(), ListShoppingItemsArgs(shop))),
                lang,
            )
        }
        return null
    }

    // ---- bought ----

    private fun parseBought(text: String): Parsed? {
        for ((regex, lang) in BOUGHT_RULES) {
            val m = regex.matchEntire(text) ?: continue
            val body = m.groupValues[1].replace(TRAILING_IS, "")
            val names = splitItems(body).map { parseItem(it).second }.filter { it.isNotEmpty() }
            if (names.isEmpty()) return null
            return Parsed(
                listOf(ToolCall.of(MarkBoughtTool.NAME, MarkBoughtArgs.serializer(), MarkBoughtArgs(names = names))),
                lang,
            )
        }
        return null
    }

    // ---- add ----

    private fun parseAdd(text: String): Parsed? {
        for ((regex, lang) in ADD_RULES) {
            val m = regex.matchEntire(text) ?: continue
            // Some rules capture the item and a trailing shop phrase separately ("verf halen | bij de gamma").
            val body = m.groupValues.drop(1).filter { it.isNotEmpty() }.joinToString(" ")
            return addCalls(body, lang)?.let { Parsed(it, lang) }
        }
        return null
    }

    private fun parseBare(text: String): Parsed? {
        if ('?' in text) return null
        val words = text.split(' ')
        if (words.size > 4 || words.first() in COMMAND_WORDS || text in SMALL_TALK) return null
        if (!BARE_ALLOWED.matches(text)) return null
        return addCalls(text, null)?.let { Parsed(it, null) }
    }

    private fun addCalls(body: String, lang: Lang?): List<ToolCall>? {
        val (itemsPart, shop) = extractShop(body.trim(), lang)
        val calls = splitItems(itemsPart).mapNotNull { raw ->
            val (quantity, name) = parseItem(raw)
            if (name.isEmpty()) null
            else ToolCall.of(
                AddShoppingItemTool.NAME,
                AddShoppingItemArgs.serializer(),
                AddShoppingItemArgs(name = name, quantity = quantity, shop = shop),
            )
        }
        return calls.ifEmpty { null }
    }

    /** "medicijnen bij de apotheek" -> ("medicijnen", "apotheek"). */
    private fun extractShop(body: String, lang: Lang?): Pair<String, String?> {
        val rules = when (lang) {
            Lang.NL -> listOf(SHOP_NL)
            Lang.EN -> listOf(SHOP_EN)
            null -> listOf(SHOP_NL, SHOP_EN)
        }
        for (rule in rules) {
            val m = rule.matchEntire(body) ?: continue
            val shop = m.groupValues[2].trim()
            if (shop.split(' ').size <= 3) return m.groupValues[1].trim() to shop
        }
        return body to null
    }

    private fun splitItems(body: String): List<String> =
        body.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }

    /** "2 kg appels" -> ("2 kg", "appels"); "nog wat melk" -> (null, "melk"). */
    private fun parseItem(raw: String): Pair<String?, String> {
        var s = stripFillers(raw.trim())
        var quantity: String? = null
        val first = s.substringBefore(' ')
        NUMBER_WORDS[first]?.let {
            quantity = it
            s = s.substringAfter(' ', "")
        }
        QUANTITY.matchEntire(s)?.let { m ->
            val unit = m.groupValues[2].trim()
            quantity = listOf(m.groupValues[1].replace(',', '.'), unit).filter { it.isNotEmpty() }.joinToString(" ")
            s = m.groupValues[3]
        }
        if (quantity != null) {
            UNITS.matchEntire(s)?.let { m ->
                quantity = "$quantity ${m.groupValues[1]}"
                s = m.groupValues[2]
            }
        }
        return quantity to stripFillers(s).trim()
    }

    private fun stripFillers(s: String): String {
        var out = s
        while (true) {
            val next = out.replaceFirst(LEADING_FILLER, "")
            if (next == out) return out
            out = next
        }
    }

    private fun clean(input: String): String =
        input.trim()
            .lowercase()
            .replace('’', '\'')
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^(?:hearth[,:]?\\s+)"), "")
            .replace(Regex("^(?:please|alsjeblieft|aub|graag)[,]?\\s+"), "")
            .replace(Regex("[,]?\\s+(?:please|alsjeblieft|aub|a\\.u\\.b\\.|graag)$"), "")
            .trimEnd('.', '!', ' ')

    private companion object {
        fun r(pattern: String) = Regex(pattern)
        private const val LIST = "(?:boodschappen)?lijst(?:je)?"
        private const val LIST_EN = "(?:shopping |grocery )?list"
        private const val ART_NL = "(?:de |het |den )?"

        val LIST_RULES = listOf(
            r("what(?:'s| is) on (?:the |our )?$LIST_EN\\??") to Lang.EN,
            r("what do we (?:still )?need(?: (?:from|at) (?:the )?(.+?))?\\??") to Lang.EN,
            r("(?:show|open)(?: me)? (?:the |our )?$LIST_EN") to Lang.EN,
            r("(?:the )?shopping list\\??") to Lang.EN,
            r("wat staat (?:er )?(?:nog )?op (?:de |onze )?$LIST\\??") to Lang.NL,
            r("wat (?:moeten|hebben) we (?:nog )?(?:nodig|halen|kopen)(?: (?:bij|in) $ART_NL(.+?))?\\??") to Lang.NL,
            r("wat moeten we (?:nog )?(?:bij|in) $ART_NL(.+?) (?:halen|kopen)\\??") to Lang.NL,
            r("(?:toon|open|laat)(?: me)? (?:de |onze )?$LIST(?: zien)?") to Lang.NL,
            r("(?:de )?boodschappenlijst(?:je)?\\??") to Lang.NL,
        )

        val BOUGHT_RULES = listOf(
            r("(?:i |we )?(?:bought|got|have bought|picked up) (.+)") to Lang.EN,
            r("(?:tick|check|cross) off (.+)") to Lang.EN,
            r("(.+?) (?:is |are )?bought") to Lang.EN,
            r("(?:ik heb |we hebben )?(.+?) gekocht") to Lang.NL,
            r("vink (.+?) af") to Lang.NL,
            r("(.+?) afvinken") to Lang.NL,
        )

        val ADD_RULES = listOf(
            r("add (.+?)(?: to (?:the |our )?$LIST_EN)?") to Lang.EN,
            r("put (.+?) on (?:the |our )?$LIST_EN") to Lang.EN,
            // "need to go to the dentist" is not a shopping item; only "need X" / "need to buy X".
            r("(?:we |i )?(?:still )?need (?:to (?:buy|get) )?(?!to )(.+)") to Lang.EN,
            r("(?:we(?:'re| are) |i(?:'m| am) )?(?:out of|running low on|low on|almost out of) (.+)") to Lang.EN,
            r("(?:don't|do not) forget(?: to (?:buy|get|grab|pick up))?:? (.+)") to Lang.EN,
            r("remember to (?:buy|get|grab|pick up) (.+)") to Lang.EN,
            r("(?:buy|get|grab|pick up) (.+)") to Lang.EN,
            r("voeg (.+?) toe(?: aan (?:de )?$LIST)?") to Lang.NL,
            r("zet (.+?) op (?:de )?$LIST") to Lang.NL,
            r("(.+?) (?:op (?:de )?$LIST(?: zetten)?|toevoegen|aan (?:de )?$LIST toevoegen)") to Lang.NL,
            r("we hebben (?:nog )?(.+?) nodig") to Lang.NL,
            r("(?:er is|we hebben) geen (.+?) meer") to Lang.NL,
            r("we zijn (?:bijna )?door (?:de |het |onze |ons )?(.+)") to Lang.NL,
            r("(?:niet vergeten|vergeet niet)[:,]? (?:om )?(.+?)(?: (?:te )?(?:halen|kopen|meenemen|mee te nemen))?") to Lang.NL,
            r("(?:koop|haal) (?:nog )?(.+)") to Lang.NL,
            r("(?:we moeten |ik moet )?(?:nog )?(.+?) (?:halen|kopen|gaan halen|meebrengen)( (?:bij|in) .+)?") to Lang.NL,
            r("(?:de |het )?(.+?) (?:is|zijn) (?:bijna )?op") to Lang.NL,
        )

        val SHOP_NL = r("(.+?) (?:bij|in|van) (?:de |het |den )?([\\p{L}\\p{N}' -]+)")
        val SHOP_EN = r("(.+?) (?:from|at) (?:the )?([\\p{L}\\p{N}' -]+)")
        val SEPARATORS = r("\\s*(?:,|&|\\+|\\band\\b|\\ben\\b)\\s*")
        val TRAILING_IS = r("\\s+(?:is|zijn|are)$")
        val LEADING_FILLER = r("^(?:some|a|an|the|more|our|een|wat|nog|de|het|onze|ons)\\s+")
        val QUANTITY = r("(\\d+(?:[.,]\\d+)?)\\s*(x|×|kg|g|gram|l|liter|cl|ml)?\\s+(.+)")
        val UNITS = r("(pak|pakken|pakje|pakjes|fles|flessen|doos|dozen|blik|blikken|stuks|bottles?|packs?|boxes?|cans?|bags?|zakken?|zakjes?) (?:of |met )?(.+)")
        val NUMBER_WORDS = mapOf(
            "twee" to "2", "drie" to "3", "vier" to "4", "vijf" to "5", "zes" to "6", "tien" to "10",
            "two" to "2", "three" to "3", "four" to "4", "five" to "5", "six" to "6", "ten" to "10",
        )
        val BARE_ALLOWED = r("[\\p{L}\\p{N}' ,&+-]+")
        val COMMAND_WORDS = setOf(
            "what", "wat", "who", "wie", "when", "wanneer", "how", "hoe", "why", "waarom", "where", "waar",
            "give", "geef", "show", "toon", "is", "are", "can", "kan", "kun", "do", "doe", "tell", "zeg",
            "help", "make", "maak", "break", "split", "plan", "remove", "delete", "verwijder", "clear",
        )
        val SMALL_TALK = setOf(
            "hi", "hello", "hey", "hallo", "hoi", "dag", "goeiemorgen", "goedemorgen", "goeiendag", "goedenavond",
            "bedankt", "dank je", "dankjewel", "dank u", "merci", "thanks", "thank you", "ok", "oké", "okay",
            "ja", "nee", "yes", "no", "test", "stop", "sorry", "cool", "top", "super", "prima", "nice",
        )
    }
}
