package app.hearth.core.tools.backlog

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.Anchor
import app.hearth.core.model.Ease
import app.hearth.core.model.FrictionDirection
import app.hearth.core.model.HabitLaw
import app.hearth.core.model.HabitStage
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolSource
import app.hearth.core.tools.board.FrictionOutcome
import app.hearth.core.tools.board.LogFrictionArgs
import app.hearth.core.tools.board.MarkFrictionArgs
import app.hearth.core.tools.board.ResolveFrictionArgs
import app.hearth.core.tools.week.AgendaArgs
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The habit coach as tools: one rating per meeting, something nice with it, and the four laws in review_habit. */
class HabitToolsTest {
    private val db = inMemoryHearthDatabase()
    private val backlog = BacklogRepository(db)
    private var clock = 1_000L
    private val meeting = MeetingRepository(db) { clock++ }
    private val tools = HearthTools(ShoppingRepository(db), backlog, BoardRepository(db), StockRepository(db), WeekRepository(db), SettingsRepository(db), meeting)
    private val ctx = ToolContext(ToolSource.UI, Lang.NL, today = LocalDate(2026, 10, 25))

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun table() = tools.registry.run(tools.addToBacklog, AddToBacklogArgs("De tafel afruimen", Section.HABIT, anchor = Anchor.DINNER), ctx).item

    @Test
    fun `each meeting keeps one rating per habit`() = runTest {
        val habit = table()
        tools.registry.run(tools.rateHabit, RateHabitArgs(itemIds = listOf(habit.id), ease = Ease.EFFORT), ctx)
        // Said again in the same meeting: it replaces.
        tools.registry.run(tools.rateHabit, RateHabitArgs(texts = listOf("tafel afruimen"), ease = Ease.HARD), ctx)
        assertEquals(listOf(Ease.HARD), meeting.habitHistory(habit.id))
        meeting.close("2026-10-18", "Weekoverleg")
        tools.registry.run(tools.rateHabit, RateHabitArgs(itemIds = listOf(habit.id), ease = Ease.EASY), ctx)
        assertEquals(listOf(Ease.HARD, Ease.EASY), meeting.habitHistory(habit.id))
        assertEquals(Ease.EASY, backlog.openItems().first { it.id == habit.id }.ease)
    }

    @Test
    fun `review_habit reads the four laws and suggests one thing`() = runTest {
        val habit = table()
        tools.registry.run(tools.rateHabit, RateHabitArgs(itemIds = listOf(habit.id), ease = Ease.HARD), ctx)
        val review = tools.registry.run(tools.reviewHabit, ReviewHabitArgs(text = "tafel"), ctx)
        assertEquals(HabitStage.SHRINK, review.reviews.single().stage)
        assertEquals(
            "“De tafel afruimen” · Te zwaar: maak het kleiner, niet harder.\n" +
                "Weer per overleg: bliksem\n" +
                "Zichtbaar: Rond het eten · Aantrekkelijk: nog niets · Makkelijk: nog niets · Voldoening: nog niets\n" +
                "Probeer (Makkelijk): Begin met de versie van twee minuten.",
            review.summary,
        )
        // Without a habit: only those asking for something. A new habit with a moment and a small step simply runs.
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Voorlezen", Section.RHYTHM, anchor = Anchor.BEDTIME), ctx)
        tools.registry.run(tools.setSmallStep, SmallStepArgs(texts = listOf("voorlezen"), step = "Eén bladzijde"), ctx)
        assertEquals(listOf("De tafel afruimen"), tools.registry.run(tools.reviewHabit, ReviewHabitArgs(), ctx).items.map { it.text })
        // The agenda carries the coach for the meeting (and for a family's AI).
        assertTrue(
            "Gewoontes (vier wetten, review_habit): De tafel afruimen – Makkelijk: Begin met de versie van twee minuten." in
                tools.registry.run(tools.weekoverlegAgenda, AgendaArgs(), ctx).summary,
        )
    }

    @Test
    fun `something nice with a habit`() = runTest {
        val habit = table()
        assertEquals(
            "“De tafel afruimen”, en erbij: met muziek op.",
            tools.registry.run(tools.setHabitPairing, SetPairingArgs(itemIds = listOf(habit.id), pairing = "Met muziek op."), ctx).summary,
        )
        assertEquals("met muziek op", backlog.openItems().first { it.id == habit.id }.pairing)
        val review = tools.registry.run(tools.reviewHabit, ReviewHabitArgs(itemId = habit.id), ctx).reviews.single()
        assertEquals("met muziek op", review.laws.first { it.law == HabitLaw.ATTRACTIVE }.now)
        assertEquals("“De tafel afruimen” zonder iets erbij.", tools.registry.run(tools.setHabitPairing, SetPairingArgs(itemIds = listOf(habit.id)), ctx).summary)
    }

    @Test
    fun `a friction about a habit changes it along a law and stays with it`() = runTest {
        val habit = table()
        tools.registry.run(tools.logFriction, LogFrictionArgs("Geen tijd om de tafel af te ruimen"), ctx)
        // Still open and naming it: the coach already hears it.
        val open = tools.registry.run(tools.reviewHabit, ReviewHabitArgs(itemId = habit.id), ctx).reviews.single()
        assertEquals(HabitStage.ADJUST, open.stage)
        assertEquals(HabitLaw.EASY, open.focus)

        tools.registry.run(tools.markFriction, MarkFrictionArgs(texts = listOf("geen tijd"), direction = FrictionDirection.CHANGE_ROUTINE), ctx)
        assertEquals(
            "“Geen tijd om de tafel af te ruimen”: “De tafel afruimen” past zich aan (begin klein: Alleen je eigen bord)",
            tools.registry.run(
                tools.resolveFriction,
                ResolveFrictionArgs(texts = listOf("geen tijd"), outcome = FrictionOutcome.ROUTINE_CHANGE, routine = "de tafel afruimen", smallStep = "alleen je eigen bord"),
                ctx,
            ).summary,
        )
        val changed = backlog.openItems().first { it.id == habit.id }
        assertEquals("Alleen je eigen bord", changed.smallStep)
        // Written, it is linked to the habit: still part of its story, no longer asking for a change.
        val after = tools.registry.run(tools.reviewHabit, ReviewHabitArgs(itemId = habit.id), ctx).reviews.single()
        assertEquals(listOf("Geen tijd om de tafel af te ruimen"), after.frictions)
        assertEquals(HabitStage.NEW, after.stage)
    }
}
