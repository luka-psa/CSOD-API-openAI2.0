package app.hearth.ai.whisper

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Whisper on the JVM (tests), the way the tablet runs it: the int8 encoder, and the merged decoder with its cache. */
class OrtWhisper(dir: File, threads: Int = 4) : WhisperModel {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) }
    private val encoder = env.createSession(File(dir, "encoder_model_quantized.onnx").path, options)
    private val decoder = env.createSession(File(dir, "decoder_model_merged_quantized.onnx").path, options)
    private val pastNames = decoder.inputNames.filter { it.startsWith("past_key_values.") }

    override suspend fun encode(features: FloatArray): HeardWindow {
        val input = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), longArrayOf(1, LogMel.BANDS.toLong(), LogMel.FRAMES.toLong()))
        val hidden = input.use { encoder.run(mapOf("input_features" to it)) }
        val states = hidden.get(0) as OnnxTensor
        return object : HeardWindow {
            private var past: Map<String, OnnxTensor> = pastNames.associateWith { OnnxTensor.createTensor(env, FloatBuffer.allocate(0), longArrayOf(1, 12, 0, 64)) }
            /** The first step's result holds the encoder's cache for every later step. */
            private var firstResult: OrtSession.Result? = null
            private var last: OrtSession.Result? = null

            override suspend fun step(ids: IntArray, rows: Int): Array<FloatArray> {
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
                return Array(rows) { r -> FloatArray(vocab).also { row -> all.position((ids.size - rows + r) * vocab); all.get(row) } }
            }

            override fun close() {
                last?.close()
                firstResult?.close()
                hidden.close()
            }
        }
    }

    override fun close() {
        encoder.close()
        decoder.close()
    }
}
