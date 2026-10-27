package app.hearth

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import app.hearth.ai.sense.SenseRuntime
import app.hearth.ai.sense.SentenceEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.LongBuffer

/**
 * The sentence model on the tablet: downloaded once into the app's own storage, run by ONNX Runtime on the tablet
 * itself (two threads, so the screen stays smooth). Nothing leaves the tablet when it reads a sentence.
 */
class AndroidSenseRuntime(context: Context) : SenseRuntime {
    private val dir = File(context.filesDir, "sense")
    private val model = File(dir, "model.onnx")
    private val tokenizer = File(dir, "tokenizer.json")

    override val available = true

    override suspend fun downloaded() = withContext(Dispatchers.IO) { model.exists() && tokenizer.exists() }

    override suspend fun download(progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        fetch(SenseRuntime.TOKENIZER_URL, tokenizer) { progress(it * TOKENIZER_SHARE) }
        fetch(SenseRuntime.MODEL_URL, model) { progress(TOKENIZER_SHARE + it * (1 - TOKENIZER_SHARE)) }
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
                    val buffer = ByteArray(64 * 1024)
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

    override suspend fun tokenizerJson() = withContext(Dispatchers.IO) { tokenizer.readText() }

    override suspend fun open(): SentenceEncoder {
        val env = OrtEnvironment.getEnvironment()
        val session = withContext(Dispatchers.IO) {
            env.createSession(model.path, OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })
        }
        return SentenceEncoder { ids ->
            withContext(Dispatchers.Default) {
                val shape = longArrayOf(1, ids.size.toLong())
                fun tensor(values: LongArray) = OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape)
                val inputs = mapOf(
                    "input_ids" to tensor(LongArray(ids.size) { ids[it].toLong() }),
                    "attention_mask" to tensor(LongArray(ids.size) { 1L }),
                    "token_type_ids" to tensor(LongArray(ids.size)),
                )
                try {
                    session.run(inputs).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val hidden = (result[0].value as Array<Array<FloatArray>>)[0]
                        val d = hidden[0].size
                        FloatArray(hidden.size * d) { hidden[it / d][it % d] }
                    }
                } finally {
                    inputs.values.forEach { it.close() }
                }
            }
        }
    }

    override suspend fun remove() = withContext(Dispatchers.IO) { dir.deleteRecursively(); Unit }

    private companion object {
        const val TOKENIZER_SHARE = 0.12f
    }
}
