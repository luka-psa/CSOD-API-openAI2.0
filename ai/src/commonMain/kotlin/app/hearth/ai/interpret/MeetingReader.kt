package app.hearth.ai.interpret

/**
 * Reads the weekoverleg with the interpreter while it goes on. It never needs the talk cut into topics: each reading
 * takes the last [window] lines as a whole (a change of direction, "nee, het wordt donderdag", sits right next to what it
 * changes), plus earlier lines that the newest ones name again ("om nog eens terug te komen op de trap"), so a topic that
 * comes back gets its context back. Each action belongs to the line it came from: a new reading replaces the actions of
 * the lines it read, and keeps the ones of lines that have scrolled out of view.
 */
class MeetingReader(
    /** One reading: the lines (oldest first) in, the actions as they stand out. */
    private val interpret: suspend (List<String>) -> List<Action>,
    private val window: Int = 12,
    private val recall: Int = 6,
) {
    /** An action and the line it came from. */
    data class Found(val action: Action, val line: Int)

    private var found: List<Found> = emptyList()
    private var readUpTo = -1

    /** What the whole meeting comes down to so far, in the order it was said. */
    val actions: List<Found> get() = found

    /** Reads again when there are new lines; returns the actions as they stand. */
    suspend fun update(lines: List<String>): List<Found> {
        if (lines.isEmpty() || lines.size - 1 == readUpTo) return found
        val start = maxOf(0, lines.size - window)
        val recent = (start until lines.size).toList()
        val back = recalled(lines, start)
        val read = (back + recent)
        val got = interpret(read.map { lines[it] })
        val placed = got.map { a -> Found(a, home(a, read, lines)) }
        // The lines just read decide again; actions of lines further back stay as they were.
        found = (found.filter { it.line !in read } + placed).sortedBy { it.line }
        readUpTo = lines.size - 1
        return found
    }

    /** Lines before the window that the newest lines name again (a shared word), at most [recall], in their order. */
    private fun recalled(lines: List<String>, start: Int): List<Int> {
        if (start == 0) return emptyList()
        val now = (maxOf(start, lines.size - 3) until lines.size).flatMap { words(lines[it]) }.toSet()
        if (now.isEmpty()) return emptyList()
        return (0 until start).filter { i -> words(lines[i]).any { w -> now.any { sameWord(it, w) } } }.takeLast(recall)
    }

    /** The line an action came from: the one sharing most of its words (the latest on a tie). */
    private fun home(a: Action, read: List<Int>, lines: List<String>): Int {
        val said = a.args.values.flatMap(::words).toSet()
        return read.maxWith(compareBy<Int> { i -> words(lines[i]).count { w -> said.any { sameWord(it, w) } } }.thenBy { it })
    }

    companion object {
        /** Small talk around the words that carry a topic. */
        val STOP_WORDS = """ja nee oké okee goed dan maar want omdat als ook nog wel niet eens even toch dus wat wie waar wanneer hoe welke
            deze die dat dit het een de en of om op in aan van voor met naar bij uit tot over door ik jij je we wij ze zij hij jullie mij me ons onze
            mijn jouw heb hebt heeft hebben ben bent is zijn was waren moet moeten kan kunnen wil willen zal zullen ga gaat gaan doe doen doet komt
            komen mag mogen zou zouden laat laten maak maken denk denken vind vinden weet weten graag gewoon eigenlijk misschien allee echt heel veel
            meer altijd nooit elke iedere yes yeah okay the and that this what with have will would could should just really then also afgesproken
            akkoord deal prima lukt mja hmm euh terug komen zeiden daarnet trouwens""".split(Regex("\\s+")).toSet()

        /** The words that carry a topic: four letters or more, not the small talk around them. */
        fun words(text: String): List<String> =
            Regex("[\\p{L}'’-]+").findAll(text.lowercase()).map { it.value }.filter { it.length >= 4 && it !in STOP_WORDS }.toList()

        /** "poetsen" and "poetst", "trap" and "trappen" are the same word. */
        fun sameWord(a: String, b: String): Boolean {
            if (a == b) return true
            val common = a.zip(b).takeWhile { (x, y) -> x == y }.size
            return common >= 4 && common * 10 >= minOf(a.length, b.length) * 7
        }
    }
}
