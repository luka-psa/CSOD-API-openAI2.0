package app.hearth.ai.whisper

import app.hearth.ai.interpret.JsonScan
import app.hearth.ai.interpret.PairRanks

/**
 * Whisper's tokenizer (GPT-2 style byte-level BPE), read from its `tokenizer.json`: text is cut into words the way the
 * reference does, each word's UTF-8 bytes become visible characters, and pieces merge by rank. Gives the same ids as
 * the reference tokenizer, so the household's own words can go in front as a prompt; decoding skips the markers.
 */
class WhisperTokenizer private constructor(
    private val ids: HashMap<String, Int>,
    private val pieces: Array<String?>,
    private val merges: PairRanks,
    private val special: Map<String, Int>,
) {
    /** The id of a marker such as `<|nl|>` or `<|startoftranscript|>`. */
    fun special(marker: String): Int = special[marker] ?: error("No $marker in this tokenizer")

    fun specialOrNull(marker: String): Int? = special[marker]

    /** The ids for plain [text] (no markers). */
    fun encode(text: String): IntArray {
        val out = ArrayList<Int>()
        WORDS.findAll(text).forEach { m ->
            val symbols = ArrayList<Int>()
            m.value.encodeToByteArray().forEach { b -> symbols += ids.getValue(BYTE_CHAR[b.toInt() and 0xFF].toString()) }
            while (symbols.size > 1) {
                var best = -1
                var bestRank = Int.MAX_VALUE
                for (j in 0 until symbols.size - 1) {
                    val r = merges.rank(symbols[j], symbols[j + 1])
                    if (r in 0 until bestRank) { bestRank = r; best = j }
                }
                if (best < 0) break
                // Every pair with this rank merges at once, left to right, as the reference does.
                val a = symbols[best]
                val b = symbols[best + 1]
                val merged = ids[pieces[a] + pieces[b]] ?: break
                var j = 0
                while (j < symbols.size - 1) {
                    if (symbols[j] == a && symbols[j + 1] == b) {
                        symbols[j] = merged
                        symbols.removeAt(j + 1)
                    }
                    j++
                }
            }
            out += symbols
        }
        return out.toIntArray()
    }

    /** Back to text; markers (ids from `<|endoftext|>` on) are left out. */
    fun decode(tokens: IntArray): String {
        val bytes = ArrayList<Byte>()
        for (t in tokens) {
            if (t >= FIRST_SPECIAL) continue
            pieces.getOrNull(t)?.forEach { c -> CHAR_BYTE[c]?.let { bytes += it } }
        }
        return bytes.toByteArray().decodeToString()
    }

    companion object {
        /** Ids from here on are markers (end of text, languages, tasks, timestamps). */
        const val FIRST_SPECIAL = 50257

        /** GPT-2's pre-tokenizer: contractions, words, numbers, other signs, and spaces, each with its leading space. */
        private val WORDS = Regex("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+")

        /** Bytes as visible characters: printable Latin-1 stays itself, the rest moves to 256 and up. */
        private val BYTE_CHAR: CharArray = run {
            val keep = (33..126) + (161..172) + (174..255)
            var extra = 0
            CharArray(256) { b -> if (b in keep) b.toChar() else (256 + extra++).toChar() }
        }
        private val CHAR_BYTE: Map<Char, Byte> = BYTE_CHAR.withIndex().associate { (b, c) -> c to b.toByte() }

        /** Reads `tokenizer.json`: the vocab, the merges ("a b", in order) and the added markers. */
        fun parse(json: String): WhisperTokenizer {
            val reader = JsonScan(json)
            val ids = HashMap<String, Int>(60_000)
            reader.seekKey("model")
            reader.seekKey("vocab")
            reader.expect('{')
            while (reader.peekNot('}')) {
                val piece = reader.string()
                reader.expect(':')
                ids[piece] = reader.int()
                reader.skip(',')
            }
            val merges = PairRanks(60_000)
            reader.seekKey("merges")
            reader.expect('[')
            var rank = 0
            while (reader.peekNot(']')) {
                val (a, b) = if (reader.peekNot('[')) reader.string().split(' ', limit = 2)
                else { reader.expect('['); val x = reader.string(); reader.skip(','); val y = reader.string(); reader.expect(']'); listOf(x, y) }
                reader.skip(',')
                val ia = ids[a]
                val ib = ids[b]
                if (ia != null && ib != null) merges.put(ia, ib, rank)
                rank++
            }
            val special = HashMap<String, Int>()
            val added = json.indexOf("\"added_tokens\"")
            if (added >= 0) {
                val r = JsonScan(json, added)
                r.seek("[")
                while (r.peekNot(']')) {
                    r.expect('{')
                    var id = -1
                    var content = ""
                    while (r.peekNot('}')) {
                        val key = r.string(); r.expect(':')
                        when (key) {
                            "id" -> id = r.int()
                            "content" -> content = r.string()
                            else -> r.value()
                        }
                        r.skip(',')
                    }
                    r.expect('}'); r.skip(',')
                    if (id >= 0) special[content] = id
                }
            }
            val pieces = arrayOfNulls<String>(maxOf(ids.values.max(), special.values.maxOrNull() ?: 0) + 1)
            ids.forEach { (p, i) -> pieces[i] = p }
            return WhisperTokenizer(ids, pieces, merges, special)
        }
    }
}
