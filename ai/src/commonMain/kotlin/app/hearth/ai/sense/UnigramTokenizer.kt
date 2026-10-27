package app.hearth.ai.sense

/**
 * The sentence model's own tokenizer (SentencePiece Unigram, as in XLM-R and multilingual-e5): spaces become "▁", each
 * word is split into the pieces with the highest total score (Viterbi), and the sentence is wrapped in <s> … </s>. Gives
 * the same ids as the model's `tokenizer.json` for ordinary text, so the tablet and the web read a sentence alike.
 */
class UnigramTokenizer(pieces: List<Pair<String, Float>>, private val unkId: Int = 3, private val bos: Int = 0, private val eos: Int = 2) {
    private val ids = HashMap<String, Int>(pieces.size * 2)
    private val scores = FloatArray(pieces.size)
    private val longest: Int
    private val unkScore: Float

    init {
        var max = 1
        var min = 0f
        pieces.forEachIndexed { i, (piece, score) ->
            // Special tokens (<s>, <pad>, </s>, <unk>) are never matched inside text.
            if (!(piece.startsWith("<") && piece.endsWith(">"))) ids[piece] = i
            scores[i] = score
            if (piece.length > max) max = piece.length
            if (score < min) min = score
        }
        longest = max
        unkScore = min - 10f
    }

    /** The ids for [text], at most [maxLength] including <s> and </s>. */
    fun encode(text: String, maxLength: Int = 128): IntArray {
        val out = ArrayList<Int>()
        out += bos
        normalize(text).split(' ').filter { it.isNotEmpty() }.forEach { word -> out += pieces("▁$word") }
        if (out.size > maxLength - 1) return (out.take(maxLength - 1) + eos).toIntArray()
        out += eos
        return out.toIntArray()
    }

    /** One word in its best pieces; a character no piece covers is <unk> (several in a row: one). */
    private fun pieces(word: String): List<Int> {
        val n = word.length
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val from = IntArray(n + 1) { -1 }
        val piece = IntArray(n + 1) { -1 }
        best[0] = 0f
        for (end in 1..n) {
            for (start in maxOf(0, end - longest) until end) {
                if (best[start] == Float.NEGATIVE_INFINITY) continue
                val id = ids[word.substring(start, end)] ?: continue
                val score = best[start] + scores[id]
                if (score > best[end]) {
                    best[end] = score; from[end] = start; piece[end] = id
                }
            }
            // Nothing ends here but the character itself: unknown.
            if (best[end] == Float.NEGATIVE_INFINITY && best[end - 1] != Float.NEGATIVE_INFINITY) {
                best[end] = best[end - 1] + unkScore; from[end] = end - 1; piece[end] = unkId
            }
        }
        val reversed = ArrayList<Int>()
        var at = n
        while (at > 0) {
            val id = piece[at]
            if (!(id == unkId && reversed.lastOrNull() == unkId)) reversed += id
            at = from[at]
        }
        return reversed.asReversed()
    }

    companion object {
        /** Whitespace as the model expects it: one plain space between words, none around. */
        fun normalize(text: String): String = text.replace(Regex("\\s+"), " ").trim()

        /**
         * The pieces and scores from a `tokenizer.json` (`"model": {"type": "Unigram", "vocab": [["piece", score], …]}`),
         * read without building the whole JSON tree: the file holds 250 000 pieces.
         */
        fun parseVocab(json: String): List<Pair<String, Float>> {
            val key = json.indexOf("\"vocab\"", json.indexOf("\"Unigram\"").coerceAtLeast(0))
            require(key >= 0) { "No Unigram vocab in tokenizer.json" }
            var i = json.indexOf('[', key) + 1
            val out = ArrayList<Pair<String, Float>>(250_100)
            val sb = StringBuilder()
            while (true) {
                while (json[i] != '[' && json[i] != ']') i++
                if (json[i] == ']') break
                i = json.indexOf('"', i) + 1
                sb.setLength(0)
                while (json[i] != '"') {
                    if (json[i] == '\\') {
                        i++
                        when (val c = json[i]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> { sb.append(json.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                            else -> sb.append(c)
                        }
                    } else sb.append(json[i])
                    i++
                }
                val comma = json.indexOf(',', i)
                val close = json.indexOf(']', comma)
                out += sb.toString() to json.substring(comma + 1, close).trim().toFloat()
                i = close + 1
            }
            return out
        }
    }
}
