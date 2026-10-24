package app.hearth.core.tools.week

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.BoardKind
import app.hearth.core.model.BoardStatus
import app.hearth.core.model.HabitHealth
import app.hearth.core.model.Meter
import app.hearth.core.model.flowSuggestion
import app.hearth.core.model.habitHealth
import app.hearth.core.model.plusDays
import app.hearth.core.model.strainSuggestion
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolSource
import app.hearth.core.tools.backlog.AddToBacklogArgs
import app.hearth.core.tools.backlog.When
import app.hearth.core.tools.board.AddHouseRuleArgs
import app.hearth.core.tools.board.AddIdeaArgs
import app.hearth.core.tools.board.AddMemberArgs
import app.hearth.core.tools.board.FrictionOutcome
import app.hearth.core.tools.board.ResolveFrictionArgs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The Weekoverleg's look back and Luister mee: wins, how heavy, the conversation, the summary. */
class MeetingToolsTest {
    private val db = inMemoryHearthDatabase()
    private var tick = app.hearth.core.model.nowMillis()
    private val clock = { tick++ }
    private val backlog = BacklogRepository(db, clock)
    private val board = BoardRepository(db, clock)
    private val meeting = MeetingRepository(db, clock)
    private val tools = HearthTools(ShoppingRepository(db), backlog, board, StockRepository(db), WeekRepository(db), SettingsRepository(db), meeting)
    private val nl = ToolContext(ToolSource.UI, Lang.NL, today = LocalDate(2026, 10, 24))

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `wins, and two meters agreed together or left neutral`() = runTest {
        assertEquals(
            "Mooi! Genoteerd bij wat goed ging: “Elke avond samen gegeten”",
            tools.registry.run(tools.addWin, AddWinArgs("elke avond samen gegeten"), nl).summary,
        )
        val agenda = tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), nl)
        assertTrue(agenda.metersOn)
        assertEquals(4 to 1, agenda.suggestedFlow to agenda.suggestedStrain)
        assertEquals("Hoe liep het: goed. Samen afgesproken.", tools.registry.run(tools.setWeekMeter, WeekMeterArgs(Meter.FLOW, 4, suggested = 4), nl).summary)
        assertEquals(
            "Wat schuurde: geen akkoord, dus neutraal. Ook dat is een antwoord.",
            tools.registry.run(tools.setWeekMeter, WeekMeterArgs(Meter.STRAIN, null, suggested = 1), nl).summary,
        )
        assertEquals(listOf(Meter.FLOW to 4, Meter.STRAIN to 3), meeting.currentMeters().map { it.meter to it.value })
        assertFailsWith<ToolException> { tools.registry.run(tools.setWeekMeter, WeekMeterArgs(Meter.FLOW, 7), nl) }

        tools.registry.run(tools.setWeekMeters, WeekMetersArgs(false), nl)
        assertEquals(false, tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), nl).metersOn)
        assertTrue("Hoe liep het" !in tools.registry.run(tools.finishWeekoverleg, AgendaArgs(), nl).huddle.summary)
    }

    @Test
    fun `an agreement that doesn't work twice becomes a working point, never naming anyone`() = runTest {
        tools.registry.run(tools.addHouseRule, AddHouseRuleArgs("Schoenen in de kast"), nl)
        tools.registry.run(tools.addHouseRule, AddHouseRuleArgs("Wie kookt, wast niet af"), nl)
        tools.registry.run(tools.checkHouseRule, CheckHouseRuleArgs(rule = "schoenen", worked = false), nl)
        tools.registry.run(tools.checkHouseRule, CheckHouseRuleArgs(rule = "wie kookt", worked = true), nl)
        assertEquals(2, tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), nl).suggestedStrain)
        val first = tools.registry.run(tools.finishWeekoverleg, AgendaArgs(), nl).huddle.summary
        assertTrue("Afspraken die nog niet liepen: Schoenen in de kast" in first, first)
        assertEquals(0, board.allItems().count { it.kind == BoardKind.FRICTION })

        val nextWeek = nl.copy(today = LocalDate(2026, 10, 31))
        tools.registry.run(tools.checkHouseRule, CheckHouseRuleArgs(rule = "Schoenen in de kast", worked = false), nextWeek)
        tools.registry.run(tools.finishWeekoverleg, AgendaArgs(), nextWeek)
        assertEquals(listOf("Afspraak loopt nog niet: Schoenen in de kast"), board.allItems().filter { it.kind == BoardKind.FRICTION }.map { it.text })
    }

    @Test
    fun `a habit that keeps slipping asks for attention, not blame`() = runTest {
        val today = app.hearth.core.model.today()
        tick = app.hearth.core.model.nowMillis() - 40L * 86_400_000L
        val ctx = nl.copy(today = today)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Planten water geven", Section.CHORE, intervalDays = 3), ctx)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Lakens verversen", Section.CHORE, intervalDays = 14), ctx)
        tick = app.hearth.core.model.nowMillis()
        // Sheets: every two weeks, as asked. Plants: once in four weeks, every three days was asked.
        val sheets = backlog.openItems().first { it.text == "Lakens verversen" }
        val plants = backlog.openItems().first { it.text == "Planten water geven" }
        backlog.logRoutine(listOf(sheets.id), today.plusDays(-15).toString())
        backlog.logRoutine(listOf(sheets.id), today.plusDays(-1).toString())
        backlog.logRoutine(listOf(plants.id), today.plusDays(-20).toString())
        val agenda = tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), ctx)
        assertEquals(listOf("Planten water geven"), agenda.habitsNeedingAttention.map { it.text })
        assertEquals(4, agenda.suggestedFlow)
    }

    @Test
    fun `habit health reads the rhythm, and new habits get time`() {
        val today = LocalDate(2026, 10, 24)
        val weekly = app.hearth.core.model.BacklogItem("1", "Badkamer", Section.CHORE, createdAt = 0, intervalDays = 7, lastDone = "2026-10-20")
        val days = listOf(-4, -11, -18, -25).map { today.plusDays(it) }
        assertEquals(HabitHealth.EASY, weekly.habitHealth(days, today.plusDays(-60), today))
        assertEquals(HabitHealth.NEEDS_ATTENTION, weekly.copy(lastDone = "2026-10-03").habitHealth(listOf(today.plusDays(-21)), today.plusDays(-60), today))
        assertEquals(HabitHealth.NEW, weekly.habitHealth(emptyList(), today.plusDays(-5), today))
        assertEquals(5, flowSuggestion(done = 10, wins = 2, habitsNeedingAttention = 0))
        assertEquals(3, flowSuggestion(done = 0, wins = 0, habitsNeedingAttention = 2))
        assertEquals(5, strainSuggestion(newFrictions = 4, rulesNotYet = 3))
    }

    @Test
    fun `the summary stays, the transcript lasts until the next meeting`() = runTest {
        tools.registry.run(tools.addTranscriptLine, TranscriptLineArgs("we moeten de garage opruimen dit weekend"), nl)
        val agenda = tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), nl)
        assertEquals(listOf("we moeten de garage opruimen dit weekend"), agenda.transcript)
        assertTrue("Gesprek (Luister mee)" in agenda.summary)

        tools.registry.run(tools.addWin, AddWinArgs("De ochtenden gingen vlotter"), nl)
        tools.registry.run(tools.setWeekMeter, WeekMeterArgs(Meter.FLOW, 3, suggested = 5), nl)
        tools.registry.run(tools.addHouseRule, AddHouseRuleArgs("Schoenen uit in de gang"), nl)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Tandarts Mia", Section.APPOINTMENT, When.DAY, "2026-10-27"), nl)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Niks plannen, uitrusten", Section.PROTECTED_TIME, When.DAY, "2026-10-25"), nl)
        val first = tools.registry.run(tools.finishWeekoverleg, AgendaArgs(), nl).huddle
        val text = first.summary
        assertTrue(text.startsWith("Weekoverleg van vandaag (24/10)"), text)
        assertTrue("Wat ging goed: De ochtenden gingen vlotter" in text, text)
        assertTrue("Hoe liep het: oké (Hearth dacht: heel goed)" in text, text)
        assertTrue("Afgesproken: Schoenen uit in de gang" in text, text)
        assertTrue("Gepland: Tandarts Mia (dinsdag)" in text, text)
        assertTrue("Vrije tijd: Niks plannen, uitrusten (morgen)" in text, text)
        assertEquals(emptyList(), meeting.currentWins())
        assertEquals(listOf("we moeten de garage opruimen dit weekend"), meeting.transcriptOf(first.id).first().map { it.text })

        // Next week: lighter, and the old conversation goes once this meeting ends. Its summary stays.
        tools.registry.run(tools.addTranscriptLine, TranscriptLineArgs("fijn dat de garage leeg is"), nl)
        val second = tools.registry.run(tools.finishWeekoverleg, AgendaArgs(), nl.copy(today = LocalDate(2026, 10, 31))).huddle
        assertEquals(emptyList(), meeting.transcriptOf(first.id).first())
        assertEquals(listOf("fijn dat de garage leeg is"), meeting.transcriptOf(second.id).first().map { it.text })
        val listed = tools.registry.run(tools.listHuddles, ListHuddlesArgs(), nl)
        assertEquals(listOf(second.id, first.id), listed.huddles.map { it.id })
        assertTrue("Schoenen uit in de gang" in listed.summary)
    }

    @Test
    fun `clearing the conversation asks first`() = runTest {
        tools.registry.run(tools.addTranscriptLine, TranscriptLineArgs("iets"), nl)
        val ask = tools.registry.execute(ToolCall("clear_transcript", JsonObject(emptyMap())), nl.copy(source = ToolSource.CHAT))
        assertIs<ToolOutcome.NeedsConfirmation>(ask)
        assertEquals(1, meeting.currentTranscript().size)
        tools.registry.run(tools.clearTranscript, ClearTranscriptArgs(), nl, confirmed = true)
        assertEquals(0, meeting.currentTranscript().size)
    }

    @Test
    fun `an idea waits on the board and is decided like a friction`() = runTest {
        val idea = tools.registry.run(tools.addIdea, AddIdeaArgs("idee: de was op zondag"), nl)
        assertEquals("Goed idee, het staat op het werkbord: “De was op zondag”", idea.summary)
        assertEquals(BoardKind.IDEA, idea.item.kind)
        tools.registry.run(tools.resolveFriction, ResolveFrictionArgs(texts = listOf("de was op zondag"), outcome = FrictionOutcome.RULE), nl)
        assertEquals(BoardStatus.BECAME_RULE, board.allItems().first { it.kind == BoardKind.IDEA }.status)
        assertTrue(board.allItems().any { it.kind == BoardKind.RULE && it.text == "De was op zondag" })
    }
}
