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
    private val board = app.hearth.core.data.board.BoardRepository(db)
    private val tools = HearthTools(shopping, inbox, board, app.hearth.core.data.food.StockRepository(db), app.hearth.core.data.week.WeekRepository(db), app.hearth.core.data.settings.SettingsRepository(db))
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

    @Test
    fun `offline capture reads rhythm and urgency`() = runTest {
        assertEquals(
            "Op de backlog: “lakens verversen” (huishoudtaak) · om de 2 weken",
            offline.handle("lakens verversen om de 2 weken", nl).text,
        )
        assertEquals(
            "Op de backlog: “plantjes water geven” (huishoudtaak) · elke week",
            offline.handle("plantjes water geven elke week", nl).text,
        )
        assertEquals(
            "Op de backlog: “verzekering bellen” (administratie) · dringend",
            offline.handle("dringend: verzekering bellen", nl).text,
        )
        assertEquals(emptyList(), shopping.openItems())
        assertEquals(listOf(14, 7, null), inbox.openItems().map { it.intervalDays })
    }

    @Test
    fun `bare project phrases become projects, a bare thing to buy stays shopping`() = runTest {
        assertEquals("Op de backlog: “IKEA kast” (project)", offline.handle("IKEA kast", nl).text)
        offline.handle("keuken uitkuisen", nl)
        offline.handle("lampen", nl)
        assertEquals(listOf("Lampen"), shopping.openItems().map { it.name })
        assertEquals(listOf(Section.PROJECT, Section.PROJECT), inbox.openItems().map { it.section })
    }

    @Test
    fun `offline capture puts rules, frictions and goals on the Werkbord`() = runTest {
        assertEquals("Nieuwe huisregel: “Wie kookt, wast niet af”", offline.handle("huisregel: wie kookt, wast niet af", nl).text)
        assertEquals(
            "Op het werkbord: “Ik erger me aan de rommel in de gang”. We bekijken het in het weekoverleg.",
            offline.handle("ik erger me aan de rommel in de gang", nl).text,
        )
        assertEquals("Nieuw doel (samen): “We willen samen elke maand op date”", offline.handle("we willen samen elke maand op date", nl).text)
        assertEquals("Nieuw doel (gezin): “10 km lopen”", offline.handle("doel: 10 km lopen", nl).text)
        assertEquals(emptyList(), inbox.openItems())
        assertEquals(emptyList(), shopping.openItems())
        assertEquals(4, board.allItems().size)
    }

    @Test
    fun `a spoken command names the place and only the rest becomes the item`() = runTest {
        assertEquals(
            "Nieuwe huisregel: “We brengen onze borden naar de keuken”",
            offline.handle("Voeg aan de huisregels toe dat we onze borden naar de keuken brengen", nl).text,
        )
        offline.handle("add a item to house that we have to bring our plates to the kitchen", nl)
        assertEquals(
            listOf("We brengen onze borden naar de keuken", "We have to bring our plates to the kitchen"),
            board.allItems().sortedBy { it.createdAt }.map { it.text },
        )
        offline.handle("zet fiets maken bij de projecten", nl)
        assertEquals(listOf("Fiets maken" to Section.PROJECT), inbox.openItems().map { it.text to it.section })
        assertEquals(emptyList(), shopping.openItems())
    }

    @Test
    fun `stock words mark the stock, or put it on the list when we don't keep it`() = runTest {
        app.hearth.core.data.food.StockRepository(db).let { stock ->
            shopping.ensureDefaultShops(Lang.NL)
            stock.ensureDefaults(Lang.NL) { null }
        }
        assertEquals("Koffie is op · op de lijst", offline.handle("de koffie is op", nl).text)
        assertEquals("WC-papier is bijna op · op de lijst", offline.handle("we zijn bijna door het wc-papier", nl).text)
        assertEquals("Ijsjes staat op de lijst · Supermarkt", offline.handle("de ijsjes zijn op", nl).text)
        assertEquals(listOf("Koffie", "WC-papier", "Ijsjes"), shopping.openItems().map { it.name })
    }

    @Test
    fun `bare chores with a verb are not shopping items`() = runTest {
        offline.handle("zolder opruimen", nl)
        offline.handle("fotoboek maken", nl)
        offline.handle("school bellen", nl)
        offline.handle("tandarts bellen", nl)
        offline.handle("melk", nl)
        assertEquals(listOf("Melk"), shopping.openItems().map { it.name })
        assertEquals(
            listOf(Section.PROJECT, Section.PROJECT, Section.ADMIN, Section.APPOINTMENT),
            inbox.openItems().map { it.section },
        )
    }

    @Test
    fun `recipe prefix saves a recipe`() = runTest {
        assertEquals(
            "Recept bewaard: Pannenkoeken (3 ingrediënten)",
            offline.handle("recept: pannenkoeken met bloem, melk en eieren. Eerst het beslag laten rusten.", nl).text,
        )
        assertEquals(emptyList(), shopping.openItems())
    }
}
