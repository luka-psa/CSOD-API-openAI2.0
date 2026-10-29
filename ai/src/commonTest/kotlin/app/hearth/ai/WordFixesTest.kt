package app.hearth.ai

import app.hearth.core.model.WordFix
import app.hearth.core.model.WordFixes
import kotlin.test.Test
import kotlin.test.assertEquals

/** The household's own words: learned from what the family puts right, applied before Hearth reads. */
class WordFixesTest {
    @Test
    fun `a line fixed by hand teaches the words that changed in place`() {
        val cases = listOf(
            Triple("dus die moet oprecht worden", "dus die moet opgehaald worden", listOf(WordFix("oprecht", "opgehaald"))),
            Triple("heb je een wesp op beneden", "heb je een wasmand beneden", listOf(WordFix("wesp op", "wasmand"))),
            Triple("dan zet Jaules de vaat was aan", "dan zet Jules de vaatwasser aan", listOf(WordFix("Jaules", "Jules"), WordFix("vaat was", "vaatwasser"))),
            // Said again in other words: nothing to learn from it.
            Triple("Istanbul", "we gaan de keuken herorganiseren", emptyList()),
            Triple("ik vind dat de keuken nog altijd werkt", "de keuken moet opgeruimd zijn voor we koken", emptyList()),
            // Only case changed: nothing new.
            Triple("mia gaat zwemmen", "Mia gaat zwemmen", emptyList()),
        )
        cases.forEach { (before, after, learned) -> assertEquals(learned, WordFixes.learn(before, after), before) }
    }

    @Test
    fun `a name typed again freely only teaches words spelled almost the same`() {
        assertEquals(listOf(WordFix("Jaules", "Jules")), WordFixes.learnSpelling("Jaules heeft een wesp op beneden", "Jules brengt de wasmand naar beneden"))
        assertEquals(listOf(WordFix("wasmant", "wasmand")), WordFixes.learnSpelling("de wasmant naar boven", "Wasmand naar boven brengen"))
        // "wesp" and "wasmand" are different words, not a slip.
        assertEquals(emptyList(), WordFixes.learnSpelling("een wesp op beneden", "de wasmand beneden"))
    }

    @Test
    fun `fixes are applied to whole words, the longest first, keeping a capital at the start`() {
        val fixes = listOf(WordFix("was", "wasgoed"), WordFix("vaat was", "vaatwasser"), WordFix("jaules", "Jules"))
        assertEquals("Jules zet de vaatwasser aan en vouwt het wasgoed", WordFixes.apply("jaules zet de vaat was aan en vouwt het was", fixes))
        assertEquals("Wasgoed", WordFixes.apply("Was", fixes))
        assertEquals("wasmand", WordFixes.apply("wasmand", fixes))
    }

    @Test
    fun `the newest meaning wins and a word learned the other way round is put right`() {
        val known = listOf(WordFix("Jaules", "Jules"), WordFix("vaat was", "vaatwasser"))
        assertEquals(
            listOf(WordFix("vaat was", "vaatwasser"), WordFix("jaules", "Jules P")),
            WordFixes.merge(known, listOf(WordFix("jaules", "Jules P"))),
        )
        // "It is Jaules after all": the old fix goes, the new one stays.
        assertEquals(listOf(WordFix("vaat was", "vaatwasser"), WordFix("Jules", "Jaules")), WordFixes.merge(known, listOf(WordFix("Jules", "Jaules"))))
        assertEquals(known, WordFixes.parse(WordFixes.encode(known)))
        assertEquals(emptyList(), WordFixes.parse("not json"))
    }
}
