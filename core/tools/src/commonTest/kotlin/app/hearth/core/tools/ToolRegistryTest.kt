package app.hearth.core.tools

import app.hearth.core.model.Lang
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolRegistryTest {

    enum class Size { QUICK, PROJECT }

    @Serializable
    data class DemoArgs(
        @Describe("The title") val title: String,
        val size: Size = Size.QUICK,
        val steps: List<String> = emptyList(),
        val minutes: Int? = null,
        val urgent: Boolean = false,
    )

    @Serializable
    data class DemoResult(override val summary: String) : ToolResult

    private class DemoTool(override val destructive: Boolean) : Tool<DemoArgs, DemoResult> {
        var runs = 0
        override val name = "demo_tool"
        override val description = "Demo"
        override val argsSerializer = DemoArgs.serializer()
        override val resultSerializer = DemoResult.serializer()
        override suspend fun run(args: DemoArgs, ctx: ToolContext): DemoResult {
            runs++
            return DemoResult("ran ${args.title}")
        }
    }

    private val ctx = ToolContext(ToolSource.CHAT, Lang.EN)

    @Test
    fun schemaIsGeneratedFromTheArgsClass() {
        val schema = DemoArgs.serializer().descriptor.toJsonSchema()
        val expected = Json.parseToJsonElement(
            """
            {"type":"object","properties":{
              "title":{"type":"string","description":"The title"},
              "size":{"type":"string","enum":["QUICK","PROJECT"]},
              "steps":{"type":"array","items":{"type":"string"}},
              "minutes":{"type":"integer"},
              "urgent":{"type":"boolean"}
            },"required":["title"],"additionalProperties":false}
            """,
        )
        assertEquals(expected, schema)
    }

    @Test
    fun destructiveToolsWaitForConfirmationOutsideTheUi() = runTest {
        val tool = DemoTool(destructive = true)
        val registry = ToolRegistry(listOf(tool))
        val call = ToolCall("demo_tool", JsonObject(mapOf("title" to JsonPrimitive("x"))))

        val pending = registry.execute(call, ctx)
        assertTrue(pending is ToolOutcome.NeedsConfirmation)
        assertEquals(0, tool.runs)

        val done = registry.execute(call, ctx, confirmed = true)
        assertEquals("ran x", done.summary)
        assertEquals(1, tool.runs)

        val uiFailure = runCatching { registry.run(tool, DemoArgs("y"), ctx.copy(source = ToolSource.UI)) }
        assertTrue(uiFailure.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun specsExposeEveryTool() {
        val registry = ToolRegistry(listOf(DemoTool(destructive = false)))
        val spec = registry.specs.single()
        assertEquals("demo_tool", spec.name)
        assertFalse(spec.destructive)
    }
}
