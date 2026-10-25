package app.hearth.ai

import app.hearth.ai.route.ListHint
import app.hearth.ai.route.ListKind
import app.hearth.ai.route.ListWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListWordsTest {
    private val cases = listOf(
        "Paklijst babysit:\n- luiers\n- slaapzak\n- speentje" to ListHint("Paklijst babysit", listOf("luiers", "slaapzak", "speentje")),
        "Babysit\n• Luiers\n• Fles" to ListHint("Babysit", listOf("Luiers", "Fles")),
        "Boodschappen\nmelk\neieren\nbrood" to ListHint("Boodschappen", listOf("melk", "eieren", "brood")),
        "1. melk\n2. tandarts bellen\n[x] lakens" to ListHint(null, listOf("melk", "tandarts bellen", "lakens")),
        "melk\ntandarts morgen" to ListHint(null, listOf("melk", "tandarts morgen")),
        "Recept pannenkoeken:\n250 g bloem\n3 eieren\n500 ml melk" to ListHint("Recept pannenkoeken", listOf("250 g bloem", "3 eieren", "500 ml melk")),
    )

    @Test
    fun readsAPastedList() {
        cases.forEach { (input, expected) -> assertEquals(expected, ListWords.extract(input), input) }
        assertNull(ListWords.extract("melk en eieren"))
    }

    @Test
    fun theHeadingSaysWhereItGoes() {
        assertEquals(ListKind.SHOPPING, ListWords.kind("Boodschappenlijst"))
        assertEquals(ListKind.RECIPE, ListWords.kind("Recept pannenkoeken"))
        assertEquals(ListKind.CHECKLIST, ListWords.kind("Paklijst babysit"))
        assertEquals(ListKind.NONE, ListWords.kind(null))
        assertEquals("Pannenkoeken", ListWords.recipeName("Recept pannenkoeken"))
    }
}
