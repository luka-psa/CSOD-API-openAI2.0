package app.hearth.ai.llm

import app.hearth.core.tools.ToolSpec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProvidersTest {

    private class FakeTransport(vararg responses: Pair<Int, String>) : HttpTransport {
        val queue = responses.toMutableList()
        val requests = mutableListOf<Triple<String, Map<String, String>, JsonObject>>()
        override suspend fun postJson(url: String, headers: Map<String, String>, body: String): HttpResult {
            requests += Triple(url, headers, Json.parseToJsonElement(body).jsonObject)
            val (status, text) = queue.removeAt(0)
            return HttpResult(status, text)
        }
    }

    private val schema = buildJsonObject { put("type", "object") }
    private val tool = ToolSpec("add_shopping_item", "Add", false, buildJsonObject { put("type", "object") })

    @Test
    fun openAiStructuredUsesStrictJsonSchema() = runTest {
        val t = FakeTransport(200 to """{"choices":[{"message":{"content":"{\"section\":\"SHOPPING\"}"}}]}""")
        val p = createProvider(ProviderConfig(ProviderKind.OPENAI, "sk-1"), t)
        val out = p.structured(ModelRole.ROUTER, "sys", "melk", "route", schema)
        assertEquals("SHOPPING", out["section"]?.jsonPrimitive?.content)

        val (url, headers, body) = t.requests.single()
        assertEquals("https://api.openai.com/v1/chat/completions", url)
        assertEquals("Bearer sk-1", headers["Authorization"])
        assertEquals("gpt-5.4-nano", body["model"]?.jsonPrimitive?.content)
        assertEquals("true", body["response_format"]!!.jsonObject["json_schema"]!!.jsonObject["strict"]!!.jsonPrimitive.content)
        assertEquals("low", body["reasoning_effort"]?.jsonPrimitive?.content)
        assertTrue("max_completion_tokens" in body)
    }

    @Test
    fun grokUsesXaiEndpointAndMaxTokens() = runTest {
        val t = FakeTransport(200 to """{"choices":[{"message":{"content":"{}"}}]}""")
        createProvider(ProviderConfig(ProviderKind.XAI, "xai-1"), t).structured(ModelRole.ROUTER, "s", "u", "route", schema)
        val (url, _, body) = t.requests.single()
        assertEquals("https://api.x.ai/v1/chat/completions", url)
        assertTrue("max_tokens" in body)
        assertFalse("reasoning_effort" in body)
    }

    @Test
    fun openAiToolLoopRoundTrip() = runTest {
        val t = FakeTransport(
            200 to """{"choices":[{"message":{"content":null,"tool_calls":[{"id":"c1","type":"function","function":{"name":"add_shopping_item","arguments":"{\"name\":\"melk\"}"}}]}}]}""",
            200 to """{"choices":[{"message":{"content":"Done"}}]}""",
        )
        val p = createProvider(ProviderConfig(ProviderKind.OPENAI, "k"), t)
        val first = p.chat(ModelRole.HELPER, "sys", listOf(LlmMessage.User("melk")), listOf(tool))
        assertEquals(LlmToolCall("c1", "add_shopping_item", buildJsonObject { put("name", "melk") }), first.toolCalls.single())

        val second = p.chat(
            ModelRole.HELPER, "sys",
            listOf(LlmMessage.User("melk"), first.asMessage(), LlmMessage.ToolResults(listOf(LlmToolResult("c1", "ok")))),
            listOf(tool),
        )
        assertEquals("Done", second.text)
        val msgs = t.requests[1].third["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user", "assistant", "tool"), msgs.map { it["role"]!!.jsonPrimitive.content })
        assertEquals("c1", msgs[3]["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("gpt-5.4-mini", t.requests[1].third["model"]!!.jsonPrimitive.content)
    }

    @Test
    fun anthropicHelperReplaysContentAndUsesFallbacks() = runTest {
        val firstContent = """[{"type":"thinking","thinking":"","signature":"sig"},{"type":"tool_use","id":"tu1","name":"add_shopping_item","input":{"name":"melk"}}]"""
        val t = FakeTransport(
            200 to """{"stop_reason":"tool_use","content":$firstContent}""",
            200 to """{"stop_reason":"end_turn","content":[{"type":"text","text":"Melk staat erop."}]}""",
        )
        val p = createProvider(ProviderConfig(ProviderKind.ANTHROPIC, "ak"), t)
        val first = p.chat(ModelRole.HELPER, "sys", listOf(LlmMessage.User("melk")), listOf(tool))
        assertEquals("tu1", first.toolCalls.single().id)
        assertNull(first.text)

        val second = p.chat(
            ModelRole.HELPER, "sys",
            listOf(LlmMessage.User("melk"), first.asMessage(), LlmMessage.ToolResults(listOf(LlmToolResult("tu1", "ok")))),
            listOf(tool),
        )
        assertEquals("Melk staat erop.", second.text)

        val (url, headers, body) = t.requests[1]
        assertEquals("https://api.anthropic.com/v1/messages", url)
        assertEquals("2026-10-21", headers["anthropic-version"])
        assertEquals("server-side-fallback-2026-07-01", headers["anthropic-beta"])
        assertEquals(JsonPrimitive("default"), body["fallbacks"])
        assertEquals("claude-opus-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("auto", body["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("low", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        // The assistant turn is replayed byte-for-byte, thinking block included.
        assertEquals(Json.parseToJsonElement(firstContent), messages[1]["content"])
        val result = messages[2]["content"]!!.jsonArray.single().jsonObject
        assertEquals("tool_result", result["type"]!!.jsonPrimitive.content)
        assertEquals("tu1", result["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun anthropicRouterUsesStructuredOutputWithoutFallbackOnHaiku() = runTest {
        val t = FakeTransport(200 to """{"stop_reason":"end_turn","content":[{"type":"text","text":"{\"section\":\"ADMIN\"}"}]}""")
        val out = createProvider(ProviderConfig(ProviderKind.ANTHROPIC, "ak"), t)
            .structured(ModelRole.ROUTER, "sys", "belastingen", "route", schema)
        assertEquals("ADMIN", out["section"]!!.jsonPrimitive.content)
        val (_, headers, body) = t.requests.single()
        assertEquals("claude-haiku-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("json_schema", body["output_config"]!!.jsonObject["format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertFalse("fallbacks" in body)
        assertFalse("anthropic-beta" in headers)
        assertFalse("tool_choice" in body)
    }

    @Test
    fun refusalsAndErrorsAreReportedClearly() = runTest {
        val refused = FakeTransport(200 to """{"stop_reason":"refusal","content":[]}""")
        assertTrue(createProvider(ProviderConfig(ProviderKind.ANTHROPIC, "k"), refused).chat(ModelRole.HELPER, "s", listOf(LlmMessage.User("x")), emptyList()).refused)

        val bad = FakeTransport(401 to """{"error":{"message":"Incorrect API key provided"}}""")
        val e = assertFailsWith<LlmException> {
            createProvider(ProviderConfig(ProviderKind.OPENAI, "bad"), bad).structured(ModelRole.ROUTER, "s", "u", "r", schema)
        }
        assertEquals(LlmException.Reason.AUTH, e.reason)
        assertEquals("Incorrect API key provided", e.message)
    }
}
