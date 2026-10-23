package app.hearth.ui

import app.hearth.core.model.Lang
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun localNow(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** "woensdag 22 oktober" / "Wednesday 22 October". */
fun formatDay(dateTime: LocalDateTime, lang: Lang): String {
    val day = when (lang) {
        Lang.NL -> listOf("maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag")
        Lang.EN -> listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    }[dateTime.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal]
    val month = when (lang) {
        Lang.NL -> listOf("januari", "februari", "maart", "april", "mei", "juni", "juli", "augustus", "september", "oktober", "november", "december")
        Lang.EN -> listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    }[dateTime.month.ordinal]
    return "$day ${dateTime.day} $month"
}

fun formatClock(dateTime: LocalDateTime): String =
    "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"

/** The local day a moment fell on. */
@OptIn(ExperimentalTime::class)
fun dateOf(millis: Long): kotlinx.datetime.LocalDate =
    kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
