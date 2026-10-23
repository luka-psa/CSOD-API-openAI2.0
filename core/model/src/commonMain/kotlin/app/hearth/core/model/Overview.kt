package app.hearth.core.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

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

fun BacklogItem.daysSinceDone(today: LocalDate): Int? =
    lastDoneDate?.let { (today.toEpochDays() - it.toEpochDays()).toInt() }

fun BacklogItem.routineState(today: LocalDate): RoutineState {
    val interval = intervalDays ?: return if (lastDone == null) RoutineState.NEW else RoutineState.FRESH
    val since = daysSinceDone(today) ?: return RoutineState.NEW
    return when {
        since >= interval -> RoutineState.DUE
        interval > 1 && since >= interval - maxOf(1, interval / 5) -> RoutineState.SOON
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
