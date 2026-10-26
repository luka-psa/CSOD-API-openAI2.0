package app.hearth.ai.route

/**
 * Talk as people say it. The recognizer writes down "euh", "allee", "da" and "ge"; the sorters want the words
 * that matter. Only what Hearth reads is cleaned: the transcript keeps what was said.
 */
object Spoken {
    // Flemish spoken forms (and a few contractions) as plain Dutch.
    private val forms = mapOf(
        "da" to "dat", "wa" to "wat", "nie" to "niet", "ni" to "niet", "ge" to "je", "gij" to "jij", "zijde" to "ben je",
        "hedde" to "heb je", "kunde" to "kun je", "moete" to "moet je", "moede" to "moet je", "gade" to "ga je", "'k" to "ik",
        "’k" to "ik", "k" to "ik", "'t" to "het", "’t" to "het", "nen" to "een", "ne" to "een", "'n" to "een", "dees" to "deze",
        "goesting" to "zin", "seffens" to "straks", "subiet" to "straks", "kga" to "ik ga", "kmoet" to "ik moet",
        "kzal" to "ik zal", "kheb" to "ik heb", "kben" to "ik ben", "tis" to "het is", "da's" to "dat is", "das" to "dat is",
        "efkes" to "even", "eventjes" to "even",
    )

    // Words that carry no meaning in a plan: "euh", "allee", "gewoon", "eigenlijk".
    private val fillers = listOf(
        "euh", "eh", "ehm", "uhm", "uh", "um", "hmm", "hm", "allee", "allez", "awel", "voilà", "voila", "zenne", "ofzo", "enzo",
        "en zo", "of zo", "weet je", "weet ge", "zeg maar", "gewoon", "eigenlijk", "even", "eens", "you know",
    )

    private const val EDGE_START = "(?<![\\p{L}'’])"
    private const val EDGE_END = "(?![\\p{L}'’])"
    private val form = Regex(EDGE_START + "(" + forms.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")" + EDGE_END, RegexOption.IGNORE_CASE)
    private val filler = Regex(EDGE_START + "(?:" + fillers.sortedByDescending { it.length }.joinToString("|") { it.split(" ").joinToString("\\s+") { w -> Regex.escape(w) } } + ")" + EDGE_END + ",?", RegexOption.IGNORE_CASE)

    /** "allee, da moet ge efkes bellen" → "dat moet je bellen". */
    fun clean(text: String): String {
        val plain = form.replace(text) { m -> forms[m.value.lowercase()] ?: m.value }
        return filler.replace(plain, " ").replace(Regex("\\s+"), " ").replace(Regex("\\s+([,.!?])"), "$1").trim().trim(',').trim()
    }

    /**
     * The recognizer's guesses for one stretch, best first: the one that names the most of the household's own words
     * (people, rooms, stock, routines) wins; on a tie the recognizer's first guess stays.
     */
    fun pick(alternatives: List<String>, vocabulary: Collection<String>): String? {
        val options = alternatives.map { it.trim() }.filter { it.isNotEmpty() }
        if (options.size <= 1 || vocabulary.isEmpty()) return options.firstOrNull()
        val words = vocabulary.map { it.lowercase().trim() }.filter { it.length >= 2 }.toSet()
        fun score(text: String): Int {
            val t = " ${text.lowercase().replace(Regex("[^\\p{L}\\p{N}'’ ]"), " ").replace(Regex("\\s+"), " ")} "
            return words.count { " $it " in t }
        }
        val best = options.maxBy { score(it) }
        return if (score(best) > score(options.first())) best else options.first()
    }
}
