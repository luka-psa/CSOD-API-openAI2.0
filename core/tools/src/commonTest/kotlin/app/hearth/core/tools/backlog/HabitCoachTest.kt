package app.hearth.core.tools.backlog

import app.hearth.core.model.Anchor
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.BoardStatus
import app.hearth.core.model.Ease
import app.hearth.core.model.HabitLaw
import app.hearth.core.model.HabitStage
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.model.TipAction
import app.hearth.core.model.habitReview
import app.hearth.core.model.isAbout
import app.hearth.core.model.lawOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** The habit coach: which law a friction points to, which frictions are about a habit, and what it suggests. */
class HabitCoachTest {
    private fun habit(text: String = "De tafel afruimen", anchor: Anchor? = null, smallStep: String? = null, pairing: String? = null, interval: Int? = 1, ease: Ease? = null) =
        BacklogItem("h1", text, Section.HABIT, createdAt = 0, intervalDays = interval, anchor = anchor, smallStep = smallStep, pairing = pairing, ease = ease)

    private fun friction(text: String, open: Boolean = true, linkedId: String? = null, kind: BoardKind = BoardKind.FRICTION) =
        BoardItem("f-$text", kind, text, if (open) BoardStatus.OPEN else BoardStatus.BECAME_ROUTINE, linkedId = linkedId, createdAt = 1, closedAt = if (open) null else 2)

    @Test
    fun `a friction's words point to a law`() {
        val table = listOf(
            "We vergeten het altijd" to HabitLaw.OBVIOUS,
            "Niemand denkt eraan" to HabitLaw.OBVIOUS,
            "Geen zin om af te ruimen" to HabitLaw.ATTRACTIVE,
            "Het is zo saai" to HabitLaw.ATTRACTIVE,
            "Te veel stappen 's avonds" to HabitLaw.EASY,
            "Geen tijd na het werk" to HabitLaw.EASY,
            "Niemand ziet dat ik het doe" to HabitLaw.SATISFYING,
            "Het heeft geen zin" to HabitLaw.SATISFYING,
            "We always forget the vitamins" to HabitLaw.OBVIOUS,
            "It's boring" to HabitLaw.ATTRACTIVE,
            "Too much in the evening" to HabitLaw.EASY,
            "Nobody notices" to HabitLaw.SATISFYING,
            "De vaatwasser staat vol" to null,
        )
        table.forEach { (words, law) -> assertEquals(law, lawOf(words), words) }
    }

    @Test
    fun `frictions about a habit name it or were written for it`() {
        val table = listOf(
            Triple("Tanden poetsen", friction("Niemand poetst zijn tanden"), true),
            Triple("De tafel afruimen", friction("Na het eten blijft de tafel vol"), true),
            Triple("Avondroutine", friction("Het avondeten is te laat"), false),
            Triple("De tafel afruimen", friction("Iets anders", open = false, linkedId = "h1"), true),
            Triple("De tafel afruimen", friction("De tafel blijft vol", open = false), false),
            Triple("De tafel afruimen", friction("Tafel mooi dekken", kind = BoardKind.GOAL), false),
        )
        table.forEach { (name, f, about) -> assertEquals(about, f.isAbout(habit(name)), "$name / ${f.text}") }
    }

    @Test
    fun `new habits start with a moment and the two-minute version`() {
        val r = habitReview(habit(), emptyList(), emptyList(), 0, null, Lang.NL)
        assertEquals(HabitStage.NEW, r.stage)
        assertEquals(listOf(TipAction.MOMENT, TipAction.SMALL_STEP), r.tips.map { it.action })
        assertEquals(listOf(null, null, null, null), r.laws.map { it.now })
    }

    @Test
    fun `too much makes it smaller, effort changes the law the friction points to`() {
        // Too much: the two-minute version first; with one already and many steps, fewer steps.
        assertEquals(TipAction.SMALL_STEP, habitReview(habit(), listOf(Ease.EASY, Ease.HARD), emptyList(), 0, null, Lang.NL).tips.first().action)
        val smaller = habitReview(habit(smallStep = "Alleen je bord"), listOf(Ease.HARD), emptyList(), 5, null, Lang.NL)
        assertEquals(HabitStage.SHRINK, smaller.stage)
        assertEquals(TipAction.FEWER_STEPS, smaller.tips.first().action)

        // "We vergeten het": make it obvious. Without a moment, a moment; with one and weekly, a fixed day.
        val forgot = listOf(friction("We vergeten de tafel af te ruimen"))
        val r = habitReview(habit(), listOf(Ease.EFFORT), forgot, 0, null, Lang.NL)
        assertEquals(HabitStage.ADJUST, r.stage)
        assertEquals(HabitLaw.OBVIOUS, r.focus)
        assertEquals(TipAction.MOMENT, r.tips.first().action)
        assertEquals(listOf("We vergeten de tafel af te ruimen"), r.frictions)
        assertEquals(TipAction.DAYS, habitReview(habit(anchor = Anchor.DINNER, interval = 7), listOf(Ease.EFFORT), forgot, 0, null, Lang.NL).tips.first().action)

        // An open friction asks for a change even when the last weather was sun.
        assertEquals(HabitStage.ADJUST, habitReview(habit(anchor = Anchor.DINNER), listOf(Ease.EASY), listOf(friction("Geen zin in de tafel")), 0, null, Lang.NL).stage)
    }

    @Test
    fun `three sunny meetings grow it a step, otherwise it is left alone`() {
        val grown = habitReview(habit(smallStep = "Alleen je bord"), listOf(Ease.HARD, Ease.EASY, Ease.EASY, Ease.EASY), emptyList(), 0, null, Lang.NL)
        assertEquals(HabitStage.GROW, grown.stage)
        assertEquals(listOf(TipAction.WHOLE, TipAction.CELEBRATE), grown.tips.map { it.action })
        assertEquals(TipAction.MORE_OFTEN, habitReview(habit(interval = 7), List(3) { Ease.EASY }, emptyList(), 0, null, Lang.NL).tips.first().action)

        val steady = habitReview(habit(anchor = Anchor.DINNER), listOf(Ease.EASY), emptyList(), 0, null, Lang.NL)
        assertEquals(HabitStage.STEADY, steady.stage)
        assertEquals(emptyList(), steady.tips)
    }

    @Test
    fun `the four laws say what is in place`() {
        val r = habitReview(habit(anchor = Anchor.DINNER, smallStep = "Alleen je bord", pairing = "met muziek op"), listOf(Ease.EASY, Ease.EFFORT, Ease.EASY), emptyList(), 0, null, Lang.NL)
        assertEquals(listOf("Rond het eten", "met muziek op", "Begin klein: Alleen je bord", "2× liep het vanzelf"), r.laws.map { it.now })
        // Without a pairing, the goal it serves makes it attractive.
        assertEquals("Voor ons doel: Fitter worden", habitReview(habit(), emptyList(), emptyList(), 0, "Fitter worden", Lang.NL).laws[1].now)
        // A rating from before the history was kept still counts.
        val old = habitReview(habit(ease = Ease.HARD), emptyList(), emptyList(), 0, null, Lang.NL)
        assertEquals(HabitStage.SHRINK, old.stage)
        assertEquals(listOf(Ease.HARD), old.ratings)
    }
}
