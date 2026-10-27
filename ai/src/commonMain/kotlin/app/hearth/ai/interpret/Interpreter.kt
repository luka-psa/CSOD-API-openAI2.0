package app.hearth.ai.interpret

/** One thing to do that the interpreter read from the talk: a Hearth action by its short name, with its words. */
data class Action(val name: String, val args: Map<String, String>)

/** The model, one step at a time: token ids in, scores for the next token out. The platform keeps the cache. */
interface DecoderSession {
    suspend fun step(ids: IntArray): FloatArray
    fun close()
}

fun interface Decoder {
    /** A fresh session (an empty cache), for one conversation thread. */
    suspend fun open(): DecoderSession
}

/**
 * Hearth's interpreter: a small language model (FunctionGemma 270M, fine-tuned on household talk) that reads one topic of
 * the weekoverleg as a whole (what was proposed, doubted, agreed, moved or cancelled) and writes the actions as they stand
 * now. Greedy and short: at most [maxNew] tokens, stopping at the end of its turn.
 */
class Interpreter(private val tokenizer: GemmaTokenizer, private val decoder: Decoder) {
    private val stops = listOfNotNull(tokenizer.id("<end_of_turn>"), tokenizer.id("<start_function_response>"), tokenizer.id("<eos>")).toSet()

    /** What [lines] (one topic, oldest first) come down to, on [date] ("maandag 2 november"). */
    suspend fun read(date: String, lines: List<String>, maxNew: Int = 120): List<Action> {
        val text = generate(promptFor(date, fit(date, lines)), maxNew)
        return parse(text)
    }

    /** The raw answer, for tests and the benchmark. */
    suspend fun generate(prompt: String, maxNew: Int = 120): String {
        val session = decoder.open()
        try {
            var feed = tokenizer.encode(prompt)
            val out = ArrayList<Int>()
            repeat(maxNew) {
                val scores = session.step(feed)
                var best = 0
                for (i in 1 until scores.size) if (scores[i] > scores[best]) best = i
                if (best in stops) return tokenizer.decode(out.toIntArray())
                out += best
                feed = intArrayOf(best)
            }
            return tokenizer.decode(out.toIntArray())
        } finally {
            session.close()
        }
    }

    /** The oldest lines go first when a topic grows too long for the model's window. */
    private fun fit(date: String, lines: List<String>): List<String> {
        var kept = lines
        while (kept.size > 1 && tokenizer.encode(promptFor(date, kept)).size > MAX_PROMPT) kept = kept.drop(1)
        return kept
    }

    companion object {
        /** Prompt and answer fit the model's 512-position cache. */
        const val MAX_PROMPT = 380

        /** Exactly as in training. */
        fun promptFor(date: String, lines: List<String>): String =
            "<bos><start_of_turn>developer\nHearth weekoverleg, $date. Zet om in acties.<end_of_turn>\n<start_of_turn>user\n" +
                lines.joinToString("\n") { "- ${it.trim()}" } + "<end_of_turn>\n<start_of_turn>model\n"

        private val CALL = Regex("<start_function_call>call:(\\w+)\\{(.*?)\\}<end_function_call>", RegexOption.DOT_MATCHES_ALL)
        private val ARG = Regex("(\\w+):<escape>(.*?)<escape>", RegexOption.DOT_MATCHES_ALL)

        /** The calls in an answer ("nothing" is no action). */
        fun parse(text: String): List<Action> = CALL.findAll(text).mapNotNull { m ->
            val name = m.groupValues[1]
            if (name == "nothing") null else Action(name, ARG.findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2].trim() })
        }.toList()
    }
}
