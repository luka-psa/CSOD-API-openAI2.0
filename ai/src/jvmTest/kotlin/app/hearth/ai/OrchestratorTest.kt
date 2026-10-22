package app.hearth.ai

import app.hearth.ai.llm.LlmException
import app.hearth.ai.llm.LlmMessage
import app.hearth.ai.llm.LlmProvider
import app.hearth.ai.llm.LlmReply
import app.hearth.ai.llm.LlmToolCall
import app.hearth.ai.llm.ModelRole
import app.hearth.ai.llm.ProviderConfig
import app.hearth.ai.llm.ProviderKind
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.model.Plan
import kotlinx.datetime.LocalDate
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolSource
import app.hearth.core.tools.ToolSpec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OrchestratorTest {
    private val db = inMemoryHearthDatabase()
    private val shopping = ShoppingRepository(db)
    private val inbox = BacklogRepository(db)
    private val tools = HearthTools(shopping, inbox)
    private val nl = ToolContext(ToolSource.CHAT, Lang.NL, today = LocalDate(2026, 10, 22))

    /** Scripted model: a queue of router decisions and helper replies. */
    private class FakeLlm : LlmProvider {
        override val config = ProviderConfig(ProviderKind.OPENAI, "k")
        val routes = ArrayDeque<JsonObject>()
        val replies = ArrayDeque<LlmReply>()
        var fail: LlmException? = null
        val helperToolSets = mutableListOf<List<String>>()

        override suspend fun structured(role: ModelRole, system: String, user: String, schemaName: String, schema: JsonObject): JsonObject {
            fail?.let { throw it }
            return routes.removeFirst()
        }

        override suspend fun chat(role: ModelRole, system: String, messages: List<LlmMessage>, tools: List<ToolSpec>): LlmReply {
            helperToolSets += tools.map { it.name }
            return replies.removeFirst()
        }
    }

    private fun route(section: Section, intent: String, text: String, language: String = "nl", reply: String = "") =
        buildJsonObject {
            put("section", section.name); put("intent", intent); put("text", text); put("language", language); put("reply", reply)
        }

    private val llm = FakeLlm()
    private val online = Orchestrator(tools.registry, provider = { llm })
    private val offline = Orchestrator(tools.registry)

    @BeforeTest
    fun seed() = runTest { shopping.ensureDefaultShops(Lang.NL) }

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `the dentist goes to the backlog as an appointment, not on the shopping list`() = runTest {
        llm.routes += route(Section.APPOINTMENT, "ADD", "Need to go to the dentist", "en")
        val reply = online.handle("need to go to the dentist", nl)
        assertEquals("On the backlog: “Need to go to the dentist” (appointment)", reply.text)
        assertTrue(shopping.openItems().isEmpty())
        assertEquals(Section.APPOINTMENT, inbox.openItems().single().section)
    }

    @Test
    fun `shopping goes through the helper with only shopping tools`() = runTest {
        llm.routes += route(Section.SHOPPING, "ADD", "Paracetamol bij de apotheek")
        llm.replies += LlmReply(
            null,
            listOf(LlmToolCall("c1", "add_shopping_item", buildJsonObject { put("name", "paracetamol"); put("shop", "apotheek") })),
            null,
        )
        llm.replies += LlmReply("Paracetamol staat op de lijst bij de apotheek.", emptyList(), null)

        val reply = online.handle("we hebben paracetamol nodig van de apotheek", nl)
        assertEquals("Paracetamol staat op de lijst bij de apotheek.", reply.text)
        assertEquals("Apotheek", shopping.allShops().first { it.id == shopping.openItems().single().shopId }.name)
        assertTrue(llm.helperToolSets.first().none { it.contains("backlog") })
    }

    @Test
    fun `deleting waits for a yes and then runs`() = runTest {
        shopping.insertItem("Kaas", shopping.allShops().first().id, null, null)
        llm.routes += route(Section.SHOPPING, "DONE", "Kaas schrappen")
        llm.replies += LlmReply(
            null,
            listOf(LlmToolCall("c1", "remove_shopping_item", buildJsonObject { putJsonArray("names") { add("kaas") } })),
            null,
        )
        val reply = online.handle("kaas mag eraf, was een vergissing", nl)
        val pending = assertNotNull(reply.pending)
        assertEquals("kaas van de lijst verwijderen?", reply.text)
        assertEquals(1, shopping.openItems().size)

        assertEquals("Verwijderd: Kaas", online.confirm(pending, nl).text)
        assertTrue(shopping.openItems().isEmpty())
    }

    @Test
    fun `small talk gets the router's reply and stores nothing`() = runTest {
        llm.routes += route(Section.OTHER, "CHAT", "", reply = "Graag gedaan!")
        assertEquals("Graag gedaan!", online.handle("merci!", nl).text)
        assertTrue(inbox.openItems().isEmpty())
    }

    @Test
    fun `clear commands skip the network entirely`() = runTest {
        llm.fail = LlmException(LlmException.Reason.NETWORK, "should not be called")
        assertEquals("Melk staat op de lijst · Supermarkt", online.handle("voeg melk toe", nl).text)
    }

    @Test
    fun `when the AI is unreachable nothing is lost`() = runTest {
        llm.fail = LlmException(LlmException.Reason.NETWORK, "offline")
        val reply = online.handle("lampen ophangen in het toilet", nl)
        assertEquals(
            "Even geen verbinding met de AI. Ik heb het veilig bewaard:\nOp de backlog: “lampen ophangen in het toilet” (project)",
            reply.text,
        )
    }

    @Test
    fun `offline sorting keeps bare appointment words off the shopping list`() = runTest {
        offline.handle("tandarts", nl)
        offline.handle("melk", nl)
        assertEquals(listOf("Melk"), shopping.openItems().map { it.name })
        assertEquals(listOf(Section.APPOINTMENT), inbox.openItems().map { it.section })
    }

    @Test
    fun `offline questions show what is open`() = runTest {
        offline.handle("melk", nl)
        offline.handle("autokeuring", nl)
        val reply = offline.handle("wat is er nog open?", nl)
        assertEquals("Supermarkt: Melk\nautokeuring (afspraak, in te plannen)", reply.text)
    }

    @Test
    fun `offline capture reads time words and done words`() = runTest {
        assertEquals("Op de backlog: “tandarts” (afspraak) · morgen", offline.handle("tandarts morgen", nl).text)
        assertEquals(
            "Op de backlog: “belastingbrief” (administratie) · klaar tegen morgen",
            offline.handle("belastingbrief tegen vrijdag", nl).text,
        )
        val friday = inbox.openItems().first { it.text == "belastingbrief" }
        assertEquals(Plan.DAY, friday.plan)
        assertEquals("2026-10-23", friday.date)

        assertEquals("Gedaan: “tandarts”. Mooi zo!", offline.handle("de tandarts is geregeld", nl).text)
        assertEquals(listOf("belastingbrief"), inbox.openItems().map { it.text })
    }
}
