package app.hearth.ai

import app.hearth.ai.route.RecipeHint
import app.hearth.ai.route.RecipeWords
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeWordsTest {
    private val cases = listOf(
        "We maakten lasagne met gehakt, tomatensaus, lasagnebladen en kaas. 45 minuten in de oven op 180 graden." to
            RecipeHint("Lasagne", listOf("gehakt", "tomatensaus", "lasagnebladen", "kaas"), "45 minuten in de oven op 180 graden"),
        "recept: pannenkoeken met bloem, melk en eieren" to RecipeHint("Pannenkoeken", listOf("bloem", "melk", "eieren"), null),
        "Vandaag hebben we een curry gemaakt. Ingrediënten: kip, kokosmelk, rijst. Eerst de ui fruiten." to
            RecipeHint("Curry", listOf("kip", "kokosmelk", "rijst"), "Eerst de ui fruiten"),
        "Soep: wortelen, ui en bouillon" to RecipeHint("Soep", listOf("wortelen", "ui", "bouillon"), null),
        "We made tacos with minced meat, wraps and salsa" to RecipeHint("Tacos", listOf("minced meat", "wraps", "salsa"), null),
        "Stoofvlees" to RecipeHint("Stoofvlees", emptyList(), null),
    )

    @Test
    fun readsRecipesFromHowPeopleTellThem() {
        cases.forEach { (input, expected) -> assertEquals(expected, RecipeWords.parse(input), input) }
    }
}
