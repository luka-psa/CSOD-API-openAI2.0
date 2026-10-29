package app.hearth.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Words the recognizer keeps getting wrong, and what the family means: "Jaules" is "Jules", "vaat was" is "vaatwasser". */
@Serializable
data class WordFix(val heard: String, val meant: String)

/**
 * The household's own words for Luister mee, learned from what the family puts right: a line fixed in *Gesprek*, a
 * suggestion's name typed again in the Voorstel. Hearth reads every conversation with them (the transcript keeps what
 * was heard) and hands the meant words to the recognizer as hints. Kept in the household's settings, so they travel
 * with sync and backup.
 */
object WordFixes {
    /** The setting that holds them (household-wide, not device-only). */
    const val SETTING = "listen.words"
    private const val MAX = 300
    private val json = Json { ignoreUnknownKeys = true }
    private val list = ListSerializer(WordFix.serializer())

    fun parse(text: String?): List<WordFix> =
        text?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() }.orEmpty()

    fun encode(fixes: List<WordFix>): String = json.encodeToString(list, fixes)

    /** [learned] on top of [known]: the newest meaning of a heard word wins; the oldest go first past [MAX]. */
    fun merge(known: List<WordFix>, learned: List<WordFix>): List<WordFix> {
        val fresh = learned.map { WordFix(it.heard.trim(), it.meant.trim()) }.filter { it.heard.isNotEmpty() && it.meant.isNotEmpty() && key(it.heard) != key(it.meant) }
        val keys = fresh.map { key(it.heard) }.toSet()
        // A word learned the other way round ("Jules" was "Jaules") is put right, not undone.
        val undone = fresh.map { key(it.meant) }.toSet()
        return (known.filter { key(it.heard) !in keys && key(it.heard) !in undone } + fresh.distinctBy { key(it.heard) }).takeLast(MAX)
    }

    fun forget(known: List<WordFix>, heard: String): List<WordFix> = known.filter { key(it.heard) != key(heard) }

    /** [text] with every fix applied, whole words only, the longest first ("vaat was" before "was"). */
    fun apply(text: String, fixes: List<WordFix>): String {
        if (fixes.isEmpty() || text.isBlank()) return text
        var out = text
        fixes.sortedByDescending { it.heard.length }.forEach { f ->
            val pattern = f.heard.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
            out = Regex("(?<![\\p{L}\\p{N}])$pattern(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).replace(out) { m ->
                // "Jaules" at the start of a sentence stays capitalised; a name stays as the family typed it.
                if (m.value.first().isUpperCase() && f.meant.first().isLowerCase()) f.meant.replaceFirstChar { it.uppercase() } else f.meant
            }
        }
        return out
    }

    /**
     * What one line fixed by hand teaches: the words that changed in place ("de vaat was" → "de vaatwasser": "vaat was"
     * is "vaatwasser"). Only when most of the line stayed the same, so a line said again in other words teaches nothing,
     * and only small changes (up to three words on each side).
     */
    fun learn(before: String, after: String): List<WordFix> {
        val a = tokens(before)
        val b = tokens(after)
        if (a.isEmpty() || b.isEmpty()) return emptyList()
        val same = lcs(a.map(::key), b.map(::key))
        if (same.size * 2 < maxOf(a.size, b.size)) return emptyList()
        val out = mutableListOf<WordFix>()
        var i = 0
        var j = 0
        fun gap(toI: Int, toJ: Int) {
            val heard = a.subList(i, toI)
            val meant = b.subList(j, toJ)
            if (heard.isNotEmpty() && meant.isNotEmpty() && heard.size <= 3 && meant.size <= 3) {
                val h = heard.joinToString(" ")
                val m = meant.joinToString(" ")
                if (h.length >= 2 && m.length >= 2 && key(h) != key(m)) out += WordFix(h, m)
            }
        }
        for ((si, sj) in same) {
            gap(si, sj)
            i = si + 1
            j = sj + 1
        }
        gap(a.size, b.size)
        return out
    }

    /**
     * What a name typed again teaches, when it was rewritten freely: only words spelled almost the same ("Jaules" →
     * "Jules", "wasmant" → "wasmand"), never a word swapped for another one.
     */
    fun learnSpelling(heard: String, typed: String): List<WordFix> {
        val said = tokens(heard)
        val saidKeys = said.map(::key).toSet()
        val now = tokens(typed)
        val nowKeys = now.map(::key).toSet()
        return now.withIndex().filter { (_, w) -> key(w) !in saidKeys && w.length >= 4 }.mapNotNull { (i, w) ->
            said.filter { s -> key(s) !in nowKeys && s.length >= 3 && s.first().lowercaseChar() == w.first().lowercaseChar() }
                .map { s -> s to distance(key(s), key(w)) }
                .filter { (s, d) -> d in 1..(if (maxOf(s.length, w.length) <= 6) 1 else 2) }
                .minByOrNull { it.second }
                // The first word of a name is capitalised by habit; a word heard in lower case stays a plain word.
                ?.let { (s, _) -> WordFix(s, if (i == 0 && s.first().isLowerCase()) w.replaceFirstChar { it.lowercase() } else w) }
        }.distinctBy { key(it.heard) }
    }

    /** The meant words, as hints for the recognizer. */
    fun vocabulary(fixes: List<WordFix>): List<String> = fixes.map { it.meant }.distinctBy(::key)

    private fun key(w: String) = w.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun tokens(text: String): List<String> = Regex("[\\p{L}\\p{N}'’]+").findAll(text).map { it.value }.toList()

    /** Positions of the longest common subsequence, as (index in a, index in b). */
    private fun lcs(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (x in n - 1 downTo 0) for (y in m - 1 downTo 0) {
            dp[x][y] = if (a[x] == b[y]) dp[x + 1][y + 1] + 1 else maxOf(dp[x + 1][y], dp[x][y + 1])
        }
        val out = mutableListOf<Pair<Int, Int>>()
        var x = 0
        var y = 0
        while (x < n && y < m) {
            when {
                a[x] == b[y] -> { out += x to y; x++; y++ }
                dp[x + 1][y] >= dp[x][y + 1] -> x++
                else -> y++
            }
        }
        return out
    }

    /** Edit distance (insert, delete, change one letter). */
    fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (x in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = x
            for (y in 1..b.length) cur[y] = minOf(prev[y] + 1, cur[y - 1] + 1, prev[y - 1] + if (a[x - 1] == b[y - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }
}
