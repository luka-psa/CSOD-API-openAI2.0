package app.hearth.core.tools.food

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.model.Lang
import app.hearth.core.model.ShopKind
import app.hearth.core.model.StockState
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The restock loop: low or out goes on the list, bought brings it back in the house. */
class StockToolsTest {
    private val db = inMemoryHearthDatabase()
    private val shopping = ShoppingRepository(db)
    private val stock = StockRepository(db)
    private val tools = HearthTools(shopping, BacklogRepository(db), BoardRepository(db), stock, app.hearth.core.data.week.WeekRepository(db), app.hearth.core.data.settings.SettingsRepository(db), app.hearth.core.data.week.MeetingRepository(db))
    private val nl = ToolContext(ToolSource.UI, Lang.NL)

    @BeforeTest
    fun setUp() = runTest {
        shopping.ensureDefaultShops(Lang.NL)
        stock.ensureDefaults(Lang.NL) { shopping.shopIdFor(it) }
    }

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun state(name: String) = stock.findItem(name)!!.state

    @Test
    fun `defaults give every room its everyday items, medicine from the pharmacy`() = runTest {
        assertEquals(
            listOf("Koelkast", "Vers", "Droge voeding", "Snacks", "Diepvries", "Drank", "Badkamer", "Wasplaats", "Medicijnkast"),
            stock.allSpots().map { it.name },
        )
        val pharmacy = shopping.allShops().first { it.kind == ShopKind.PHARMACY }
        assertEquals(pharmacy.id, stock.findItem("Paracetamol")!!.shopId)
        // Seeding twice does nothing.
        stock.ensureDefaults(Lang.NL) { shopping.shopIdFor(it) }
        assertEquals(9, stock.allSpots().size)
    }

    @Test
    fun `out puts it on the list once, bought brings it back in stock`() = runTest {
        val out = tools.registry.run(tools.setStockState, SetStockStateArgs(names = listOf("wc-papier"), state = StockState.OUT), nl)
        assertEquals("WC-papier is op · op de lijst", out.summary)
        tools.registry.run(tools.setStockState, SetStockStateArgs(names = listOf("wc-papier"), state = StockState.LOW), nl)
        val open = shopping.openItems()
        assertEquals(listOf("WC-papier"), open.map { it.name })
        assertEquals(stock.findItem("WC-papier")!!.id, open.single().stockItemId)

        tools.registry.run(tools.markBought, MarkBoughtArgs(names = listOf("wc-papier")), nl)
        assertEquals(StockState.IN_STOCK, state("WC-papier"))
    }

    @Test
    fun `medicine goes to the pharmacy list, back in stock takes it off`() = runTest {
        tools.registry.run(tools.setStockState, SetStockStateArgs(names = listOf("paracetamol"), state = StockState.OUT), nl)
        val pharmacy = shopping.allShops().first { it.kind == ShopKind.PHARMACY }
        assertEquals(pharmacy.id, shopping.openItems().single().shopId)
        tools.registry.run(tools.setStockState, SetStockStateArgs(names = listOf("paracetamol"), state = StockState.IN_STOCK), nl)
        assertTrue(shopping.openItems().isEmpty())
    }

    @Test
    fun `adding a stock item to the list by name links it`() = runTest {
        tools.registry.run(tools.addShoppingItem, AddShoppingItemArgs("koffie"), nl)
        assertEquals(StockState.LOW, state("Koffie"))
        tools.registry.run(tools.removeShoppingItem, MarkBoughtArgs(names = listOf("koffie")), nl, confirmed = true)
        assertEquals(StockState.IN_STOCK, state("Koffie"))
    }

    @Test
    fun `new items and places, removing asks first`() = runTest {
        assertEquals("Kattenvoer staat in de voorraad · Huisdieren", tools.registry.run(tools.addStockItem, AddStockItemArgs("kattenvoer", "huisdieren"), nl).summary)
        val listed = tools.registry.run(tools.listStock, ListStockArgs(spot = "Huisdieren"), nl)
        assertEquals("Huisdieren: Kattenvoer", listed.summary)
        val call = ToolCall.of(RemoveStockItemTool.NAME, StockRefArgs.serializer(), StockRefArgs(names = listOf("kattenvoer")))
        assertTrue(tools.registry.execute(call, nl) is ToolOutcome.NeedsConfirmation)
        tools.registry.execute(call, nl, confirmed = true)
        assertEquals(null, stock.findItem("Kattenvoer"))
    }

    @Test
    fun `clearing the bought items asks first`() = runTest {
        tools.registry.run(tools.addShoppingItem, AddShoppingItemArgs("melk"), nl)
        tools.registry.run(tools.addShoppingItem, AddShoppingItemArgs("ijsjes"), nl)
        tools.registry.run(tools.markBought, MarkBoughtArgs(names = listOf("melk", "ijsjes")), nl)
        val call = ToolCall.of(ClearBoughtItemsTool.NAME, ClearBoughtArgs.serializer(), ClearBoughtArgs())
        assertTrue(tools.registry.execute(call, nl) is ToolOutcome.NeedsConfirmation)
        assertEquals("Lijst opgeruimd: 2 gekocht. Goed gedaan!", tools.registry.execute(call, nl, confirmed = true).summary)
        assertTrue(shopping.boughtItems().isEmpty())
    }

    @Test
    fun `items move to another category, and a category can go with its items after a yes`() = runTest {
        assertEquals("Chips staat nu bij Feest", tools.registry.run(tools.moveStockItem, MoveStockItemArgs(names = listOf("chips"), spot = "feest"), nl).summary)
        val feest = stock.allSpots().single { it.name == "Feest" }
        assertEquals(feest.id, stock.findItem("Chips")!!.spotId)

        val call = ToolCall.of(RemoveStorageSpotTool.NAME, RemoveStorageSpotArgs.serializer(), RemoveStorageSpotArgs("Feest"))
        assertEquals("Feest met alles erin uit de voorraad halen?", tools.registry.execute(call, nl).summary)
        tools.registry.execute(call, nl, confirmed = true)
        assertEquals(null, stock.findItem("Chips"))
        assertTrue(stock.allSpots().none { it.name == "Feest" })
    }
}
