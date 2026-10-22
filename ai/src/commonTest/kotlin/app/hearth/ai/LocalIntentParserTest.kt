package app.hearth.ai

import app.hearth.core.model.Lang
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.food.AddShoppingItemArgs
import app.hearth.core.tools.food.AddShoppingItemTool
import app.hearth.core.tools.food.ListShoppingItemsArgs
import app.hearth.core.tools.food.ListShoppingItemsTool
import app.hearth.core.tools.food.MarkBoughtArgs
import app.hearth.core.tools.food.MarkBoughtTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalIntentParserTest {
    private val parser = LocalIntentParser()

    private fun add(name: String, quantity: String? = null, shop: String? = null) =
        ToolCall.of(AddShoppingItemTool.NAME, AddShoppingItemArgs.serializer(), AddShoppingItemArgs(name, quantity, shop))

    private fun list(shop: String? = null) =
        ToolCall.of(ListShoppingItemsTool.NAME, ListShoppingItemsArgs.serializer(), ListShoppingItemsArgs(shop))

    private fun bought(vararg names: String) =
        ToolCall.of(MarkBoughtTool.NAME, MarkBoughtArgs.serializer(), MarkBoughtArgs(names = names.toList()))

    private val cases: List<Triple<String, List<ToolCall>, Lang?>> = listOf(
        // Bare items
        Triple("melk", listOf(add("melk")), null),
        Triple("Melk.", listOf(add("melk")), null),
        Triple("eieren en brood", listOf(add("eieren"), add("brood")), null),
        Triple("2 melk", listOf(add("melk", "2")), null),
        // English add
        Triple("add milk", listOf(add("milk")), Lang.EN),
        Triple("Add milk to the shopping list", listOf(add("milk")), Lang.EN),
        Triple("we need eggs and bread", listOf(add("eggs"), add("bread")), Lang.EN),
        Triple("we're out of coffee", listOf(add("coffee")), Lang.EN),
        Triple("add 6 eggs, butter & some cheese", listOf(add("eggs", "6"), add("butter"), add("cheese")), Lang.EN),
        Triple("don't forget medicine from the pharmacy", listOf(add("medicine", shop = "pharmacy")), Lang.EN),
        Triple("get paint at Gamma", listOf(add("paint", shop = "gamma")), Lang.EN),
        Triple("add two packs of pasta", listOf(add("pasta", "2 packs")), Lang.EN),
        Triple("please add 1.5 kg apples", listOf(add("apples", "1.5 kg")), Lang.EN),
        // Dutch / Flemish add
        Triple("voeg melk toe", listOf(add("melk")), Lang.NL),
        Triple("zet koffie op de boodschappenlijst", listOf(add("koffie")), Lang.NL),
        Triple("we hebben nog eieren nodig", listOf(add("eieren")), Lang.NL),
        Triple("we zijn bijna door de wc-papier", listOf(add("wc-papier")), Lang.NL),
        Triple("de melk is op", listOf(add("melk")), Lang.NL),
        Triple("er is geen boter meer", listOf(add("boter")), Lang.NL),
        Triple(
            "niet vergeten: medicijnen bij de apotheek",
            listOf(add("medicijnen", shop = "apotheek")),
            Lang.NL,
        ),
        Triple(
            "vergeet niet om paracetamol bij de apotheek te halen",
            listOf(add("paracetamol", shop = "apotheek")),
            Lang.NL,
        ),
        Triple("verf halen bij de Gamma", listOf(add("verf", shop = "gamma")), Lang.NL),
        Triple("nog brood halen bij de bakker", listOf(add("brood", shop = "bakker")), Lang.NL),
        Triple("we moeten nog lampen kopen in de IKEA", listOf(add("lampen", shop = "ikea")), Lang.NL),
        Triple("haal twee pakken melk", listOf(add("melk", "2 pakken")), Lang.NL),
        Triple("kaas en ham toevoegen", listOf(add("kaas"), add("ham")), Lang.NL),
        // List
        Triple("what's on the list?", listOf(list()), Lang.EN),
        Triple("What do we need from the pharmacy?", listOf(list("pharmacy")), Lang.EN),
        Triple("wat staat er op de lijst?", listOf(list()), Lang.NL),
        Triple("Wat moeten we nog halen?", listOf(list()), Lang.NL),
        Triple("wat moeten we bij de apotheek halen?", listOf(list("apotheek")), Lang.NL),
        Triple("boodschappenlijst", listOf(list()), Lang.NL),
        // Bought
        Triple("bought milk and eggs", listOf(bought("milk", "eggs")), Lang.EN),
        Triple("melk gekocht", listOf(bought("melk")), Lang.NL),
        Triple("ik heb de eieren gekocht", listOf(bought("eieren")), Lang.NL),
        Triple("vink brood af", listOf(bought("brood")), Lang.NL),
    )

    @Test
    fun parsesEverydayCommands() {
        for ((input, calls, lang) in cases) {
            val parsed = parser.parse(input)
            assertEquals(calls, parsed?.calls, "calls for: $input")
            assertEquals(lang, parsed?.lang, "language for: $input")
        }
    }

    @Test
    fun leavesQuestionsAndSmallTalkToSmarterTiers() {
        listOf(
            "",
            "hallo",
            "thanks",
            "what's open?",
            "give me one quick task",
            "break the kitchen clean-out into steps",
            "wanneer is de autokeuring?",
            "can you plan the week for us",
        ).forEach { assertNull(parser.parse(it), "should not parse: $it") }
    }
}
