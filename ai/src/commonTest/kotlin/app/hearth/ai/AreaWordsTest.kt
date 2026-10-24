package app.hearth.ai

import app.hearth.ai.route.AreaHint
import app.hearth.ai.route.AreaWords
import app.hearth.core.model.Section
import kotlin.test.Test
import kotlin.test.assertEquals

class AreaWordsTest {
    @Test
    fun `command words name the place and stay out of the item`() {
        val cases = listOf(
            "voeg aan de huisregels toe dat we onze borden naar de keuken brengen" to AreaHint(Section.NOTE, "We brengen onze borden naar de keuken"),
            "Voeg een item toe aan de huisregels: schoenen uit in de gang" to AreaHint(Section.NOTE, "Schoenen uit in de gang"),
            "add a item to house that we have to bring our plates to the kitchen" to AreaHint(Section.NOTE, "We have to bring our plates to the kitchen"),
            "add to house rules that phones stay downstairs at night" to AreaHint(Section.NOTE, "Phones stay downstairs at night"),
            "nieuwe huisregel: geen schermen aan tafel" to AreaHint(Section.NOTE, "Geen schermen aan tafel"),
            "zet bij de ergernissen dat de vaatwasser nooit leeg is" to AreaHint(Section.FRICTION, "De vaatwasser is nooit leeg"),
            "nieuw doel samen elke week gaan wandelen" to AreaHint(Section.GOAL, "Samen elke week gaan wandelen"),
            "add to goals: run a 10k" to AreaHint(Section.GOAL, "Run a 10k"),
            "zet fiets maken bij de projecten" to AreaHint(Section.PROJECT, "Fiets maken"),
            "add fixing the gate to projects" to AreaHint(Section.PROJECT, "Fixing the gate"),
            "zet de verzekering opzeggen op de takenlijst" to AreaHint(Section.OTHER, "De verzekering opzeggen"),
            "voeg toe aan de gewoontes: planten water geven om de 3 dagen" to AreaHint(Section.CHORE, "Planten water geven om de 3 dagen"),
        )
        for ((said, expected) in cases) assertEquals(expected, AreaWords.extract(said), said)
    }

    @Test
    fun `a dat-clause reads as a sentence`() {
        assertEquals("We moeten onze borden meebrengen", AreaWords.mainClause("we onze borden moeten meebrengen"))
        assertEquals("Niemand roept aan tafel", AreaWords.mainClause("niemand aan tafel roept"))
        assertEquals("schoenen uit", AreaWords.mainClause("schoenen uit"))
    }

    @Test
    fun `ordinary sentences are left to the other rules`() {
        listOf(
            "voeg melk toe",
            "zet melk op de lijst",
            "tandarts morgen",
            "huisregel: geen schermen na 8 uur",
            "add milk to the shopping list",
            "we eten vrijdag spaghetti",
            "ik erger me aan de rommel in de gang",
        ).forEach { assertEquals(null, AreaWords.extract(it), it) }
    }
}
