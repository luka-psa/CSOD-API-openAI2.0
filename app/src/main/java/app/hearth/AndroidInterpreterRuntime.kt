package app.hearth

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import app.hearth.ai.interpret.Decoder
import app.hearth.ai.interpret.DecoderSession
import app.hearth.ai.interpret.INTERPRETER_URL
import app.hearth.ai.interpret.InterpreterRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Hearth's interpreter on the tablet: downloaded once into the app's own storage, run by ONNX Runtime on the tablet
 * itself. Each reading gets fresh cache buffers (512 positions per layer, written in place, as the model was exported).
 */
class AndroidInterpreterRuntime(context: Context) : InterpreterRuntime {
    private val dir = File(context.filesDir, "interpreter")

    override val available = INTERPRETER_URL.isNotBlank()

    override suspend fun downloaded() = withContext(Dispatchers.IO) { InterpreterRuntime.FILES.all { (name, _) -> File(dir, name).exists() } }

    override suspend fun download(progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        var done = 0f
        for ((name, share) in InterpreterRuntime.FILES) {
            val into = File(dir, name)
            if (!into.exists()) fetch("$INTERPRETER_URL/$name", into) { progress(done + it * share) }
            done += share
        }
        progress(1f)
    }

    /** Into a ".part" file first, so a broken download never looks finished. */
    private fun fetch(url: String, into: File, progress: (Float) -> Unit) {
        val part = File(into.path + ".part")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var got = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        got += n
                        if (total > 0) progress((got.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
            check(part.renameTo(into)) { "Could not keep ${into.name}" }
        } finally {
            connection.disconnect()
            part.delete()
        }
    }

    override suspend fun tokenizerJson() = withContext(Dispatchers.IO) { File(dir, "tokenizer.json").readText() }

    override suspend fun open(): Decoder {
        val env = OrtEnvironment.getEnvironment()
        val session = withContext(Dispatchers.IO) {
            // Four threads: the tablet's fast cores; the screen keeps its own.
            env.createSession(File(dir, "model.onnx").path, OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) })
        }
        val layers = session.inputNames.count { it.startsWith("past_key_values.") && it.endsWith(".key") }
        return Decoder {
            object : DecoderSession {
                private var past: Map<String, OnnxTensor> = (0 until layers).flatMap { i -> listOf("key", "value").map { "past_key_values.$i.$it" } }
                    .associateWith { OnnxTensor.createTensor(env, FloatBuffer.allocate(CAP * HEAD), longArrayOf(1, 1, CAP.toLong(), HEAD.toLong())) }
                private var total = 0
                private var last: OrtSession.Result? = null

                override suspend fun step(ids: IntArray): FloatArray = withContext(Dispatchers.Default) {
                    total += ids.size
                    val inputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { ids[it].toLong() }), longArrayOf(1, ids.size.toLong()))
                    val mask = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(total) { 1L }), longArrayOf(1, total.toLong()))
                    val result = try {
                        session.run(mapOf("input_ids" to inputIds, "attention_mask" to mask) + past)
                    } finally {
                        inputIds.close(); mask.close()
                    }
                    // The cache comes back as the next step's past; the step before it can go.
                    val next = result.associate { (name, value) -> name.replace("present", "past_key_values") to value as OnnxTensor }
                        .filterKeys { it.startsWith("past_key_values") }
                    if (last == null) past.values.forEach { it.close() } else last!!.close()
                    last = result
                    past = next
                    val logits = (result.get("logits").get() as OnnxTensor).floatBuffer
                    FloatArray(logits.remaining()).also { logits.get(it) }
                }

                override fun close() {
                    last?.close() ?: past.values.forEach { it.close() }
                }
            }
        }
    }

    override suspend fun remove() = withContext(Dispatchers.IO) { dir.deleteRecursively(); Unit }

    private companion object {
        const val CAP = 512
        const val HEAD = 256
    }
}
