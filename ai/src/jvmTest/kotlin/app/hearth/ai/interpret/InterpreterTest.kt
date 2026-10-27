package app.hearth.ai.interpret

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class InterpreterTest {
    private val dir = System.getenv("HEARTH_INTERPRETER_DIR")?.let(::File)?.takeIf { File(it, "model.onnx").exists() }

    @Test
    fun callsAreReadBack() {
        val text = "<start_function_call>call:add_task{day:<escape>donderdag<escape>,text:<escape>Mia ophalen<escape>}<end_function_call>" +
            "<start_function_call>call:nothing{}<end_function_call>"
        assertEquals(listOf(Action("add_task", mapOf("day" to "donderdag", "text" to "Mia ophalen"))), Interpreter.parse(text))
    }

    /** The same answer as the reference loop (Python, plain ONNX Runtime) for the same prompt. */
    @Test
    fun sameAnswerAsTheReferenceLoop() = runTest {
        dir ?: return@runTest
        val expected = File(dir, "expected.txt").takeIf { it.exists() }?.readText() ?: return@runTest
        val interpreter = Interpreter(GemmaTokenizer.parse(File(dir, "tokenizer.json").readText()), OrtDecoder(dir))
        val got = interpreter.generate(Interpreter.promptFor("maandag 2 november", listOf("dinsdag heeft Mia tandarts")), maxNew = 60)
        assertEquals(expected, got)
    }
}
