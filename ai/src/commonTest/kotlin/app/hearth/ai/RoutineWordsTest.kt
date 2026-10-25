package app.hearth.ai

import app.hearth.ai.route.RoutineHint
import app.hearth.ai.route.RoutineWords
import app.hearth.core.model.Anchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutineWordsTest {
    private val cases = listOf(
        "avondroutine: boodschappen, Mia ophalen, koken en pyjama aan" to
            RoutineHint("Avondroutine", Anchor.EVENING, listOf("Boodschappen", "Mia ophalen", "Koken", "Pyjama aan")),
        "paklijst opvang: luiers, flesjes, reservekleren, knuffel" to
            RoutineHint("Paklijst opvang", null, listOf("Luiers", "Flesjes", "Reservekleren", "Knuffel")),
        "ochtendroutine: aankleden dan ontbijt daarna tanden poetsen" to
            RoutineHint("Ochtendroutine", Anchor.MORNING, listOf("Aankleden", "Ontbijt", "Tanden poetsen")),
        "routine na school: brooddoos leegmaken, vieruurtje" to
            RoutineHint("Routine na school", Anchor.AFTERNOON, listOf("Brooddoos leegmaken", "Vieruurtje")),
        "lijstje voor de babysit: slaapzak; speentje" to
            RoutineHint("Lijstje voor de babysit", null, listOf("Slaapzak", "Speentje")),
        "evening routine: groceries, pick up Mia, cook and then pyjamas" to
            RoutineHint("Evening routine", Anchor.EVENING, listOf("Groceries", "Pick up Mia", "Cook", "Pyjamas")),
        "slaaproutine: badje → pyjama → verhaaltje" to
            RoutineHint("Slaaproutine", Anchor.BEDTIME, listOf("Badje", "Pyjama", "Verhaaltje")),
    )

    @Test
    fun readsARoutineWithItsSteps() {
        cases.forEach { (input, expected) -> assertEquals(expected, RoutineWords.extract(input), input) }
    }

    @Test
    fun leavesOtherSentencesAlone() {
        listOf("huisregel: geen schermen", "melk en eieren", "avondeten: pasta", "recept: pannenkoeken met bloem en melk").forEach {
            assertNull(RoutineWords.extract(it), it)
        }
    }
}
