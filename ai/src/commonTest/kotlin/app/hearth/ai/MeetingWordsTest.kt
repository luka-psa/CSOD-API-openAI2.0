package app.hearth.ai

import app.hearth.ai.route.MeetingWords
import app.hearth.ai.route.Place
import app.hearth.ai.route.Spoken
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MeetingWordsTest {
    private val monday = LocalDate(2026, 10, 26)

    @Test
    fun pausesInsideAThoughtDoNotCutIt() {
        // As the recognizer gave it: a new line at every breath.
        val heard = listOf(
            "dat gaat donderdag naar de opvang dus die moet oprecht worden",
            "voor de rest ben ik donderdag vrij",
            "deze week moet ik afspreken met Anthony",
            "vrije tijd gewoon bezoeken",
        )
        val thoughts = MeetingWords.thoughts(heard, monday)
        assertEquals(listOf(0..1, 2..3), thoughts.map { it.lines })
        assertEquals(
            listOf(
                listOf(Place.EVENT to "dat gaat donderdag naar de opvang"),
                listOf(Place.EVENT to "deze week moet ik afspreken met Anthony"),
            ),
            thoughts.map { t -> t.conclusions.map { it.place to it.sentence } },
        )
        // What was said around it stays with it.
        assertEquals("deze week moet ik afspreken met Anthony vrije tijd gewoon bezoeken", thoughts[1].conclusions.single().heard)
    }

    @Test
    fun thoughtsAreJoinedByHowTheySoundNotByPauses() {
        val cases = listOf(
            // "dus", "die", "voor de rest" go on about what was just said.
            listOf("Mia gaat zaterdag zwemmen", "dus die moet om negen uur klaar staan") to listOf(0..1),
            // A half sentence and its rest.
            listOf("deze week moet ik afspreken met", "Anthony") to listOf(0..1),
            // A day said after a pause.
            listOf("we gaan naar de zee", "zaterdag") to listOf(0..1),
            // "ja", "oké": heard, part of the same thought.
            listOf("de garage opruimen dit weekend", "oké") to listOf(0..1),
            // Someone new says something new.
            listOf("we moeten nog melk halen", "ik wil zaterdag uitslapen") to listOf(0..0, 1..1),
            // "oh ja" starts another point.
            listOf("we moeten nog melk halen", "oh ja dus de tandarts bellen") to listOf(0..0, 1..1),
        )
        cases.forEach { (lines, groups) -> assertEquals(groups, MeetingWords.thoughts(lines, monday).map { it.lines }, lines.toString()) }
    }

    @Test
    fun aLongSilenceStartsAnotherThought() {
        val lines = listOf("we moeten melk halen", "en brood")
        assertEquals(listOf(0..1), MeetingWords.thoughts(lines, monday, times = listOf(0L, 20_000L)).map { it.lines })
        assertEquals(listOf(0..0, 1..1), MeetingWords.thoughts(lines, monday, times = listOf(0L, 300_000L)).map { it.lines })
    }

    @Test
    fun oneThoughtCanHoldMoreThanOneConclusion() {
        val t = MeetingWords.thoughts(listOf("we moeten nog melk halen", "de verzekering bellen", "en zaterdag niks plannen"), monday).single()
        assertEquals(listOf(Place.SHOPPING, Place.PLANNING, Place.FREE_TIME), t.conclusions.map { it.place })
    }

    @Test
    fun justTalkConcludesNothing() {
        assertEquals(emptyList(), MeetingWords.thoughts(listOf("ja dat is goed", "voor de rest ben ik donderdag vrij"), monday).single().conclusions)
    }

    @Test
    fun aPronounBecomesThePersonTheThoughtIsAbout() {
        val t = MeetingWords.thoughts(listOf("Mia is wat ziek", "die gaat donderdag naar de dokter"), monday, people = listOf("Mia", "Sam")).single()
        assertEquals(listOf(Place.EVENT to "Mia gaat donderdag naar de dokter"), t.conclusions.map { it.place to it.sentence })
    }

    @Test
    fun placesReadTheWholeWordOnly() {
        val cases = listOf(
            // "rest" in "voor de rest" is not rest.
            "voor de rest ben ik donderdag vrij" to Place.NONE,
            "zaterdag hebben we vrij" to Place.FREE_TIME,
            "deze week moet ik afspreken met Anthony" to Place.EVENT,
            "donderdag gaat ze naar de opvang" to Place.EVENT,
            "zaterdag moeten we de auto wassen" to Place.PLANNING,
            "ik ben het beu dat de schoenen in de gang liggen" to Place.FRICTION,
            "da's ambetant" to Place.FRICTION,
            "we moeten nog melk halen" to Place.SHOPPING,
            "ja dat is goed" to Place.NONE,
        )
        cases.forEach { (sentence, place) -> assertEquals(place, MeetingWords.place(sentence, monday), sentence) }
    }

    @Test
    fun theGistReadsAsAnItem() {
        val cases = listOf(
            "moet ik afspreken met Anthony" to "Afspreken met Anthony",
            "dat gaat naar de opvang" to "Naar de opvang",
            "gaan we op vakantie" to "Op vakantie",
            "ja dus we moeten nog de tandarts bellen" to "De tandarts bellen",
            "allee ge moet efkes de verzekering bellen" to "De verzekering bellen",
            "Mia gaat naar de opvang" to "Mia gaat naar de opvang",
            "de tandarts voor Mia" to "De tandarts voor Mia",
        )
        cases.forEach { (said, gist) -> assertEquals(gist, MeetingWords.gist(said), said) }
    }

    @Test
    fun spokenFlemishIsReadAsPlainDutch() {
        val cases = listOf(
            "allee, da moet ge efkes bellen" to "dat moet je bellen",
            "euh 'k ga morgen nie" to "ik ga morgen niet",
            "we moeten gewoon nen nieuwe kast" to "we moeten een nieuwe kast",
            "Mia gaat naar de opvang" to "Mia gaat naar de opvang",
        )
        cases.forEach { (said, plain) -> assertEquals(plain, Spoken.clean(said), said) }
    }

    @Test
    fun theHouseholdsOwnWordsPickTheRightGuess() {
        val vocabulary = listOf("Ada", "Mia", "opvang", "melk")
        assertEquals("Ada moet naar de opvang", Spoken.pick(listOf("adda moet naar de op vang", "Ada moet naar de opvang"), vocabulary))
        // A tie keeps the recognizer's first guess.
        assertEquals("we eten pasta", Spoken.pick(listOf("we eten pasta", "we eten basta"), vocabulary))
        assertEquals("melk", Spoken.pick(listOf("melk"), emptyList()))
    }
}
