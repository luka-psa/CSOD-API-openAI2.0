package app.hearth

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import app.hearth.ai.whisper.HeardWindow
import app.hearth.ai.whisper.LogMel
import app.hearth.ai.whisper.WhisperModel
import app.hearth.ai.whisper.WhisperRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest

/**
 * Whisper on the tablet: its three files fetched once from Hugging Face (a fixed revision, each checked by size and
 * SHA-256), run by ONNX Runtime on the tablet itself. The audio it hears never leaves the tablet.
 */
class AndroidWhisperRuntime(private val context: Context) : WhisperRuntime {
    private val dir = File(context.filesDir, "whisper")

    override val available = true

    /** On by itself on a tablet (the shared screen it is made for); a phone waits for the switch. */
    override val onByDefault: Boolean get() = context.resources.configuration.smallestScreenWidthDp >= 600

    override suspend fun downloaded() = withContext(Dispatchers.IO) {
        WhisperRuntime.FILES.all { File(dir, it.name).length() == it.size }
    }

    override suspend fun download(progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val total = WhisperRuntime.FILES.sumOf { it.size }
        var done = 0L
        for (file in WhisperRuntime.FILES) {
            val into = File(dir, file.name)
            if (into.length() == file.size) { done += file.size; continue }
            val part = File(dir, file.name + ".part")
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = URL("${WhisperRuntime.REPO}/${file.path}").openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            try {
                check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            progress((done.toFloat() / total).coerceAtMost(1f))
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha != file.sha256) {
                part.delete()
                error("${file.name} came in damaged")
            }
            into.delete()
            check(part.renameTo(into)) { "Could not keep ${file.name}" }
        }
        progress(1f)
    }

    override suspend fun tokenizerJson() = withContext(Dispatchers.IO) { File(dir, "tokenizer.json").readText() }

    override suspend fun open(): WhisperModel = withContext(Dispatchers.IO) {
        val env = OrtEnvironment.getEnvironment()
        // Four threads: the tablet's fast cores; the screen keeps its own.
        val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
        val encoder = env.createSession(File(dir, "encoder.onnx").path, options)
        val decoder = env.createSession(File(dir, "decoder.onnx").path, options)
        OrtWhisperModel(env, encoder, decoder)
    }

    override suspend fun remove() = withContext(Dispatchers.IO) { dir.deleteRecursively(); Unit }
}

/** The encoder once per stretch, then the merged decoder token by token with its cache (as exported by onnx-community). */
private class OrtWhisperModel(private val env: OrtEnvironment, private val encoder: OrtSession, private val decoder: OrtSession) : WhisperModel {
    private val pastNames = decoder.inputNames.filter { it.startsWith("past_key_values.") }

    override suspend fun encode(features: FloatArray): HeardWindow = withContext(Dispatchers.Default) {
        val input = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), longArrayOf(1, LogMel.BANDS.toLong(), LogMel.FRAMES.toLong()))
        val hidden = input.use { encoder.run(mapOf("input_features" to it)) }
        Window(hidden)
    }

    private inner class Window(private val hidden: OrtSession.Result) : HeardWindow {
        private val states = hidden.get(0) as OnnxTensor
        private var past: Map<String, OnnxTensor> = pastNames.associateWith { OnnxTensor.createTensor(env, FloatBuffer.allocate(0), longArrayOf(1, 12, 0, 64)) }
        /** The first step's result holds the encoder's cache for every later step. */
        private var firstResult: OrtSession.Result? = null
        private var last: OrtSession.Result? = null

        override suspend fun step(ids: IntArray, rows: Int): Array<FloatArray> = withContext(Dispatchers.Default) {
            val first = firstResult == null
            val inputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { ids[it].toLong() }), longArrayOf(1, ids.size.toLong()))
            val branch = OnnxTensor.createTensor(env, booleanArrayOf(!first))
            val result = try {
                decoder.run(mapOf("input_ids" to inputIds, "encoder_hidden_states" to states, "use_cache_branch" to branch) + past)
            } finally {
                inputIds.close(); branch.close()
            }
            // The decoder's cache grows each step; the encoder's is made on the first step and kept.
            val empty = if (first) past.values.toList() else emptyList()
            past = pastNames.associateWith { name ->
                if (!first && ".encoder." in name) past.getValue(name)
                else result.get(name.replace("past_key_values", "present")).get() as OnnxTensor
            }
            empty.forEach { it.close() }
            if (first) {
                firstResult = result
            } else {
                last?.close()
                last = result
            }
            val logits = result.get("logits").get() as OnnxTensor
            val vocab = logits.info.shape[2].toInt()
            val all = logits.floatBuffer
            Array(rows) { r -> FloatArray(vocab).also { row -> all.position((ids.size - rows + r) * vocab); all.get(row) } }
        }

        override fun close() {
            last?.close()
            firstResult?.close()
            hidden.close()
        }
    }

    override fun close() {
        encoder.close()
        decoder.close()
    }
}
