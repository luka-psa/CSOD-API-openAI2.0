package app.hearth.ai

import app.hearth.ai.route.TaskHint
import app.hearth.core.model.Anchor
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
        "’s avonds de tassen klaarzetten" to TaskHint("De tassen klaarzetten", null, false, Anchor.EVENING),
        "na het eten de tafel afruimen" to TaskHint("De tafel afruimen", null, false, Anchor.DINNER),
        "elke avond voor het slapen tanden poetsen" to TaskHint("Tanden poetsen", null, false, Anchor.BEDTIME),
        "s ochtends bedden opmaken" to TaskHint("Bedden opmaken", null, false, Anchor.MORNING),
        "na school brooddozen leegmaken" to TaskHint("Brooddozen leegmaken", null, false, Anchor.AFTERNOON),
        "brush teeth before bed" to TaskHint("Brush teeth", null, false, Anchor.BEDTIME),
        "vanavond pizza" to TaskHint("vanavond pizza", null, false),
        "morgenochtend tandarts" to TaskHint("morgenochtend tandarts", null, false),
    )

    @Test
    fun readsRhythmAndUrgency() {
        cases.forEach { (input, expected) -> assertEquals(expected, TaskWords.extract(input), input) }
    }
}
