package app.hearth.ai

import app.hearth.ai.route.TaskHint
import app.hearth.core.model.Anchor
import app.hearth.ai.route.TaskWords
import app.hearth.core.model.Schedule
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskWordsTest {
    private val cases = listOf(
        "lakens verversen om de 2 weken" to TaskHint("lakens verversen", 14, false),
        "gras maaien elke week" to TaskHint("gras maaien", 7, false),
        "Ramen kuisen, maandelijks" to TaskHint("Ramen kuisen", 30, false),
        "elke dag de vaatwasser uitladen" to TaskHint("de vaatwasser uitladen", 1, false),
        "water the plants every 3 days" to TaskHint("water the plants", 3, false),
        "dakgoot elk seizoen" to TaskHint("dakgoot", 90, false),
        "dringend: verzekering bellen" to TaskHint("verzekering bellen", null, true),
        "call the bank asap" to TaskHint("call the bank", null, true),
        "tandarts bellen" to TaskHint("tandarts bellen", null, false),
        "weekend plannen" to TaskHint("weekend plannen", null, false),
        "’s avonds de tassen klaarzetten" to TaskHint("De tassen klaarzetten", null, false, Anchor.EVENING),
        "na het eten de tafel afruimen" to TaskHint("De tafel afruimen", null, false, Anchor.DINNER),
        "elke avond voor het slapen tanden poetsen" to TaskHint("Tanden poetsen", null, false, Anchor.BEDTIME),
        "s ochtends bedden opmaken" to TaskHint("Bedden opmaken", null, false, Anchor.MORNING),
        "na school brooddozen leegmaken" to TaskHint("Brooddozen leegmaken", null, false, Anchor.AFTERNOON),
        "brush teeth before bed" to TaskHint("Brush teeth", null, false, Anchor.BEDTIME),
        "vanavond pizza" to TaskHint("vanavond pizza", null, false),
        "morgenochtend tandarts" to TaskHint("morgenochtend tandarts", null, false),
    )

    private val fixedDays = listOf(
        "vuilbak buiten elke donderdag" to TaskHint("vuilbak buiten", 7, false, schedule = Schedule(listOf(THURSDAY))),
        "elke donderdag om 8 uur sporten" to TaskHint("sporten", 7, false, schedule = Schedule(listOf(THURSDAY), time = "08:00")),
        "elke donderdagavond om 8 uur sporten" to TaskHint("Sporten", 7, false, Anchor.EVENING, Schedule(listOf(THURSDAY), time = "20:00")),
        "zwemles iedere woensdag om 2 uur" to TaskHint("zwemles", 7, false, schedule = Schedule(listOf(WEDNESDAY), time = "14:00")),
        "stofzuigen elke ma, wo en vr" to TaskHint("stofzuigen", 7, false, schedule = Schedule(listOf(MONDAY, WEDNESDAY, FRIDAY))),
        "badkamer kuisen elke week op dinsdag" to TaskHint("badkamer kuisen", 7, false, schedule = Schedule(listOf(TUESDAY))),
        "'s maandags de papiermand buiten" to TaskHint("de papiermand buiten", 7, false, schedule = Schedule(listOf(MONDAY))),
        "huur betalen de 1ste van de maand" to TaskHint("huur betalen", 30, false, schedule = Schedule(monthDay = 1)),
        "elke maand op de 15de de meters opnemen" to TaskHint("de meters opnemen", 30, false, schedule = Schedule(monthDay = 15)),
        "de eerste zaterdag van de maand containerpark" to TaskHint("containerpark", 30, false, schedule = Schedule(listOf(SATURDAY), monthWeek = 1)),
        "take out the bins every Monday and Thursday at 7pm" to TaskHint("take out the bins", 7, false, schedule = Schedule(listOf(MONDAY, THURSDAY), time = "19:00")),
        "pay rent on the 1st of every month" to TaskHint("pay rent", 30, false, schedule = Schedule(monthDay = 1)),
        "every last Friday of the month movie night" to TaskHint("movie night", 30, false, schedule = Schedule(listOf(FRIDAY), monthWeek = -1)),
        "elke dag om 7 uur medicijnen" to TaskHint("medicijnen", 1, false, schedule = Schedule(kotlinx.datetime.DayOfWeek.entries.toList(), time = "07:00")),
        // One day, not a rhythm; and words that only look like days.
        "op maandag de garage bellen" to TaskHint("op maandag de garage bellen", null, false),
        "elke mail beantwoorden" to TaskHint("elke mail beantwoorden", null, false),
        "elke dag om de beurt afwassen" to TaskHint("om de beurt afwassen", 1, false),
    )

    @Test
    fun readsFixedDaysAndTimes() {
        fixedDays.forEach { (input, expected) -> assertEquals(expected, TaskWords.extract(input), input) }
    }

    @Test
    fun readsRhythmAndUrgency() {
        cases.forEach { (input, expected) -> assertEquals(expected, TaskWords.extract(input), input) }
    }
}
