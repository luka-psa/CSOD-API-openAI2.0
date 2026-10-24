package app.hearth.ai

import app.hearth.ai.route.TimeWords
import app.hearth.core.tools.backlog.When
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeWordsTest {
    // Saturday 24 October 2026.
    private val today = LocalDate(2026, 10, 24)

    @Test
    fun `days further ahead are read anywhere in a sentence`() {
        val cases = listOf(
            "verlof van 3 tot 7 november" to ("Verlof (3/11–7/11)" to "2026-11-03"),
            "verlof van 28 december tot 4 januari" to ("Verlof (28/12–4/1)" to "2026-12-28"),
            "verjaardag oma op 12/12" to ("Verjaardag oma" to "2026-12-12"),
            "oma is jarig 12 december" to ("Oma is jarig" to "2026-12-12"),
            "tandarts op 3 maart" to ("Tandarts" to "2027-03-03"),
            "vakantie volgende maand" to ("Vakantie" to "2026-11-01"),
            "autokeuring volgende week" to ("Autokeuring" to "2026-10-26"),
            "ramen wassen over 3 weken" to ("Ramen wassen" to "2026-11-14"),
            "birthday party on May 4" to ("Birthday party" to "2027-05-04"),
            "de tandarts voor Mia is volgende dinsdag" to ("de tandarts voor Mia" to "2026-10-27"),
        )
        for ((said, expected) in cases) {
            val hint = TimeWords.extractAnywhere(said, today)!!
            assertEquals(expected, hint.text.replaceFirstChar { it.uppercase() }.let { if (said.startsWith("de ")) hint.text else it } to hint.date.toString(), said)
            assertEquals(When.DAY, hint.whenValue, said)
        }
    }

    @Test
    fun `a deadline date reads as finish by`() {
        val hint = TimeWords.extractAnywhere("belastingbrief tegen 1 december", today)!!
        assertEquals(true, hint.finishBy)
        assertEquals("2026-12-01", hint.date.toString())
    }

    @Test
    fun `amounts are not dates`() {
        assertEquals(null, TimeWords.extractAnywhere("2 kg appels", today))
        assertEquals(null, TimeWords.extractAnywhere("om 8 uur gaan slapen", today))
    }
}
