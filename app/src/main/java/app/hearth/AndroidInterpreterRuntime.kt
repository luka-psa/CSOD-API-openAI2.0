package app.hearth

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import app.hearth.ai.interpret.Decoder
import app.hearth.ai.interpret.DecoderSession
import app.hearth.ai.interpret.INTERPRETER_URL
import app.hearth.ai.interpret.InterpreterRuntime
import app.hearth.ai.interpret.ModelManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Hearth's interpreter on the tablet: fetched once in the background from the household's own download site (its
 * manifest, then each file in parts, joined and checked), run by ONNX Runtime on the tablet itself. Each reading gets
 * fresh cache buffers (512 positions per layer, written in place, as the model was exported).
 */
class AndroidInterpreterRuntime(private val context: Context) : InterpreterRuntime {
    private val dir = File(context.filesDir, "interpreter")
    private val local = File(dir, "manifest.json")

    override val available = INTERPRETER_URL.isNotBlank()

    /** On by itself on a tablet (the shared screen it is made for); a phone waits for the switch. */
    override val onByDefault: Boolean get() = context.resources.configuration.smallestScreenWidthDp >= 600

    override suspend fun downloaded() = withContext(Dispatchers.IO) {
        local.exists() && runCatching { ModelManifest.parse(local.readText()).files.all { File(dir, it.name).length() == it.size } }.getOrDefault(false)
    }

    override suspend fun outdated(): Boolean = withContext(Dispatchers.IO) {
        val mine = runCatching { ModelManifest.parse(local.readText()).version }.getOrNull() ?: return@withContext true
        val theirs = runCatching { ModelManifest.parse(text("$INTERPRETER_URL/manifest.json")).version }.getOrNull() ?: return@withContext false
        mine != theirs
    }

    override suspend fun download(progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val manifest = ModelManifest.parse(text("$INTERPRETER_URL/manifest.json"))
        dir.mkdirs()
        local.delete()
        var done = 0L
        for (file in manifest.files) {
            val into = File(dir, file.name)
            val part = File(dir, file.name + ".part")
            val digest = MessageDigest.getInstance("SHA-256")
            part.outputStream().use { output ->
                for (name in file.parts) fetch("$INTERPRETER_URL/$name", output, digest) { done += it; progress(done.toFloat() / manifest.size) }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            check(sha == file.sha256) { "${file.name} came in damaged" }
            into.delete()
            check(part.renameTo(into)) { "Could not keep ${file.name}" }
        }
        // Written last: only a complete, checked model counts as downloaded.
        local.writeText(Json.encodeToString(ModelManifest.serializer(), manifest))
        progress(1f)
    }

    private fun text(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** One part, appended to [output]; [got] hears each block's size. */
    private fun fetch(url: String, output: OutputStream, digest: MessageDigest, got: (Int) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    got(n)
                }
            }
        } finally {
            connection.disconnect()
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
