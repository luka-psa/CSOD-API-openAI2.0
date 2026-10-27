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
import app.hearth.core.model.Ease
import app.hearth.core.model.glance
import app.hearth.core.model.easeNow
import app.hearth.core.tools.backlog.PlanBacklogArgs
import app.hearth.core.tools.backlog.RateHabitArgs
import app.hearth.core.tools.backlog.SmallStepArgs
import app.hearth.core.model.weekSuggestion
import app.hearth.core.model.WeekRating
import app.hearth.core.model.plusDays
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
    private val week = WeekRepository(db)
    private val tools = HearthTools(ShoppingRepository(db), backlog, board, StockRepository(db), week, SettingsRepository(db), meeting)
    private val nl = ToolContext(ToolSource.UI, Lang.NL, today = LocalDate(2026, 10, 24))

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `wins, and the week's weather agreed together or left open`() = runTest {
        assertEquals(
            "Mooi! Genoteerd bij wat goed ging: “Elke avond samen gegeten”",
            tools.registry.run(tools.addWin, AddWinArgs("elke avond samen gegeten"), nl).summary,
        )
        // Named under a topic, it keeps it, and the summary says where.
        tools.registry.run(tools.addWin, AddWinArgs("de ramen zijn gelapt", topic = app.hearth.core.model.Topic.HOME), nl)
        assertEquals(listOf(null, app.hearth.core.model.Topic.HOME), meeting.currentWins().map { it.topic })
        val preview = tools.registry.run(tools.finishWeekoverleg, app.hearth.core.tools.week.FinishArgs(preview = true), nl).summary
        assertTrue("Wat ging goed: Elke avond samen gegeten; Huishouden: De ramen zijn gelapt" in preview, preview)
        val agenda = tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), nl)
        assertTrue(agenda.weekOn)
        assertEquals(4, agenda.suggestedWeek)
        assertTrue("De week (samen afspreken, van 0 tot 5): voorstel zon, liep goed (4/5)." in agenda.summary, agenda.summary)
        assertEquals("De week vroeg moeite (3/5). Samen afgesproken.", tools.registry.run(tools.rateWeek, RateWeekArgs(3, suggested = 4), nl).summary)
        assertEquals(WeekRating(3, 4, true, null), meeting.currentWeek())
        // The older form still works: the weather becomes its score.
        tools.registry.run(tools.rateWeek, RateWeekArgs(ease = Ease.EFFORT, suggested = 4), nl)
        assertEquals(WeekRating(3, 4, true, null), meeting.currentWeek())
        // Saying it again replaces it; no agreement is an answer too.
        assertEquals("De week: geen akkoord. Ook dat is een antwoord.", tools.registry.run(tools.rateWeek, RateWeekArgs(null, suggested = 4), nl).summary)
        assertEquals(WeekRating(null, 4, false, null), meeting.currentWeek())

        tools.registry.run(tools.setWeekMeters, WeekMetersArgs(false), nl)
        assertEquals(false, tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), nl).weekOn)
        assertTrue("De week" !in tools.registry.run(tools.finishWeekoverleg, app.hearth.core.tools.week.FinishArgs(), nl).huddle!!.summary)
    }

    @Test
    fun `an agreement that doesn't work twice becomes a working point, never naming anyone`() = runTest {
        tools.registry.run(tools.addHouseRule, AddHouseRuleArgs("Schoenen in de kast"), nl)
        tools.registry.run(tools.addHouseRule, AddHouseRuleArgs("Wie kookt, wast niet af"), nl)
        // The same 0–5 scale as the routines; the older yes/no form still works.
        assertEquals("“Schoenen in de kast”: te zwaar (1/5). Misschien mag de huisregel eenvoudiger.",
            tools.registry.run(tools.checkHouseRule, CheckHouseRuleArgs(rule = "schoenen", score = 1), nl).summary)
        tools.registry.run(tools.checkHouseRule, CheckHouseRuleArgs(rule = "wie kookt", worked = true), nl)
        // Two house rules that ask a lot: Hearth suggests a cloud, never a bolt.
        tools.registry.run(tools.checkHouseRule, CheckHouseRuleArgs(rule = "wie kookt", score = 0), nl)
        assertEquals(3, tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), nl).suggestedWeek)
        tools.registry.run(tools.checkHouseRule, CheckHouseRuleArgs(rule = "wie kookt", score = 3), nl)
        assertEquals(listOf(1, 3), meeting.currentRuleChecks().map { it.score })
        assertEquals(listOf(Ease.HARD, Ease.EFFORT), meeting.currentRuleChecks().map { it.ease })
        // The Huisregels tile says how they go on average.
        val rules = board.allItems().filter { it.kind == BoardKind.RULE }
        assertEquals("2 huisregels · gemiddeld 2/5", app.hearth.core.model.Topic.RULES.glance(app.hearth.core.model.WeekLook(emptyList(), rules, 0, 0, nl.today, meeting.ruleScores()), Lang.NL))
        val first = tools.registry.run(tools.finishWeekoverleg, app.hearth.core.tools.week.FinishArgs(), nl).huddle!!.summary
        assertTrue("Huisregels: Schoenen in de kast: te zwaar (1/5); Wie kookt, wast niet af: vraagt moeite (3/5)" in first, first)
        assertEquals(0, board.allItems().count { it.kind == BoardKind.FRICTION })

        val nextWeek = nl.copy(today = LocalDate(2026, 10, 31))
        tools.registry.run(tools.checkHouseRule, CheckHouseRuleArgs(rule = "Schoenen in de kast", worked = false), nextWeek)
        tools.registry.run(tools.finishWeekoverleg, app.hearth.core.tools.week.FinishArgs(), nextWeek)
        assertEquals(listOf("Huisregel loopt nog niet: Schoenen in de kast"), board.allItems().filter { it.kind == BoardKind.FRICTION }.map { it.text })
    }

    @Test
    fun `a chore that keeps slipping asks for attention, not blame`() = runTest {
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
        assertEquals(listOf("Planten water geven" to Ease.EFFORT), agenda.needsAttention.map { it.item.text to it.ease })
        assertEquals(4, agenda.suggestedWeek)
    }

    @Test
    fun `habits are rated together and get a small first step, tasks count their postponements`() = runTest {
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Na het eten de tafel afruimen", Section.HABIT), nl)
        assertEquals(
            "“Na het eten de tafel afruimen”: te zwaar (1/5). Begin met de versie van twee minuten.",
            tools.registry.run(tools.rateHabit, RateHabitArgs(texts = listOf("tafel afruimen"), score = 1), nl).summary,
        )
        assertEquals("Begin klein: “Eigen bord naar de keuken”.", tools.registry.run(tools.setSmallStep, SmallStepArgs(texts = listOf("tafel afruimen"), step = "eigen bord naar de keuken"), nl).summary)
        val habit = backlog.openItems().single()
        assertEquals(Ease.HARD to "Eigen bord naar de keuken", habit.ease to habit.smallStep)

        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Fiets herstellen", Section.PROJECT, When.DAY, "2026-10-26"), nl)
        tools.registry.run(tools.planBacklogItem, PlanBacklogArgs(texts = listOf("fiets"), `when` = When.DAY, date = "2026-10-29"), nl)
        tools.registry.run(tools.planBacklogItem, PlanBacklogArgs(texts = listOf("fiets"), `when` = When.LATER), nl)
        tools.registry.run(tools.planBacklogItem, PlanBacklogArgs(texts = listOf("fiets"), `when` = When.DAY, date = "2026-11-02"), nl)
        assertEquals(2, backlog.openItems().first { it.text == "Fiets herstellen" }.postponed)
        val agenda = tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), nl)
        assertEquals(listOf("Fiets herstellen" to Ease.EFFORT), agenda.needsAttention.map { it.item.text to it.ease })
        assertEquals(listOf("Na het eten de tafel afruimen"), agenda.habits.map { it.text })
        val summary = tools.registry.run(tools.finishWeekoverleg, app.hearth.core.tools.week.FinishArgs(), nl).huddle!!.summary
        assertTrue("Ritmes & routines: Na het eten de tafel afruimen: te zwaar (1/5) (begin klein: Eigen bord naar de keuken)" in summary, summary)
        assertTrue("Vraagt aandacht: Fiets herstellen (2× verschoven)" in summary, summary)
    }

    @Test
    fun `colours read the right signal for each kind`() {
        val today = LocalDate(2026, 10, 24)
        val chore = app.hearth.core.model.BacklogItem("1", "Badkamer", Section.CHORE, createdAt = 0, intervalDays = 7, lastDone = "2026-10-20")
        val days = listOf(-4, -11, -18, -25).map { today.plusDays(it) }
        assertEquals(Ease.EASY, chore.easeNow(days, today.plusDays(-60), today))
        assertEquals(Ease.EFFORT, chore.copy(lastDone = "2026-10-03").easeNow(listOf(today.plusDays(-21)), today.plusDays(-60), today))
        assertEquals(null, chore.easeNow(emptyList(), today.plusDays(-5), today))
        val habit = app.hearth.core.model.BacklogItem("2", "Na het eten afruimen", Section.HABIT, createdAt = 0)
        assertEquals(null, habit.easeNow(emptyList(), today, today))
        assertEquals(Ease.HARD, habit.copy(ease = Ease.HARD).easeNow(emptyList(), today, today))
        val task = app.hearth.core.model.BacklogItem("3", "Belastingbrief", Section.ADMIN, createdAt = 0)
        assertEquals(listOf(null, Ease.EFFORT, Ease.HARD), listOf(1, 3, 4).map { task.copy(postponed = it).easeNow(emptyList(), today, today) })
        assertEquals(4, weekSuggestion(done = 10, wins = 2, needingAttention = 0, newFrictions = 0, rulesTooHard = 0))
        assertEquals(3, weekSuggestion(done = 0, wins = 0, needingAttention = 2, newFrictions = 0, rulesTooHard = 0))
        assertEquals(3, weekSuggestion(done = 10, wins = 2, needingAttention = 0, newFrictions = 4, rulesTooHard = 3))
    }

    @Test
    fun `the summary stays, the transcript lasts until the next meeting`() = runTest {
        tools.registry.run(tools.addTranscriptLine, TranscriptLineArgs("we moeten de garage opruimen dit weekend"), nl)
        val agenda = tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), nl)
        assertEquals(listOf("we moeten de garage opruimen dit weekend"), agenda.transcript)
        assertTrue("Gesprek (Luister mee)" in agenda.summary)

        tools.registry.run(tools.addWin, AddWinArgs("De ochtenden gingen vlotter"), nl)
        tools.registry.run(tools.rateWeek, RateWeekArgs(3, suggested = 4), nl)
        tools.registry.run(tools.addHouseRule, AddHouseRuleArgs("Schoenen uit in de gang"), nl)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Tandarts Mia", Section.APPOINTMENT, When.DAY, "2026-10-27"), nl)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Niks plannen, uitrusten", Section.PROTECTED_TIME, When.DAY, "2026-10-25"), nl)
        val first = tools.registry.run(tools.finishWeekoverleg, app.hearth.core.tools.week.FinishArgs(), nl).huddle!!
        val text = first.summary
        assertTrue(text.startsWith("Weekoverleg van vandaag (24/10)"), text)
        assertTrue("Wat ging goed: De ochtenden gingen vlotter" in text, text)
        assertTrue("De week: vroeg moeite (3/5) (Hearth dacht: liep goed (4/5))" in text, text)
        assertTrue("Nieuwe huisregels: Schoenen uit in de gang" in text, text)
        assertTrue("Admin & afspraken: Tandarts Mia (dinsdag)" in text, text)
        assertTrue("Vrije tijd: Niks plannen, uitrusten (morgen)" in text, text)
        assertEquals(emptyList(), meeting.currentWins())
        assertEquals(listOf("we moeten de garage opruimen dit weekend"), meeting.transcriptOf(first.id).first().map { it.text })

        // Next week: lighter, and the old conversation goes once this meeting ends. Its summary stays.
        tools.registry.run(tools.addTranscriptLine, TranscriptLineArgs("fijn dat de garage leeg is"), nl)
        val second = tools.registry.run(tools.finishWeekoverleg, app.hearth.core.tools.week.FinishArgs(), nl.copy(today = LocalDate(2026, 10, 31))).huddle!!
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
    fun `misheard words are fixed, and the family's AI reads the conversation and marks what it placed`() = runTest {
        val line = tools.registry.run(tools.addTranscriptLine, TranscriptLineArgs("dus die moet oprecht worden"), nl).line
        tools.registry.run(tools.addTranscriptLine, TranscriptLineArgs("deze week moet ik afspreken met Anthony"), nl)
        val fixed = tools.registry.run(tools.fixTranscriptLine, FixLineArgs(line.id, " dus die moet opgehaald worden "), nl)
        assertEquals("Verbeterd: “dus die moet opgehaald worden”", fixed.summary)
        assertEquals("dus die moet opgehaald worden", meeting.currentTranscript().first().text)

        val read = tools.registry.run(tools.readConversation, ReadConversationArgs(), nl)
        assertEquals(listOf("dus die moet opgehaald worden", "deze week moet ik afspreken met Anthony"), read.lines.map { it.text })
        assertTrue(read.summary.startsWith("2 zinnen nog niet op hun plek:"))
        // Over MCP: read, add, then mark what was handled.
        val overMcp = tools.registry.specs.filter { it.exposeOverMcp }.map { it.name }
        assertTrue("read_conversation" in overMcp && "mark_transcript_sorted" in overMcp && "fix_transcript_line" in overMcp)
        tools.registry.run(tools.markTranscriptSorted, MarkSortedArgs(read.lines.map { it.id }), nl)
        assertEquals("Alles uit het gesprek staat al op zijn plek.", tools.registry.run(tools.readConversation, ReadConversationArgs(), nl).summary)

        // A meeting that ended keeps its words as they were.
        tools.registry.run(tools.finishWeekoverleg, FinishArgs(), nl)
        val gone = runCatching { tools.registry.run(tools.fixTranscriptLine, FixLineArgs(line.id, "iets anders"), nl) }.exceptionOrNull()
        assertIs<ToolException>(gone)
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

    @Test
    fun `a preview writes the summary to agree on without recording the meeting`() = runTest {
        tools.registry.run(tools.addWin, AddWinArgs("Samen gegeten"), nl)
        val preview = tools.registry.run(tools.finishWeekoverleg, app.hearth.core.tools.week.FinishArgs(preview = true), nl)
        assertEquals(null, preview.huddle)
        assertTrue(preview.lines.any { it.startsWith("Wat ging goed: Samen gegeten") }, preview.summary)
        assertEquals(1, meeting.currentWins().size)
        assertEquals(null, week.last())
    }

    @Test
    fun `ratings from before the scale read as their weather's score`() {
        val score = app.hearth.core.data.week.ReflectionEntity::scoreOf
        // Old rows: 1 loopt vanzelf, 2 vraagt moeite, 0 te zwaar (and empty); new rows hold 10 + the score.
        assertEquals(listOf(5, 3, 1, 1), listOf(1, 2, 0, null).map(score))
        assertEquals((0..5).toList(), (0..5).map { score(app.hearth.core.data.week.ReflectionEntity.codeOf(it)) })
    }
}
