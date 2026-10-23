package app.hearth.core.tools.food

import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.model.Lang
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Households from the first layout (one "Keuken") get the categories once; their own items stay. */
class StockLayoutTest {
    private val db = inMemoryHearthDatabase()
    private val shopping = ShoppingRepository(db)
    private val stock = StockRepository(db)

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `the old kitchen splits into categories, keeping what the family added`() = runTest {
        shopping.ensureDefaultShops(Lang.NL)
        val keuken = stock.spot("Keuken")
        stock.addItem("Melk", keuken.id)
        stock.addItem("Pasta", keuken.id)
        stock.addItem("Kattenvoer", keuken.id)

        stock.ensureDefaults(Lang.NL) { shopping.shopIdFor(it) }

        val spots = stock.allSpots().associateBy { it.id }
        assertEquals("Koelkast", spots[stock.findItem("Melk")!!.spotId]!!.name)
        assertEquals("Droge voeding", spots[stock.findItem("Pasta")!!.spotId]!!.name)
        assertEquals("Keuken", spots[stock.findItem("Kattenvoer")!!.spotId]!!.name)
        assertTrue(stock.findItem("Chips") != null)

        // Only once: a category the family removed doesn't come back.
        stock.deleteSpot(stock.allSpots().single { it.name == "Snacks" }.id)
        stock.ensureDefaults(Lang.NL) { shopping.shopIdFor(it) }
        assertTrue(stock.allSpots().none { it.name == "Snacks" })
    }

    @Test
    fun `an emptied old kitchen goes`() = runTest {
        val keuken = stock.spot("Keuken")
        stock.addItem("Melk", keuken.id)
        stock.ensureDefaults(Lang.NL) { null }
        assertTrue(stock.allSpots().none { it.name == "Keuken" })
    }
}
