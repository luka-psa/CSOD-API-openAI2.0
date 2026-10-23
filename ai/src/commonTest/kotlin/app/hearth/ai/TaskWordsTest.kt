package app.hearth.ai

import app.hearth.ai.route.TaskHint
import app.hearth.ai.route.TaskWords
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
    )

    @Test
    fun readsRhythmAndUrgency() {
        cases.forEach { (input, expected) -> assertEquals(expected, TaskWords.extract(input), input) }
    }
}
