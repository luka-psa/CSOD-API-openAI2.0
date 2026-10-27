package app.hearth.ai.interpret

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** The interpreter's model on the JVM (tests), the way the tablet runs it: shared cache buffers of 512 positions. */
class OrtDecoder(dir: File, threads: Int = 4) : Decoder {
    private val env = OrtEnvironment.getEnvironment()
    private val session = env.createSession(File(dir, "model.onnx").path, OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) })
    private val layers = session.inputNames.count { it.startsWith("past_key_values.") && it.endsWith(".key") }

    override suspend fun open(): DecoderSession = object : DecoderSession {
        private var past: Map<String, OnnxTensor> = (0 until layers).flatMap { i -> listOf("key", "value").map { "past_key_values.$i.$it" } }
            .associateWith { OnnxTensor.createTensor(env, FloatBuffer.allocate(CAP * HEAD), longArrayOf(1, 1, CAP.toLong(), HEAD.toLong())) }
        private var total = 0
        private var last: OrtSession.Result? = null

        override suspend fun step(ids: IntArray): FloatArray {
            total += ids.size
            val inputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { ids[it].toLong() }), longArrayOf(1, ids.size.toLong()))
            val mask = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(total) { 1L }), longArrayOf(1, total.toLong()))
            val result = session.run(mapOf("input_ids" to inputIds, "attention_mask" to mask) + past)
            inputIds.close(); mask.close()
            // The cache comes back as the next step's past; the previous result (and the first empty buffers) can go.
            val next = result.associate { (name, value) -> name.replace("present", "past_key_values") to value as OnnxTensor }.filterKeys { it.startsWith("past_key_values") }
            if (last == null) past.values.forEach { it.close() } else last!!.close()
            last = result
            past = next
            val logits = result.get("logits").get() as OnnxTensor
            return logits.floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
        }

        override fun close() {
            last?.close() ?: past.values.forEach { it.close() }
        }
    }

    private companion object {
        const val CAP = 512
        const val HEAD = 256
    }
}
