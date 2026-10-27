package app.hearth.ai.interpret

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MeetingReaderTest {
    /** A stand-in interpreter: a dentist line is an appointment (with the last day said), "afgezegd" drops it. */
    private val read = mutableListOf<List<String>>()
    private val fake: suspend (List<String>) -> List<Action> = { lines ->
        read += lines
        if (lines.any { "afgezegd" in it }) emptyList()
        else lines.filter { "tandarts" in it }.map {
            val day = lines.flatMap { it.split(" ") }.lastOrNull { it.endsWith("dag") } ?: ""
            Action("add_appointment", mapOf("text" to "Tandarts Mia", "day" to day))
        }.distinct()
    }

    @Test
    fun aNewReadingReplacesWhatItReadAndKeepsTheRest() = runTest {
        val r = MeetingReader(fake, window = 3, recall = 2)
        val lines = mutableListOf("dinsdag heeft Mia tandarts", "ja")
        assertEquals(listOf("dinsdag"), r.update(lines).map { it.action.args["day"] })
        // Three more lines: the dentist scrolls out of view, but its action stays.
        lines += listOf("de auto moet naar de garage", "ok", "en het gras afrijden")
        assertEquals(listOf("Tandarts Mia"), r.update(lines).map { it.action.args["text"] })
        // It comes back, moved to Thursday: the old line is recalled and read again with the change.
        lines += listOf("de tandarts van Mia is verplaatst naar donderdag")
        assertEquals(listOf("donderdag"), r.update(lines).map { it.action.args["day"] })
        assertEquals("dinsdag heeft Mia tandarts", read.last().first())
        // Cancelled: gone.
        lines += listOf("die tandarts is afgezegd")
        assertEquals(emptyList(), r.update(lines))
    }

    @Test
    fun theSameLinesAreNotReadTwice() = runTest {
        val r = MeetingReader(fake)
        val lines = listOf("dinsdag heeft Mia tandarts")
        r.update(lines); r.update(lines)
        assertEquals(1, read.size)
    }
}
