package app.hearth.ai.meeting

import app.hearth.ai.Orchestrator
import app.hearth.ai.interpret.Action
import app.hearth.ai.route.Place
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.model.Anchor
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.model.Topic
import app.hearth.core.model.WordFix
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolJson
import app.hearth.core.tools.ToolSource
import app.hearth.core.tools.backlog.PlanBacklogArgs
import app.hearth.core.tools.backlog.SetAnchorArgs
import app.hearth.core.tools.food.AddShoppingItemArgs
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The meeting read live (what each line is about) and as a whole afterwards (what was concluded, next to what exists). */
class MeetingProcessorTest {
    private val db = inMemoryHearthDatabase()
    private val tools = HearthTools(
        app.hearth.core.data.food.ShoppingRepository(db), app.hearth.core.data.backlog.BacklogRepository(db), app.hearth.core.data.board.BoardRepository(db),
        app.hearth.core.data.food.StockRepository(db), app.hearth.core.data.week.WeekRepository(db), app.hearth.core.data.settings.SettingsRepository(db),
        app.hearth.core.data.week.MeetingRepository(db),
    )
    private val orchestrator = Orchestrator(tools.registry)
    private val processor = MeetingProcessor(orchestrator)
    // 22 October 2026 is a Thursday: "zaterdag" is the 24th, "vrijdag" the 23rd.
    private val nl = ToolContext(ToolSource.UI, Lang.NL, today = LocalDate(2026, 10, 22))

    @AfterTest
    fun tearDown() = db.close()

    private fun lines(vararg text: String) = text.mapIndexed { i, t -> HeardLine("l$i", t, 1_000L * i) }
    private fun task(id: String, text: String, section: Section, anchor: Anchor? = null) = BacklogItem(id = id, text = text, section = section, createdAt = 0, anchor = anchor)
    private fun board(id: String, text: String, kind: BoardKind) = BoardItem(id = id, kind = kind, text = text, createdAt = 0)

    private val household = Household(
        people = listOf("Mia", "Jules"),
        backlog = listOf(
            task("insurance", "Verzekering bellen", Section.ADMIN),
            task("dishwasher", "Vaatwasser uitladen", Section.CHORE),
        ),
        board = listOf(board("shoes", "Schoenen in de kast", BoardKind.RULE), board("yoga", "Terug yoga doen", BoardKind.GOAL)),
        shopping = listOf("Melk"),
        dishes = listOf("Spaghetti"),
        words = listOf(WordFix("vaat was", "vaatwasser"), WordFix("Jaules", "Jules")),
    )

    /** A scripted interpreter: what it answers per window, and the windows it was given. */
    private class Script(val answer: (List<String>) -> List<Action>) {
        val windows = mutableListOf<List<String>>()
        suspend fun read(lines: List<String>): List<Action> = answer(lines).also { windows += lines }
    }

    @Test
    fun `live, each line shows names, what the household has and topic words, with its own words put right`() {
        val intake = MeetingIntake(household)
        val dishes = intake.read("dan zet Jaules de vaat was aan")
        assertEquals("dan zet Jules de vaatwasser aan", dishes.text)
        assertEquals(listOf("Jules" to null, "vaatwasser" to "dishwasher"), dishes.marks.map { dishes.text.substring(it.start, it.end) to it.known?.id })
        assertEquals(Topic.HOME, dishes.topic)

        val cases = listOf(
            "we moeten de boodschappen eens anders aanpakken" to Topic.SHOPPING,
            "ik wil heel graag terug yoga doen" to Topic.GOALS,
            "donderdagavond ga ik squashen" to Topic.FREE_TIME,
            "jij denkt na over vijf gerechten" to Topic.MENU,
            "de handdoek blijft altijd op de grond liggen" to Topic.HOME,
            "wanneer bel ik de verzekering" to Topic.ADMIN,
            "ja nee dat klopt" to null,
        )
        cases.forEach { (line, topic) -> assertEquals(topic, intake.read(line).topic, line) }

        // Beside it: what the meeting is about so far, by topic, with the things named.
        val so = seen(listOf(dishes) + cases.map { intake.read(it.first) })
        assertEquals(Topic.ADMIN to listOf("Verzekering bellen"), so.first().topic to so.first().known.map { it.text })
        assertEquals(listOf("Vaatwasser uitladen"), so.first { it.topic == Topic.HOME }.known.map { it.text })
        assertEquals(listOf("handdoek"), so.first { it.topic == Topic.HOME }.words)
    }

    @Test
    fun `a conclusion about something the household has changes it instead of adding it twice`() = runTest {
        val script = Script { window ->
            buildList {
                if (window.any { "verzekering" in it }) add(Action("add_task", mapOf("text" to "Verzekering bellen", "day" to "zaterdag")))
                if (window.any { "vaatwasser" in it }) add(Action("add_routine", mapOf("text" to "Vaatwasser uitladen", "when" to "na het eten")))
                if (window.any { "schoenen" in it }) add(Action("add_house_rule", mapOf("text" to "Schoenen in de kast")))
                if (window.any { "melk" in it }) add(Action("add_shopping", mapOf("items" to "melk, eieren")))
            }
        }
        val done = processor.process(
            lines(
                "zaterdag bel ik de verzekering",
                "de vaat was moet uitgeladen worden na het eten",
                "de schoenen moeten in de kast hè",
                "we hebben nog melk en eieren nodig",
            ),
            household, nl, interpret = script::read,
        )
        val byPlace = done.found.associate { it.proposal.place to it.proposal }

        val plan = byPlace.getValue(Place.PLANNING)
        assertEquals("insurance", plan.match?.id)
        assertTrue(plan.match!!.update)
        assertEquals("2026-10-24", ToolJson.decodeFromJsonElement(PlanBacklogArgs.serializer(), plan.calls.single().arguments).date)

        val routine = byPlace.getValue(Place.RHYTHM)
        assertEquals("dishwasher" to Anchor.DINNER, routine.match?.id to ToolJson.decodeFromJsonElement(SetAnchorArgs.serializer(), routine.calls.single().arguments).anchor)

        val rule = byPlace.getValue(Place.HOUSE_RULE)
        assertEquals("shoes", rule.match?.id)
        assertEquals(false, rule.match!!.update)
        assertTrue(rule.calls.isEmpty() && rule.match!!.asNew.isNotEmpty())

        // Milk is on the list already: only the eggs.
        assertEquals(listOf("Eieren"), byPlace.getValue(Place.SHOPPING).calls.map { ToolJson.decodeFromJsonElement(AddShoppingItemArgs.serializer(), it.arguments).name.replaceFirstChar(Char::uppercase) })
    }

    @Test
    fun `made-up words and half thoughts are dropped, and the last word on a plan counts`() = runTest {
        val script = Script { window ->
            buildList {
                if (window.any { it == "wil dat jij" }) add(Action("add_goal", mapOf("text" to "Guisen")))
                if (window.any { "kan dat" in it }) add(Action("add_task", mapOf("text" to "Kan dat")))
                if (window.any { "zaterdag pizza" in it }) add(Action("plan_meal", mapOf("dish" to "Pizza", "day" to "zaterdag")))
                if (window.any { "toch maar vrijdag" in it }) add(Action("plan_meal", mapOf("dish" to "Pizza", "day" to "vrijdag")))
            }
        }
        val done = processor.process(
            lines("wil dat jij", "we eten zaterdag pizza", "ja misschien kan dat", "nee we eten de pizza toch maar vrijdag"),
            household, nl, interpret = script::read,
        )
        assertEquals(listOf(Triple(Place.MEAL, "Pizza", LocalDate(2026, 10, 23))), done.found.map { Triple(it.proposal.place, it.proposal.text, it.proposal.date) })
        // The pizza rests on both lines it was said in.
        assertEquals(listOf(1, 3), done.found.single().lines)
        assertNotNull(done.raw)
    }

    @Test
    fun `a topic that comes back later is read together with what was said about it first`() = runTest {
        val script = Script { emptyList() }
        var progress = 0 to 0
        val done = processor.process(
            lines("donderdagavond ga ik squashen", "de vaatwasser moet leeg voor het koken", "de wasmand staat altijd boven", "squash is om acht uur"),
            household, nl, interpret = script::read, progress = { d, t -> progress = d to t },
        )
        assertEquals(listOf(Topic.FREE_TIME to listOf(0, 3), Topic.HOME to listOf(1, 2)), done.parts.map { it.topic to it.lines })
        assertTrue(script.windows.any { it.size == 2 && "squash" in it[1] })
        assertEquals(2 to 2, progress)
        // Nothing concluded: every thought is just talk, kept once.
        assertTrue(done.found.isEmpty() && done.quiet.isNotEmpty())
    }

    @Test
    fun `without the interpreter the rules conclude, and they are held against what exists the same way`() = runTest {
        val done = processor.process(lines("we spreken af dat de schoenen in de kast gaan", "donderdag moet de auto naar de garage"), household, nl)
        val rule = done.found.first { it.proposal.place == Place.HOUSE_RULE }.proposal
        assertEquals("shoes", rule.match?.id)
        val car = done.found.first { it.proposal.place != Place.HOUSE_RULE }.proposal
        assertEquals(null, car.match)
        // Said on a Thursday, "donderdag" is next week's.
        assertEquals(LocalDate(2026, 10, 29), car.date)
        assertEquals(null, done.raw)
    }

    @Test
    fun `what the interpreter makes of plain talk is dropped, what was really said stays`() = runTest {
        val script = Script { window ->
            buildList {
                if (window.any { "punten" in it }) add(Action("add_idea", mapOf("text" to "Een belangrijke punten")))
                if (window.any { "structuur" in it }) add(Action("log_friction", mapOf("text" to "Een structuur niet wilde")))
                if (window.any { "wasmand" in it }) add(Action("add_task", mapOf("text" to "De wasmand zoeken")))
                if (window.any { "boodschappenlijst" in it }) add(Action("add_idea", mapOf("text" to "De boodschappenlijst vervangen door een app")))
                if (window.any { "vuilbak" in it }) add(Action("log_friction", mapOf("text" to "De vuilbak loopt altijd over")))
            }
        }
        val done = processor.process(
            lines(
                "Ik heb een paar belangrijke punten. Ik wilde eigenlijk niet zo veel structuur.",
                "Waar zou de wasmand zijn? Misschien kunnen we de boodschappenlijst vervangen door een app.",
                "De vuilbak loopt altijd over, dat stoort me.",
            ),
            household, nl, interpret = script::read,
        )
        assertEquals(
            listOf(Place.FRICTION to "De vuilbak loopt altijd over", Place.IDEA to "De boodschappenlijst vervangen door een app"),
            done.found.map { it.proposal.place to it.proposal.text }.sortedBy { it.first.ordinal },
        )
        // The interpreter was given sentences, as it learned, and its suggestion rests on the one it came from.
        assertTrue(script.windows.flatten().contains("Misschien kunnen we de boodschappenlijst vervangen door een app."))
        assertEquals("Misschien kunnen we de boodschappenlijst vervangen door een app.", done.found.first { it.proposal.place == Place.IDEA }.proposal.heard)
    }

    @Test
    fun `long stretches are read sentence by sentence, all of them, in windows the model can take`() = runTest {
        val script = Script { emptyList() }
        // Whisper writes up to half a minute as one line: six sentences each, and one long run-on sentence.
        val sentences = (1..24).map { n -> "Dan hebben we het over nummer $n gehad en dat was het wel zo een beetje." }
        val runOn = (1..80).joinToString(" ") { if (it % 10 == 0) "woord$it," else "woord$it" }
        val talk = sentences.chunked(6).map { it.joinToString(" ") } + runOn
        processor.process(lines(*talk.toTypedArray()), household, nl, interpret = script::read)

        val read = script.windows.flatten()
        sentences.forEach { s -> assertTrue(s in read, s) }
        assertTrue(script.windows.all { w -> w.size <= 16 && w.sumOf { it.split(" ").size } <= 130 })
        // The run-on sentence in pieces at its commas, every word of it read.
        val pieces = read.filter { it.startsWith("woord") }.distinct()
        assertTrue(pieces.size >= 3 && pieces.all { it.split(" ").size <= 35 })
        assertEquals(runOn, pieces.joinToString(" "))
    }
}
