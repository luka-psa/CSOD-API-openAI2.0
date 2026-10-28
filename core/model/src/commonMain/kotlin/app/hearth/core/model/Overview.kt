package app.hearth.core.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

// What the dashboard shows, as plain functions so the screen, the tools and MCP all agree.

/** Where a routine stands. There is no "late": a routine is at most "about time". */
enum class RoutineState {
    /** Has a rhythm but was never ticked off in Hearth yet. */
    NEW,
    /** Done recently enough. */
    FRESH,
    /** Coming up in the next days. */
    SOON,
    /** About time again. */
    DUE,
}

fun BacklogItem.isRoutine(): Boolean = !isStep && section.kind == TaskKind.ROUTINE

/**
 * One line of a routine: a step of a sequence ("Mia ophalen", then "Koken") or of a checklist ("Luiers"
 * in the bag for the opvang). Ticked per day; tomorrow it is open again.
 */
val BacklogItem.isRoutineStep: Boolean get() = isStep && section.kind == TaskKind.ROUTINE

fun BacklogItem.doneOn(day: LocalDate): Boolean = lastDoneDate == day

/** A routine or one of its steps ticked off on [day]: it shows ticked, and a second tap takes the tick back. */
fun BacklogItem.tickedOn(day: LocalDate): Boolean = !done && (isRoutine() || isRoutineStep) && doneOn(day)

fun BacklogItem.daysSinceDone(today: LocalDate): Int? =
    lastDoneDate?.let { (today.toEpochDays() - it.toEpochDays()).toInt() }

fun BacklogItem.routineState(today: LocalDate): RoutineState {
    schedule?.let { return it.state(lastDoneDate, today) }
    val interval = intervalDays ?: return if (lastDone == null) RoutineState.NEW else RoutineState.FRESH
    val since = daysSinceDone(today) ?: return RoutineState.NEW
    return when {
        since >= interval -> RoutineState.DUE
        interval > 1 && since >= interval - maxOf(1, interval / 5) -> RoutineState.SOON
        else -> RoutineState.FRESH
    }
}

/**
 * On fixed days: about time on its day, and still open after it until done or its next day comes ("sinds donderdag",
 * never "late"). Never done and its day already passed: it starts at the next one.
 */
private fun Schedule.state(lastDone: LocalDate?, today: LocalDate): RoutineState {
    val last = lastOn(today)
    return when {
        last != null && (lastDone == null || lastDone < last) && (last == today || lastDone != null) -> RoutineState.DUE
        nextAfter(today) == today.plusDays(1) -> RoutineState.SOON
        else -> RoutineState.FRESH
    }
}

/** Routines to pick up today: about time, or with a rhythm and never done yet. Daily ones first. */
fun dueRoutines(items: List<BacklogItem>, today: LocalDate): List<BacklogItem> =
    items.filter { it.isRoutine() && it.plan != Plan.DAY && it.intervalDays != null && it.routineState(today).let { s -> s == RoutineState.DUE || s == RoutineState.NEW } }
        .sortedWith(compareBy({ it.intervalDays }, { -(it.daysSinceDone(today) ?: Int.MAX_VALUE / 2) }))

/** Must not slip: urgent admin, deadlines within [horizonDays], and follow-ups that are due. */
fun BacklogItem.isDontForget(today: LocalDate, horizonDays: Int = 3): Boolean {
    if (isRoutine() || done) return false
    val d = localDate
    return urgent ||
        (waiting && d != null && d <= today) ||
        (!waiting && plan == Plan.DAY && finishBy && d != null && d <= today.plusDays(horizonDays))
}

fun dontForget(items: List<BacklogItem>, today: LocalDate): List<BacklogItem> =
    items.filter { it.isDontForget(today) }.sortedWith(compareBy({ !it.urgent }, { it.date ?: "9999" }))

/** On today's plan: "do on" today or an earlier day, not already under "don't forget". */
fun plannedToday(items: List<BacklogItem>, today: LocalDate): List<BacklogItem> =
    items.filter {
        !it.waiting && it.plan == Plan.DAY && !it.isDontForget(today) &&
            (it.localDate?.let { d -> d <= today } ?: false)
    }.sortedBy { it.date }

/** What is planned on one future day (projects, steps, admin, appointments, a routine given a day, protected time). */
fun plannedOn(items: List<BacklogItem>, day: LocalDate): List<BacklogItem> =
    items.filter { it.plan == Plan.DAY && it.localDate == day }

/** How full a day feels, without anyone estimating durations. */
enum class DayLoad { FREE, LIGHT, BUSY }

fun dayLoad(plannedCount: Int): DayLoad = when {
    plannedCount == 0 -> DayLoad.FREE
    plannedCount <= 2 -> DayLoad.LIGHT
    else -> DayLoad.BUSY
}

val LocalDate.isWeekend: Boolean get() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

/** "elke week", "om de 2 weken", "af en toe". */
fun intervalLabel(days: Int?, lang: Lang): String {
    val nl = lang == Lang.NL
    return when (days) {
        null -> if (nl) "af en toe" else "now and then"
        1 -> if (nl) "elke dag" else "every day"
        7 -> if (nl) "elke week" else "every week"
        14 -> if (nl) "om de 2 weken" else "every 2 weeks"
        30, 31 -> if (nl) "elke maand" else "every month"
        90 -> if (nl) "elk seizoen" else "every season"
        365 -> if (nl) "elk jaar" else "every year"
        else -> if (days % 7 == 0) (if (nl) "om de ${days / 7} weken" else "every ${days / 7} weeks")
        else (if (nl) "om de $days dagen" else "every $days days")
    }
}

/** "vandaag gedaan", "5 dagen geleden", "nog niet gedaan". */
fun lastDoneLabel(item: BacklogItem, today: LocalDate, lang: Lang): String {
    val nl = lang == Lang.NL
    return when (val days = item.daysSinceDone(today)) {
        null -> if (nl) "nog niet gedaan" else "not done yet"
        0 -> if (nl) "vandaag gedaan" else "done today"
        1 -> if (nl) "gisteren gedaan" else "done yesterday"
        in 2..13 -> if (nl) "$days dagen geleden" else "$days days ago"
        in 14..59 -> if (nl) "${days / 7} weken geleden" else "${days / 7} weeks ago"
        else -> if (nl) "${days / 30} maanden geleden" else "${days / 30} months ago"
    }
}

/**
 * How much something asks of the household, as a colour (never red, never "late"). Atomic Habits: when
 * something is hard, make it smaller, easier or less often rather than trying harder.
 */
enum class Ease {
    /** Loopt vanzelf (green). */
    EASY,
    /** Vraagt moeite (amber): a smaller first step may help. */
    EFFORT,
    /** Te zwaar (orange): make it smaller or split it up. */
    HARD,
}

// What the family rates in the Terugblik (routines, house rules, the week) goes on a scale from 0 to 5: 0 "komt er niet
// van", 5 "loopt vanzelf". The score keeps its weather (bliksem 0–1, wolk 2–3, zon 4–5), so everything that reads ease
// (the coach, the attention list, the colours) keeps working, and the number shows how it moves week by week.

const val SCORE_MAX = 5

/** The weather of a score: zon 4–5, wolk 2–3, bliksem 0–1. */
fun easeOf(score: Int): Ease = when {
    score >= 4 -> Ease.EASY
    score >= 2 -> Ease.EFFORT
    else -> Ease.HARD
}

/** A rating from before the scale (or from a caller that only knows the weather): its middle-of-the-band score. */
val Ease.score: Int get() = when (this) {
    Ease.EASY -> 5
    Ease.EFFORT -> 3
    Ease.HARD -> 1
}

/** Kind words for each point of the scale; never "failed". */
fun scoreWord(score: Int, lang: Lang): String {
    val nl = lang == Lang.NL
    return when (score.coerceIn(0, SCORE_MAX)) {
        0 -> if (nl) "komt er niet van" else "doesn't happen yet"
        1 -> if (nl) "te zwaar" else "too much"
        2 -> if (nl) "moeizaam" else "a struggle"
        3 -> if (nl) "vraagt moeite" else "takes effort"
        4 -> if (nl) "loopt goed" else "goes well"
        else -> if (nl) "loopt vanzelf" else "runs by itself"
    }
}

/** Habits: the family's last score, or the one its old weather stood for. */
val BacklogItem.scoreNow: Int? get() = score ?: ease?.score

/** Habits and rhythms are lived, not ticked off: how hard they are is what the family says ([BacklogItem.ease]). */
val BacklogItem.isHabit: Boolean get() = !isStep && (section == Section.HABIT || section == Section.RHYTHM)

/**
 * The colour right now. Habits: the family's own rating. Chores (ticked off now and then): from when they
 * were actually done ([doneDays]; [addedOn] = when it got its rhythm). Tasks: from how often their day was
 * pushed back. Null: nothing to say (new, or simply fine).
 */
fun BacklogItem.easeNow(doneDays: List<LocalDate>, addedOn: LocalDate, today: LocalDate): Ease? = when {
    isHabit -> ease
    isRoutine() -> choreEase(doneDays, addedOn, today)
    postponed >= 4 -> Ease.HARD
    postponed >= 2 -> Ease.EFFORT
    else -> null
}

/** A chore over four weeks (or three rounds): done at least 60% of the rounds and not two rounds behind = easy. */
private fun BacklogItem.choreEase(doneDays: List<LocalDate>, addedOn: LocalDate, today: LocalDate): Ease? {
    val interval = intervalDays?.takeIf { it > 0 } ?: return null
    val age = (today.toEpochDays() - addedOn.toEpochDays()).toInt()
    if (age < interval * 2) return null
    val span = minOf(age, maxOf(28, interval * 3))
    val from = today.plusDays(-span)
    val expected = span.toDouble() / interval
    val done = doneDays.count { it > from && it <= today }
    val behind = (daysSinceDone(today) ?: span).toDouble() / interval
    return when {
        done >= expected * 0.6 && behind < 2 -> Ease.EASY
        behind >= 4 && done == 0 -> Ease.HARD
        else -> Ease.EFFORT
    }
}

/** The local day a moment fell on. */
@OptIn(kotlin.time.ExperimentalTime::class)
fun dayOf(millis: Long): LocalDate =
    kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
