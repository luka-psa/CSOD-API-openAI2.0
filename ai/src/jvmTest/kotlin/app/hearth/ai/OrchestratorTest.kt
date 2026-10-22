package app.hearth.ai

import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.model.Lang
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrchestratorTest {
    private val db = inMemoryHearthDatabase()
    private val repo = ShoppingRepository(db)
    private val orchestrator = Orchestrator(HearthTools(repo).registry)
    private val ctx = ToolContext(ToolSource.CHAT, Lang.NL)

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `chat adds items and the list shows them`() = runTest {
        repo.ensureDefaultShops(Lang.NL)

        val added = orchestrator.handle("eieren en brood", ctx)
        assertEquals("Eieren staat op de lijst · Supermarkt\nBrood staat op de lijst · Supermarkt", added.text)

        val pharmacy = orchestrator.handle("niet vergeten: medicijnen bij de apotheek", ctx)
        assertEquals("Medicijnen staat op de lijst · Apotheek", pharmacy.text)

        assertEquals(listOf("Eieren", "Brood", "Medicijnen"), repo.openItems().map { it.name })
    }

    @Test
    fun `replies in the language the person used`() = runTest {
        repo.ensureDefaultShops(Lang.NL)
        assertEquals("Added Milk · Supermarkt", orchestrator.handle("add milk", ctx).text)
    }

    @Test
    fun `unknown requests get a friendly hint`() = runTest {
        val reply = orchestrator.handle("what's open?", ctx.copy(lang = Lang.EN))
        assertTrue(reply.outcomes.isEmpty())
        assertTrue(reply.text.startsWith("I can't do that one yet"))
    }
}
