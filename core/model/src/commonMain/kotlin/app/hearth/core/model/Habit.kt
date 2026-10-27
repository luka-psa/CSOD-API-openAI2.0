package app.hearth.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// The habit coach: how a habit is going (its 0–5 score per meeting) and, only when it needs one, one thing to change,
// chosen along the four laws of Atomic Habits (make it obvious, attractive, easy, satisfying) from its scores and the
// frictions talked about it. A habit starts small and only
// grows once it runs by itself; the coach never counts streaks and never says "failed".

/** What makes a habit stick (James Clear, Atomic Habits). */
enum class HabitLaw {
    /** Zichtbaar: a moment or a day, something you see. */
    OBVIOUS,
    /** Aantrekkelijk: something nice with it, or together. */
    ATTRACTIVE,
    /** Makkelijk: the two-minute version, fewer steps, less often. */
    EASY,
    /** Voldoening: it feels good right after, and someone says so. */
    SATISFYING,
}

fun HabitLaw.title(lang: Lang): String = when (this) {
    HabitLaw.OBVIOUS -> if (lang == Lang.NL) "Zichtbaar" else "Obvious"
    HabitLaw.ATTRACTIVE -> if (lang == Lang.NL) "Aantrekkelijk" else "Attractive"
    HabitLaw.EASY -> if (lang == Lang.NL) "Makkelijk" else "Easy"
    HabitLaw.SATISFYING -> if (lang == Lang.NL) "Voldoening" else "Satisfying"
}

/** Where a habit stands, from its weather and what was said about it. */
enum class HabitStage {
    /** Not rated yet: hang it on a moment and start with the two-minute version. */
    NEW,
    /** Too much last time: smaller, not harder. */
    SHRINK,
    /** Takes effort, or a friction about it is still open: change one thing. */
    ADJUST,
    /** It runs; nothing to change. */
    STEADY,
    /** Ran by itself three meetings in a row: one step bigger, if the family wants. */
    GROW,
}

fun HabitStage.line(lang: Lang): String {
    val nl = lang == Lang.NL
    return when (this) {
        HabitStage.NEW -> if (nl) "Nieuw: begin klein en hang het aan een moment." else "New: start small and hang it on a moment."
        HabitStage.SHRINK -> if (nl) "Te zwaar: maak het kleiner, niet harder." else "Too much: make it smaller, not harder."
        HabitStage.ADJUST -> if (nl) "Vraagt moeite: verander één ding." else "Takes effort: change one thing."
        HabitStage.STEADY -> if (nl) "Het loopt. Niets aan veranderen." else "It runs. Nothing to change."
        HabitStage.GROW -> if (nl) "Loopt vanzelf: klaar voor een stap groter?" else "Runs by itself: ready for a step bigger?"
    }
}

/** What one tap on a tip does. */
enum class TipAction {
    /** Hang it on a moment of the day, or another one. */
    MOMENT,
    /** Give a weekly habit its own day. */
    DAYS,
    /** Something nice with it ([BacklogItem.pairing]). */
    PAIR,
    /** A smaller first step ([BacklogItem.smallStep]). */
    SMALL_STEP,
    /** Half as often, for now. */
    LESS_OFTEN,
    /** Open the routine to take a step out. */
    FEWER_STEPS,
    /** Say it in Wat ging goed. */
    CELEBRATE,
    /** The small step did its work: back to the whole habit. */
    WHOLE,
    /** Twice as often. */
    MORE_OFTEN,
    /** One step more. */
    ADD_STEP,
}

/** One thing to try, under one law; growing a habit has no law of its own ([law] null). */
@Serializable
data class HabitTip(val law: HabitLaw?, val action: TipAction, val text: String)

/** "Zichtbaar", or "Een stap groter" for a habit ready to grow. */
fun HabitTip.tag(lang: Lang): String = law?.title(lang) ?: if (lang == Lang.NL) "Een stap groter" else "A step bigger"

/** How one law is in place now ("Rond het eten", "Begin klein: …"); null: not yet. */
@Serializable
data class LawState(val law: HabitLaw, val now: String?)

@Serializable
data class HabitReview(
    val itemId: String,
    val stage: HabitStage,
    /** Its score (0–5) per meeting, oldest first (the meeting still to come last). */
    val ratings: List<Int>,
    /** What was said about it: frictions and ideas, oldest first. */
    val frictions: List<String>,
    /** The law the latest friction points to; null when nothing was said, or nothing clear. */
    val focus: HabitLaw?,
    val laws: List<LawState>,
    /** At most two, the first is the one to try. Empty when it simply runs. */
    val tips: List<HabitTip>,
) {
    /** The last score, if it was rated. */
    val score: Int? get() = ratings.lastOrNull()

    /** Up (1), down (-1) or the same (0) since the meeting before; null with fewer than two scores. */
    val trend: Int? get() = if (ratings.size < 2) null else ratings.last().compareTo(ratings[ratings.size - 2]).coerceIn(-1, 1)

    /** Whether it needs a change now: too much, takes effort, talked about, or ready to grow. */
    val needsChange: Boolean get() = tips.isNotEmpty() && stage != HabitStage.STEADY && stage != HabitStage.NEW
}

/** Which law a friction points to, from its words: "we vergeten het" is about seeing it, "te veel" about ease. */
fun lawOf(text: String): HabitLaw? {
    val words = text.lowercase()
    return LAW_WORDS.firstOrNull { (_, pattern) -> pattern.containsMatchIn(words) }?.first
}

// Checked in this order: "heeft geen zin" (no point) is about satisfaction before "geen zin" (no wish) is about attraction.
private val LAW_WORDS = listOf(
    HabitLaw.SATISFYING to Regex(
        "niemand (ziet|merkt)|niet gezien|ondankbaar|voor niets|voor niks|geen resultaat|heeft geen zin|geen nut|nutteloos|" +
            "nobody (sees|notices)|unnoticed|pointless|no point|thankless",
    ),
    HabitLaw.OBVIOUS to Regex(
        "verge(et|ten)|vergat|denk(t|en)? er ?(niet )?aan|niet aan gedacht|onthoud|herinner|kwijt|uit het oog|" +
            "forg[eo]t|remember|slip(s|ped)? (my|our) mind|out of sight",
    ),
    HabitLaw.ATTRACTIVE to Regex(
        "geen zin|saai|vervelend|hekel|stom|zeur|zaag|niemand wil|tegenzin|niet leuk|" +
            "boring|hate|annoying|(no ?one|nobody) wants|no fun|not fun|don'?t feel like",
    ),
    HabitLaw.EASY to Regex(
        "te veel|teveel|te lang|geen tijd|druk|\\bmoe\\b|moeilijk|zwaar|lukt (niet|nooit)|ingewikkeld|gedoe|stress|" +
            "too much|too long|no time|busy|tired|\\bhard\\b|difficult|complicated|overwhelm",
    ),
)

/** A friction or idea about [habit]: linked to it when it was written, or naming it while it is still open. */
fun BoardItem.isAbout(habit: BacklogItem): Boolean {
    if (!isWorkingPoint) return false
    if (linkedId == habit.id) return true
    if (!isOpen) return false
    val names = keyWords(habit.text)
    return keyWords(text).any { w -> names.any { sameWord(it, w) } }
}

/** What was said about [habit]: open frictions and ideas, and those written in the last eight weeks. */
fun frictionsAbout(habit: BacklogItem, board: List<BoardItem>, today: LocalDate): List<BoardItem> =
    board.filter { it.isAbout(habit) && (it.isOpen || it.closedAt?.let { c -> dayOf(c) >= today.plusDays(-56) } == true) }

private val STOP_WORDS = setOf(
    "altijd", "nooit", "niet", "elke", "iedere", "voor", "naar", "maar", "toch", "even", "weer", "zelf", "veel", "door",
    "over", "onze", "geen", "niks", "niets", "iets", "alles", "moet", "moeten", "wordt", "worden", "heeft", "hebben", "zijn", "doen", "loopt", "keer", "niemand",
    "always", "never", "every", "with", "after", "before", "again", "about", "still", "nobody", "does", "doesn",
)

private fun keyWords(text: String) = normalizeName(text).split(Regex("[^\\p{L}]+")).filter { it.length >= 4 && it !in STOP_WORDS }

/** "poetsen" and "poetst", "vaatwas" and "vaatwasser" are the same; "avondroutine" and "avondeten" are not. */
private fun sameWord(a: String, b: String): Boolean {
    if (a == b) return true
    val common = a.zip(b).takeWhile { (x, y) -> x == y }.size
    return common >= 5 && common * 10 >= minOf(a.length, b.length) * 7
}

/**
 * The coach for one habit: its stage, how each law is in place and at most two tips. [ratings] is its score (0–5) per
 * meeting, oldest first (empty: the coach uses its last rating); [frictions] are the ones [isAbout] it; [steps] is how
 * many steps it has; [goal] is the goal it serves, if any.
 */
fun habitReview(habit: BacklogItem, ratings: List<Int>, frictions: List<BoardItem>, steps: Int, goal: String?, lang: Lang): HabitReview {
    val nl = lang == Lang.NL
    fun t(dutch: String, english: String) = if (nl) dutch else english
    val scores = ratings.ifEmpty { listOfNotNull(habit.scoreNow) }
    val weather = scores.map(::easeOf)
    val said = frictions.sortedBy { it.closedAt ?: it.createdAt }
    val focus = said.reversed().firstNotNullOfOrNull { lawOf(it.text) }
    val last = weather.lastOrNull()
    val stage = when {
        last == Ease.HARD -> HabitStage.SHRINK
        last == Ease.EFFORT || said.any { it.isOpen } -> HabitStage.ADJUST
        weather.size >= 3 && weather.takeLast(3).all { it == Ease.EASY } && scores.last() == SCORE_MAX -> HabitStage.GROW
        last == null -> HabitStage.NEW
        else -> HabitStage.STEADY
    }
    val weekly = habit.schedule == null && (habit.intervalDays ?: 1) >= 7
    val sunny = weather.count { it == Ease.EASY }
    val laws = listOf(
        LawState(HabitLaw.OBVIOUS, habit.schedule?.label(lang)?.replaceFirstChar { it.uppercase() } ?: habit.anchor?.title(lang)),
        LawState(HabitLaw.ATTRACTIVE, habit.pairing ?: goal?.let { t("Voor ons doel: $it", "For our goal: $it") }),
        LawState(HabitLaw.EASY, habit.smallStep?.let { t("Begin klein: $it", "Start small: $it") }),
        LawState(HabitLaw.SATISFYING, sunny.takeIf { it > 0 }?.let { t("$it× liep het vanzelf", "Ran easily $it×") }),
    )

    fun missing(law: HabitLaw) = when (law) {
        HabitLaw.OBVIOUS -> habit.anchor == null && habit.schedule == null
        HabitLaw.ATTRACTIVE -> habit.pairing == null
        HabitLaw.EASY -> habit.smallStep == null
        // Hearth makes it visible every week; celebrating is offered, never missing.
        HabitLaw.SATISFYING -> false
    }

    fun tip(law: HabitLaw): HabitTip = when (law) {
        HabitLaw.OBVIOUS -> when {
            missing(law) -> HabitTip(law, TipAction.MOMENT, t("Hang het aan een moment, na iets wat je toch al doet.", "Hang it on a moment, after something you already do."))
            weekly -> HabitTip(law, TipAction.DAYS, t("Geef het een vaste dag, dan hoeft niemand eraan te denken.", "Give it a fixed day, so nobody has to remember."))
            else -> HabitTip(law, TipAction.MOMENT, t("Misschien past een ander moment beter, waar je het vanzelf ziet.", "Maybe another moment fits better, where you see it anyway."))
        }
        HabitLaw.ATTRACTIVE -> HabitTip(
            law, TipAction.PAIR,
            habit.pairing?.let { t("Iets anders leuks erbij? Nu: $it.", "Something else nice with it? Now: $it.") }
                ?: t("Koppel er iets leuks aan: muziek, samen, of iets lekkers erna.", "Pair it with something nice: music, together, or a treat after."),
        )
        HabitLaw.EASY -> when {
            habit.smallStep == null -> HabitTip(law, TipAction.SMALL_STEP, t("Begin met de versie van twee minuten.", "Start with the two-minute version."))
            steps > 3 -> HabitTip(law, TipAction.FEWER_STEPS, t("Minder stappen: welke mag eruit?", "Fewer steps: which one can go?"))
            habit.schedule == null && (habit.intervalDays ?: 1) < 30 -> HabitTip(law, TipAction.LESS_OFTEN, t("Minder vaak, tot het vanzelf gaat.", "Less often, until it runs by itself."))
            else -> HabitTip(law, TipAction.SMALL_STEP, t("Maak de kleine stap nog kleiner.", "Make the small step even smaller."))
        }
        HabitLaw.SATISFYING -> HabitTip(law, TipAction.CELEBRATE, t("Zeg het als het lukt: bij Wat ging goed.", "Say it when it works: in What went well."))
    }

    val tips = when (stage) {
        HabitStage.NEW -> listOfNotNull(tip(HabitLaw.OBVIOUS).takeIf { missing(HabitLaw.OBVIOUS) }, tip(HabitLaw.EASY).takeIf { missing(HabitLaw.EASY) })
        HabitStage.SHRINK -> listOfNotNull(tip(HabitLaw.EASY), focus?.takeIf { it != HabitLaw.EASY }?.let(::tip))
        HabitStage.ADJUST -> {
            val first = focus ?: HabitLaw.entries.firstOrNull(::missing) ?: HabitLaw.EASY
            listOfNotNull(tip(first), HabitLaw.entries.firstOrNull { it != first && missing(it) }?.let(::tip))
        }
        HabitStage.GROW -> listOf(
            when {
                habit.smallStep != null -> HabitTip(null, TipAction.WHOLE, t("Drie keer vanzelf: klaar voor de hele versie?", "Easy three times: ready for the whole thing?"))
                habit.schedule == null && (habit.intervalDays ?: 1) > 1 -> HabitTip(null, TipAction.MORE_OFTEN, t("Het loopt vanzelf. Iets vaker?", "It runs by itself. A bit more often?"))
                else -> HabitTip(null, TipAction.ADD_STEP, t("Het loopt vanzelf. Een kleine stap erbij?", "It runs by itself. One small step more?"))
            },
            tip(HabitLaw.SATISFYING),
        )
        HabitStage.STEADY -> emptyList()
    }.distinctBy { it.action }.take(2)

    return HabitReview(habit.id, stage, scores, said.map { it.text }, focus, laws, tips)
}

/** What Wat ging goed says when a habit is celebrated. */
fun celebrateWords(habit: BacklogItem, lang: Lang): String =
    if (lang == Lang.NL) "${habit.text}: gelukt deze week" else "${habit.text}: we did it this week"
