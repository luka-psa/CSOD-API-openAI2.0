package app.hearth.ai.interpret

/**
 * Gemma's tokenizer (BPE with byte fallback), read from its `tokenizer.json`: the special markers
 * (`<start_of_turn>`, `<start_function_call>`, …) are kept whole, spaces become "▁", unknown characters become their
 * UTF-8 bytes (`<0x41>`), and pieces are merged in the order of the merge list. Gives the same ids as the reference
 * tokenizer; the merges live in a compact table (two ids → rank), not 515 000 strings.
 */
class GemmaTokenizer private constructor(
    private val ids: HashMap<String, Int>,
    private val pieces: Array<String>,
    private val merges: PairRanks,
    private val special: List<Pair<String, Int>>,
) {
    /** The ids for [text]; special markers inside it count as one token each. */
    fun encode(text: String): IntArray {
        val out = ArrayList<Int>()
        var at = 0
        while (at < text.length) {
            val hit = special.firstOrNull { (s, _) -> text.startsWith(s, at) }
            if (hit != null) {
                out += hit.second
                at += hit.first.length
                continue
            }
            val next = special.mapNotNull { (s, _) -> text.indexOf(s, at).takeIf { it >= 0 } }.minOrNull() ?: text.length
            bpe(text.substring(at, next).replace(' ', '▁'), out)
            at = next
        }
        return out.toIntArray()
    }

    /** One stretch of plain text: characters (or their bytes), then the merges by rank. */
    private fun bpe(text: String, out: MutableList<Int>) {
        if (text.isEmpty()) return
        val symbols = ArrayList<Int>()
        var i = 0
        while (i < text.length) {
            val step = if (text[i].isHighSurrogate() && i + 1 < text.length) 2 else 1
            val ch = text.substring(i, i + step)
            val id = ids[ch]
            if (id != null) symbols += id else ch.encodeToByteArray().forEach { b -> symbols += ids[byteToken(b)] ?: UNK }
            i += step
        }
        while (symbols.size > 1) {
            var best = -1
            var bestRank = Int.MAX_VALUE
            for (j in 0 until symbols.size - 1) {
                val r = merges.rank(symbols[j], symbols[j + 1])
                if (r in 0 until bestRank) { bestRank = r; best = j }
            }
            if (best < 0) break
            val merged = ids[pieces[symbols[best]] + pieces[symbols[best + 1]]] ?: break
            symbols[best] = merged
            symbols.removeAt(best + 1)
        }
        out += symbols
    }

    /** Back to text: "▁" is a space, byte pieces become their characters again, markers stay as they are. */
    fun decode(tokens: IntArray): String {
        val bytes = ArrayList<Byte>()
        val sb = StringBuilder()
        fun flush() { if (bytes.isNotEmpty()) { sb.append(bytes.toByteArray().decodeToString()); bytes.clear() } }
        for (t in tokens) {
            val p = pieces.getOrNull(t) ?: continue
            val byte = BYTE.matchEntire(p)?.groupValues?.get(1)?.toInt(16)
            if (byte != null) bytes += byte.toByte() else { flush(); sb.append(p.replace('▁', ' ')) }
        }
        flush()
        return sb.toString()
    }

    fun id(piece: String): Int? = ids[piece]

    companion object {
        private const val UNK = 3
        private val BYTE = Regex("<0x([0-9A-Fa-f]{2})>")
        private fun byteToken(b: Byte) = "<0x" + (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0') + ">"

        /** Reads `tokenizer.json`: the vocab (piece → id), the merges (in order) and the added tokens. */
        fun parse(json: String): GemmaTokenizer {
            val reader = JsonScan(json)
            val ids = HashMap<String, Int>(300_000)
            reader.seekKey("model")
            reader.seekKey("vocab")
            reader.expect('{')
            while (reader.peekNot('}')) {
                val piece = reader.string()
                reader.expect(':')
                ids[piece] = reader.int()
                reader.skip(',')
            }
            val pieces = Array(ids.values.max() + 1) { "" }
            ids.forEach { (p, i) -> pieces[i] = p }
            val merges = PairRanks(600_000)
            reader.seekKey("merges")
            reader.expect('[')
            var rank = 0
            while (reader.peekNot(']')) {
                reader.expect('[')
                val a = reader.string(); reader.skip(','); val b = reader.string()
                reader.expect(']'); reader.skip(',')
                val ia = ids[a]; val ib = ids[b]
                if (ia != null && ib != null) merges.put(ia, ib, rank)
                rank++
            }
            // Added tokens: their content and id (the markers the model reads and writes).
            val special = ArrayList<Pair<String, Int>>()
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
                    if (id >= 0 && content.isNotEmpty() && content.startsWith("<") && content.endsWith(">") && !BYTE.matches(content)) special += content to id
                }
            }
            // Longest first, so "<start_function_call>" wins over a shorter marker that starts the same way.
            return GemmaTokenizer(ids, pieces, merges, special.sortedByDescending { it.first.length })
        }
    }
}

/** Two token ids → merge rank, in flat arrays (open addressing): small and fast for half a million merges. */
internal class PairRanks(capacity: Int) {
    private val size = (capacity * 2).takeHighestOneBit() * 2
    private val keys = LongArray(size) { -1L }
    private val values = IntArray(size)

    private fun slot(key: Long): Int {
        var h = (key xor (key ushr 29)) * -0x61c8864680b583ebL
        h = h xor (h ushr 32)
        return (h.toInt() and (size - 1))
    }

    fun put(a: Int, b: Int, rank: Int) {
        val key = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)
        var i = slot(key)
        while (keys[i] != -1L && keys[i] != key) i = (i + 1) and (size - 1)
        if (keys[i] == -1L) { keys[i] = key; values[i] = rank }
    }

    fun rank(a: Int, b: Int): Int {
        val key = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)
        var i = slot(key)
        while (true) {
            val k = keys[i]
            if (k == -1L) return -1
            if (k == key) return values[i]
            i = (i + 1) and (size - 1)
        }
    }
}

/** Just enough JSON reading for tokenizer files: strings with escapes, numbers, and skipping a value. */
internal class JsonScan(private val s: String, var i: Int = 0) {
    fun seek(token: String) { i = s.indexOf(token, i).also { require(it >= 0) { "No $token" } } + token.length }
    /** To the value of `"key":` (a key, not the same word inside a string). */
    fun seekKey(key: String) { i = (Regex("\"" + Regex.escape(key) + "\"\\s*:").find(s, i) ?: error("No $key")).range.last + 1 }
    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
    fun expect(c: Char) { ws(); require(s[i] == c) { "Expected $c at $i" }; i++ }
    fun skip(c: Char) { ws(); if (i < s.length && s[i] == c) i++ }
    fun peekNot(c: Char): Boolean { ws(); return s[i] != c }
    fun int(): Int { ws(); val start = i; while (s[i] == '-' || s[i].isDigit()) i++; return s.substring(start, i).toInt() }
    fun string(): String {
        ws(); require(s[i] == '"') { "Expected a string at $i" }; i++
        val sb = StringBuilder()
        while (s[i] != '"') {
            if (s[i] == '\\') {
                i++
                when (val c = s[i]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                    'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                    else -> sb.append(c)
                }
            } else sb.append(s[i])
            i++
        }
        i++
        return sb.toString()
    }
    /** Skips any value (string, number, literal, object or array). */
    fun value() {
        ws()
        when (s[i]) {
            '"' -> string()
            '{', '[' -> {
                var depth = 0
                while (true) {
                    when (s[i]) {
                        '"' -> { string(); continue }
                        '{', '[' -> depth++
                        '}', ']' -> { depth--; if (depth == 0) { i++; return } }
                    }
                    i++
                }
            }
            else -> while (s[i] != ',' && s[i] != '}' && s[i] != ']') i++
        }
    }
}
