package app.hearth.ai.whisper

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/** A stretch of speech to write down: where it starts in the recording (ms) and its samples (16 kHz). */
class SpeechChunk(val startMs: Long, val samples: FloatArray) {
    val durationMs: Long get() = samples.size * 1000L / LogMel.RATE
}

/**
 * Cuts a running recording into stretches of speech for Whisper, at the pauses people make anyway: nothing is cut
 * mid-word, nothing is lost between stretches, and silence never reaches the model (where it makes things up). A frame
 * (30 ms) starts speech when it is clearly louder than the room has been lately, and speech goes on while frames stay
 * above the room (a quiet voice, soft consonants); a stretch ends at a pause once it is long
 * enough to read well (Whisper reads 30 s at a time), at a long pause whatever its length, or at [MAX_MS] at its quietest
 * moment. Stretches with hardly any speech in them are dropped.
 */
class SpeechCutter {
    private val frame = FRAME_MS * LogMel.RATE / 1000
    private var pending = FloatArray(0)
    /** Samples of the current stretch (and the bit just before speech started). */
    private val current = ArrayList<FloatArray>()
    private var currentStart = 0L
    private var currentFrames = 0
    private var speechFrames = 0
    private var silentRun = 0
    private var inSpeech = false
    /** Frames seen since the start, and the room's level (dB): it follows quiet moments down quickly, talk up slowly. */
    private var frames = 0L
    private var floor = -60f
    /** The frames just before speech starts, so the first syllable isn't clipped. */
    private val preroll = ArrayDeque<FloatArray>()

    /** True while someone is speaking (with a little hangover). */
    val speaking: Boolean get() = inSpeech && silentRun * FRAME_MS < HANGOVER_MS

    /** Feeds samples (-1..1); returns the stretches that ended in them. */
    fun feed(samples: FloatArray): List<SpeechChunk> {
        val all = if (pending.isEmpty()) samples else pending + samples
        val out = ArrayList<SpeechChunk>()
        var at = 0
        while (at + frame <= all.size) {
            onFrame(all.copyOfRange(at, at + frame))?.let(out::add)
            at += frame
        }
        pending = all.copyOfRange(at, all.size)
        return out
    }

    /** The end of the recording: what is still open becomes a stretch. */
    fun flush(): List<SpeechChunk> = listOfNotNull(close(trailing = silentRun))

    private fun onFrame(f: FloatArray): SpeechChunk? {
        var sum = 0.0
        for (v in f) sum += v * v
        val db = (10 * log10(sum / f.size + 1e-10)).toFloat()
        floor = if (frames == 0L) db else if (db < floor) floor + (db - floor) * FALL else floor + RISE_DB
        frames++
        // Speech starts clearly above the room and goes on while it stays above it (soft consonants, a quiet voice).
        val loud = db > max(floor + START_DB, MIN_DB)
        val voiced = db > max(floor + KEEP_DB, MIN_DB)
        if (!inSpeech) {
            preroll.addLast(f)
            if (preroll.size > PREROLL_MS / FRAME_MS) preroll.removeFirst()
            if (!loud) return null
            // Speech starts: the stretch begins a little before it.
            inSpeech = true
            currentStart = (frames - preroll.size) * FRAME_MS
            current.addAll(preroll)
            currentFrames = preroll.size
            preroll.clear()
            speechFrames = 1
            silentRun = 0
            return null
        }
        current += f
        currentFrames++
        if (voiced) { speechFrames++; silentRun = 0 } else silentRun++
        val length = currentFrames * FRAME_MS
        val pause = silentRun * FRAME_MS
        return when {
            pause >= LONG_PAUSE_MS -> close(trailing = silentRun)
            pause >= PAUSE_MS && length >= MIN_MS -> close(trailing = silentRun)
            length >= MAX_MS -> cutAtQuietest()
            else -> null
        }
    }

    /** Ends the stretch, without the silence at its end beyond a short tail. */
    private fun close(trailing: Int): SpeechChunk? {
        if (current.isEmpty()) return null
        val keep = currentFrames - max(0, trailing - TAIL_MS / FRAME_MS)
        val chunk = if (speechFrames * FRAME_MS >= MIN_SPEECH_MS) SpeechChunk(currentStart, join(current.subList(0, keep))) else null
        reset()
        return chunk
    }

    /** A stretch at its longest: cut where it was quietest in its last seconds, the rest starts the next one. */
    private fun cutAtQuietest(): SpeechChunk? {
        val from = max(0, current.size - SEARCH_MS / FRAME_MS)
        val energy = (from until current.size).map { i -> current[i].sumOf { (it * it).toDouble() } }
        // The latest of the quietest moments (within 1 dB), so the stretch stays as long as it can.
        val lowest = energy.min()
        val quietest = from + energy.indexOfLast { it <= lowest * 1.26 }
        val head = current.subList(0, quietest + 1).toList()
        val tail = current.subList(quietest + 1, current.size).toList()
        val chunk = SpeechChunk(currentStart, join(head))
        val tailStart = currentStart + (quietest + 1) * FRAME_MS.toLong()
        reset()
        if (tail.isNotEmpty()) {
            inSpeech = true
            current.addAll(tail)
            currentFrames = tail.size
            currentStart = tailStart
            speechFrames = tail.size
        }
        return chunk
    }

    private fun reset() {
        current.clear()
        currentFrames = 0
        speechFrames = 0
        silentRun = 0
        inSpeech = false
    }

    private fun join(parts: List<FloatArray>): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var at = 0
        for (p in parts) { p.copyInto(out, at); at += p.size }
        return out
    }

    companion object {
        const val FRAME_MS = 30
        /** A stretch is read once it is this long and someone pauses. */
        const val MIN_MS = 6_000
        /** Whisper reads 30 s at a time; a stretch never gets longer than this. */
        const val MAX_MS = 28_000
        const val PAUSE_MS = 700
        /** A pause this long ends a stretch however short it is. */
        const val LONG_PAUSE_MS = 2_000
        const val HANGOVER_MS = 300
        const val PREROLL_MS = 500
        const val TAIL_MS = 300
        /** Where to look for the quietest moment when a stretch gets too long. */
        const val SEARCH_MS = 6_000
        const val MIN_SPEECH_MS = 400
        private const val START_DB = 9f
        private const val KEEP_DB = 4f
        /** Quieter than this is no voice, however quiet the room (a tablet a few metres away still hears more). */
        private const val MIN_DB = -70f
        /** The room's level drops to a quiet frame fast and creeps up 0.33 dB a second while people talk. */
        private const val FALL = 0.2f
        private const val RISE_DB = 0.01f
    }
}
