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
    fun `wins and how heavy the week felt, per person`() = runTest {
        tools.registry.run(tools.addMember, AddMemberArgs("Sam"), nl)
        tools.registry.run(tools.addMember, AddMemberArgs("Lies"), nl)
        assertEquals(
            "Mooi! Genoteerd bij wat goed ging: “Elke avond samen gegeten”",
            tools.registry.run(tools.addWin, AddWinArgs("elke avond samen gegeten"), nl).summary,
        )
        tools.registry.run(tools.setWeekFeeling, WeekFeelingArgs(4, "Sam"), nl)
        assertEquals("Sam: vrij licht. Dank je om het te zeggen.", tools.registry.run(tools.setWeekFeeling, WeekFeelingArgs(2, "Sam"), nl).summary)
        tools.registry.run(tools.setWeekFeeling, WeekFeelingArgs(3, "Lies"), nl)
        assertEquals(listOf(2, 3), meeting.currentFeelings().map { it.value })
        assertFailsWith<ToolException> { tools.registry.run(tools.setWeekFeeling, WeekFeelingArgs(7), nl) }
    }

    @Test
    fun `the summary stays, the transcript lasts until the next meeting`() = runTest {
        tools.registry.run(tools.addTranscriptLine, TranscriptLineArgs("we moeten de garage opruimen dit weekend"), nl)
        val agenda = tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), nl)
        assertEquals(listOf("we moeten de garage opruimen dit weekend"), agenda.transcript)
        assertTrue("Gesprek (Luister mee)" in agenda.summary)

        tools.registry.run(tools.addWin, AddWinArgs("De ochtenden gingen vlotter"), nl)
        tools.registry.run(tools.setWeekFeeling, WeekFeelingArgs(4), nl)
        tools.registry.run(tools.addHouseRule, AddHouseRuleArgs("Schoenen uit in de gang"), nl)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Tandarts Mia", Section.APPOINTMENT, When.DAY, "2026-10-27"), nl)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Niks plannen, uitrusten", Section.PROTECTED_TIME, When.DAY, "2026-10-25"), nl)
        val first = tools.registry.run(tools.finishWeekoverleg, AgendaArgs(), nl).huddle
        val text = first.summary
        assertTrue(text.startsWith("Weekoverleg van vandaag (24/10)"), text)
        assertTrue("Wat ging goed: De ochtenden gingen vlotter" in text, text)
        assertTrue("Hoe zwaar: vrij zwaar" in text, text)
        assertTrue("Afgesproken: Schoenen uit in de gang" in text, text)
        assertTrue("Gepland: Tandarts Mia (dinsdag)" in text, text)
        assertTrue("Vrije tijd: Niks plannen, uitrusten (morgen)" in text, text)
        assertEquals(emptyList(), meeting.currentWins())
        assertEquals(listOf("we moeten de garage opruimen dit weekend"), meeting.transcriptOf(first.id).first().map { it.text })

        // Next week: lighter, and the old conversation goes once this meeting ends. Its summary stays.
        tools.registry.run(tools.addTranscriptLine, TranscriptLineArgs("fijn dat de garage leeg is"), nl)
        tools.registry.run(tools.setWeekFeeling, WeekFeelingArgs(2), nl.copy(today = LocalDate(2026, 10, 31)))
        val second = tools.registry.run(tools.finishWeekoverleg, AgendaArgs(), nl.copy(today = LocalDate(2026, 10, 31))).huddle
        assertTrue("(lichter dan vorige keer)" in second.summary, second.summary)
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
