package app.hearth.ai

import app.hearth.ai.route.MeetingWords
import app.hearth.ai.route.Place
import app.hearth.ai.route.Spoken
import app.hearth.ai.route.Stage
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

    /**
     * Two people in the weekoverleg, thinking out loud: catching up, looking back at themselves, what's next, what
     * rubs, a brainstorm on a rule and the rule they agree on. Only what is decided becomes something.
     */
    @Test
    fun aRealConversationOnlyTurnsDecisionsIntoThings() {
        val sunday = LocalDate(2026, 10, 25)
        val heard = listOf(
            "oké hoe was jouw week eigenlijk",
            "ja best druk op het werk maar ik vond het wel goed dat we woensdag samen gegeten hebben",
            "ja dat was fijn",
            "ik was wel moe donderdag",
            "ik heb eerlijk gezegd de was niet goed opgevolgd deze week",
            "nee dat ligt nog allemaal in de mand",
            "ik denk dat ik het gewoon altijd uitstel tot het weekend",
            "mja dat herken ik wel",
            "wat staat er volgende week eigenlijk op de planning",
            "dinsdag heeft Mia tandarts",
            "en ik moet donderdag de auto naar de garage brengen",
            "misschien kan jij Mia dan ophalen",
            "ja dat kan ik doen",
            "wat ik niet zo leuk vind is dat de keuken 's avonds altijd vol staat",
            "ja dat stoort mij ook",
            "zouden we niet kunnen afspreken dat wie kookt niet afwast",
            "hmm of misschien dat we gewoon voor het slapengaan samen tien minuten opruimen",
            "ik weet niet of dat gaat lukken elke dag",
            "wat als we het alleen op weekdagen doen",
            "ja maar dan blijft het weekend een rommel",
            "oké dus dan elke avond maar maximaal tien minuten",
            "ja dat lijkt me haalbaar",
            "goed dan spreken we af dat we elke avond voor het slapengaan tien minuten samen de keuken opruimen",
            "afgesproken",
        )
        val thoughts = MeetingWords.thoughts(heard, sunday, people = listOf("Mia", "Sam", "Lien"))
        assertEquals(
            listOf(
                // Catching up and looking back at ourselves: talk, except what went well.
                Stage.TALK to emptyList(),
                Stage.LOOK_BACK to listOf(Place.WIN to "We hebben woensdag samen gegeten"),
                Stage.LOOK_BACK to emptyList(),
                Stage.LOOK_BACK to emptyList(),
                Stage.LOOK_BACK to emptyList(),
                // What's next: the plans, and the suggestion the other said yes to (on the day said before).
                Stage.PLANNING to emptyList(),
                Stage.PLANNING to listOf(Place.EVENT to "dinsdag heeft Mia tandarts", Place.EVENT to "en ik moet donderdag de auto naar de garage brengen"),
                Stage.DECISION to listOf(Place.EVENT to "Mia donderdag ophalen"),
                // What rubs, once, in its own words; "dat stoort mij ook" agrees.
                Stage.CONCERN to listOf(Place.FRICTION to "De keuken staat 's avonds altijd vol"),
                // The brainstorm gives no ideas of its own, only the rule they agree on.
                Stage.DECISION to listOf(Place.HOUSE_RULE to "goed dan spreken we af dat we elke avond voor het slapengaan tien minuten samen de keuken opruimen"),
            ),
            thoughts.map { t -> t.stage to t.conclusions.map { it.place to it.sentence } },
        )
        assertEquals("Elke avond voor het slapengaan tien minuten samen de keuken opruimen", MeetingWords.ruleText(thoughts.last().conclusions.single().sentence))
    }

    @Test
    fun optionsWithoutAYesStayTalk() {
        val monday = LocalDate(2026, 10, 26)
        // A brainstorm that ends without a decision.
        val open = MeetingWords.thoughts(listOf("zouden we de was op zondag doen", "of misschien op woensdag", "ik weet het niet"), monday).single()
        assertEquals(Stage.BRAINSTORM to emptyList<Place>(), open.stage to open.conclusions.map { it.place })
        // A suggestion that gets a no.
        val no = MeetingWords.thoughts(listOf("kan jij zaterdag de garage opruimen", "nee liever niet"), monday).single()
        assertEquals(emptyList(), no.conclusions)
        // The same in English: the yes makes it a task.
        val yes = MeetingWords.thoughts(listOf("could you call the dentist on Tuesday", "sure"), monday).single()
        assertEquals(listOf(Place.EVENT to "call the dentist on Tuesday"), yes.conclusions.map { it.place to it.sentence })
    }

    @Test
    fun talkAboutTheMeetingItselfOrEndingInADoubtIsNoPlan() {
        listOf(
            "Ja vandaag gaan we kijken naar de weekplanning misschien",
            "Vandaag gaan we de weekplanning bekijken",
            "eerst de agenda overlopen",
            "donderdag gaan we naar de zee misschien",
        ).forEach { said -> assertEquals(emptyList(), MeetingWords.thoughts(listOf(said), monday).flatMap { it.conclusions }, said) }
    }

    @Test
    fun lookingBackNamesWhatWentWellAndWhatRubs() {
        val t = MeetingWords.thoughts(
            listOf("wat ik vond dat heel goed ging was het opkuisen van de lavabo iets anders wat moeilijk ging was de keuken die blijft altijd vol staan"),
            monday,
        ).single()
        assertEquals(listOf(Place.WIN, Place.FRICTION), t.conclusions.map { it.place })
        // "waar ik …" and "wat we …" in the middle of a sentence are clauses, not questions.
        assertEquals(app.hearth.ai.route.Move.LOOK_BACK, MeetingWords.move("waar ik vond het heel goed ging was de lavabo"))
        assertEquals(app.hearth.ai.route.Move.QUESTION, MeetingWords.move("waar gaan we naartoe"))
    }

    @Test
    fun aDutchMeetingPrefersTheDutchGuess() {
        val guesses = listOf("Did You Michael Self It hebben ook het eten vergeten", "die is misschien zelf het eten vergeten")
        assertEquals("die is misschien zelf het eten vergeten", Spoken.pick(guesses, emptyList(), dutch = true))
        assertEquals(guesses.first(), Spoken.pick(guesses, emptyList()))
    }

    @Test
    fun aStretchHeardAgainOnlyAddsWhatIsNew() {
        assertEquals(null, Spoken.newPart("we moeten de was doen", "We moeten de was doen."))
        assertEquals("doen", Spoken.newPart("de was", "de was doen"))
        assertEquals(null, Spoken.newPart("morgen moeten we de was doen", "de was doen"))
        assertEquals("ook nog melk", Spoken.newPart("de was doen", "ook nog melk"))
        assertEquals("melk", Spoken.newPart(null, " melk "))
    }

    @Test
    fun choresAreNeverShopping() {
        assertEquals(true, MeetingWords.choreTalk("we moeten de was doen"))
        assertEquals(false, MeetingWords.choreTalk("wasmiddel kopen"))
        assertEquals(false, MeetingWords.choreTalk("melk halen"))
    }

    @Test
    fun aConclusionNeedsTalkThatSaysSo() {
        val cases = listOf(
            // Talk about the meeting, a question, a look back: nothing to put somewhere.
            Triple(Place.IDEA, "Ik heb een paar belangrijke punten voor vandaag.", false),
            Triple(Place.FRICTION, "Ik wilde eigenlijk niet zo veel structuur.", false),
            Triple(Place.PLANNING, "Waar is de wasmand eigenlijk?", false),
            Triple(Place.PLANNING, "Vorige week hebben we de wasmand gezocht.", false),
            Triple(Place.GOAL, "We gaan de weekplanning bekijken.", false),
            Triple(Place.SHOPPING, "De melk stond in de koelkast.", false),
            Triple(Place.HOUSE_RULE, "De schoenen staan in de gang.", false),
            Triple(Place.NONE, "We moeten de auto wassen.", false),
            // Said so.
            Triple(Place.IDEA, "Misschien kunnen we de boodschappenlijst vervangen door een app.", true),
            Triple(Place.IDEA, "Het zou handiger zijn als we samen koken, wat als we dat proberen.", true),
            Triple(Place.FRICTION, "De vuilbak loopt altijd over, dat stoort me.", true),
            Triple(Place.PLANNING, "Zaterdag moeten we een nieuwe wasmand kopen.", true),
            Triple(Place.PLANNING, "Kan jij donderdag de auto wassen?", true),
            Triple(Place.EVENT, "Mia gaat woensdag naar de zwemles.", true),
            Triple(Place.RHYTHM, "Elke avond de vaatwasser uitladen.", true),
            Triple(Place.HOUSE_RULE, "We spreken af dat de schoenen in de kast gaan.", true),
            Triple(Place.HOUSE_RULE, "Geen gsm aan tafel, nooit meer.", true),
            Triple(Place.GOAL, "Ik wil meer sporten dit jaar.", true),
            Triple(Place.SHOPPING, "We hebben nog melk en eieren nodig.", true),
            Triple(Place.SHOPPING, "Er is geen koffie meer.", true),
        )
        cases.forEach { (place, said, backed) -> assertEquals(backed, MeetingWords.backs(place, said, monday), "$place: $said") }
    }
}
