package app.hearth.ai

import app.hearth.ai.route.MealHint
import app.hearth.ai.route.MealWords
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MealWordsTest {
    // Friday 23 October 2026.
    private val friday = LocalDate(2026, 10, 23)

    private val cases = listOf(
        "we eten maandag spaghetti" to MealHint("spaghetti", LocalDate(2026, 10, 26)),
        "We eten lasagne op zondag" to MealHint("lasagne", LocalDate(2026, 10, 25)),
        "vanavond eten we frietjes" to MealHint("frietjes", friday),
        "vrijdag eten we vis" to MealHint("vis", friday),
        "eten dinsdag: soep met balletjes" to MealHint("soep met balletjes", LocalDate(2026, 10, 27)),
        "dinner on monday: pizza" to MealHint("pizza", LocalDate(2026, 10, 26)),
        "we're having tacos tomorrow" to MealHint("tacos", LocalDate(2026, 10, 24)),
    )

    @Test
    fun readsMeals() {
        cases.forEach { (input, expected) -> assertEquals(expected, MealWords.extract(input, friday), input) }
        assertEquals(null, MealWords.extract("tandarts maandag", friday))
        assertEquals(null, MealWords.extract("melk", friday))
    }
}
