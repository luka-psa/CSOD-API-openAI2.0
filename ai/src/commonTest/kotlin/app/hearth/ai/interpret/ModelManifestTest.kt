package app.hearth.ai.interpret

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelManifestTest {
    @Test
    fun readsTheManifestTheBuildPublishes() {
        val m = ModelManifest.parse(
            """{"version": "r3", "files": [
              {"name": "tokenizer.json", "parts": ["tokenizer.json"], "size": 10, "sha256": "aa"},
              {"name": "model.onnx.data", "parts": ["model.onnx.data.part0", "model.onnx.data.part1"], "size": 30, "sha256": "bb", "extra": 1}
            ]}""",
        )
        assertEquals("r3", m.version)
        assertEquals(40L, m.size)
        assertEquals(listOf("model.onnx.data.part0", "model.onnx.data.part1"), m.files[1].parts)
    }
}
