package app.hearth.core.tools.food

import app.hearth.core.data.HearthDatabase
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.model.Lang
import app.hearth.core.model.ShopKind
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShoppingToolsTest {
    private lateinit var db: HearthDatabase
    private lateinit var repo: ShoppingRepository
    private lateinit var tools: HearthTools
    private val nl = ToolContext(ToolSource.CHAT, Lang.NL)
    private val en = ToolContext(ToolSource.CHAT, Lang.EN)

    @BeforeTest
    fun setUp() = runTest {
        db = inMemoryHearthDatabase()
        repo = ShoppingRepository(db)
        repo.ensureDefaultShops(Lang.NL)
        tools = HearthTools(repo, BacklogRepository(db), app.hearth.core.data.board.BoardRepository(db), app.hearth.core.data.food.StockRepository(db), app.hearth.core.data.week.WeekRepository(db), app.hearth.core.data.settings.SettingsRepository(db))
    }

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun add(name: String, quantity: String? = null, shop: String? = null, ctx: ToolContext = nl) =
        tools.registry.run(tools.addShoppingItem, AddShoppingItemArgs(name, quantity, shop), ctx)

    @Test
    fun `seeds default shops once`() = runTest {
        repo.ensureDefaultShops(Lang.NL)
        val shops = repo.allShops()
        assertEquals(listOf("Supermarkt", "Bakker", "Apotheek", "Gamma", "IKEA"), shops.map { it.name })
    }

    @Test
    fun `adds to the supermarket by default`() = runTest {
        val result = add("melk")
        assertEquals("Melk", result.item.name)
        assertEquals("Supermarkt", result.shopName)
        assertTrue(result.item.isOneOff)
        assertEquals("Melk staat op de lijst · Supermarkt", result.summary)
    }

    @Test
    fun `resolves shop words in both languages`() = runTest {
        assertEquals("Apotheek", add("medicijnen", shop = "de apotheek").shopName)
        assertEquals("Apotheek", add("plasters", shop = "pharmacy", ctx = en).shopName)
        assertEquals("Gamma", add("verf", shop = "Gamma").shopName)
    }

    @Test
    fun `unknown shop is created with a guessed kind`() = runTest {
        val result = add("koffie", shop = "Colruyt")
        assertEquals("Colruyt", result.shopName)
        assertEquals(ShopKind.SUPERMARKET, repo.allShops().first { it.name == "Colruyt" }.kind)
    }

    @Test
    fun `adding something already on the list does not duplicate`() = runTest {
        add("Melk")
        val again = add("  melk ", quantity = "2")
        assertTrue(again.alreadyOnList)
        assertEquals("2", again.item.quantity)
        assertEquals(1, repo.openItems().size)
        assertEquals("Melk (2) stond al op de lijst · Supermarkt", again.summary)
    }

    @Test
    fun `list groups by shop in shop order`() = runTest {
        add("paracetamol", shop = "apotheek")
        add("melk")
        add("brood", shop = "bakker")
        val result = tools.registry.run(tools.listShoppingItems, ListShoppingItemsArgs(), nl)
        assertEquals(listOf("Supermarkt", "Bakker", "Apotheek"), result.groups.map { it.shop.name })
        assertEquals("Supermarkt: Melk\nBakker: Brood\nApotheek: Paracetamol", result.summary)
    }

    @Test
    fun `list can filter one shop and says when empty`() = runTest {
        assertEquals("Nothing needed right now.", tools.registry.run(tools.listShoppingItems, ListShoppingItemsArgs(), en).summary)
        add("paracetamol", shop = "apotheek")
        add("melk")
        val pharmacy = tools.registry.run(tools.listShoppingItems, ListShoppingItemsArgs(shop = "pharmacy"), en)
        assertEquals(listOf("Paracetamol"), pharmacy.groups.single().items.map { it.name })
    }

    @Test
    fun `mark bought by name and by id, then undo`() = runTest {
        val milk = add("melk").item
        add("eieren")
        val bought = tools.registry.run(tools.markBought, MarkBoughtArgs(names = listOf("Eieren")), nl)
        assertEquals("Gekocht: Eieren", bought.summary)
        tools.registry.run(tools.markBought, MarkBoughtArgs(itemIds = listOf(milk.id)), nl)
        assertTrue(repo.openItems().isEmpty())

        val visible = repo.visibleItems(boughtSince = 0).first()
        assertTrue(visible.all { it.bought && it.boughtAt != null })

        val undo = tools.registry.run(tools.unmarkBought, MarkBoughtArgs(itemIds = listOf(milk.id)), nl)
        assertEquals("Melk staat terug op de lijst", undo.summary)
        assertEquals(listOf("Melk"), repo.openItems().map { it.name })
        assertNull(repo.openItems().single().boughtAt)
    }

    @Test
    fun `adding again after buying creates a fresh line`() = runTest {
        add("melk")
        tools.registry.run(tools.markBought, MarkBoughtArgs(names = listOf("melk")), nl)
        assertFalse(add("melk").alreadyOnList)
    }

    @Test
    fun `removing needs confirmation outside the UI and then deletes`() = runTest {
        add("melk")
        add("kaas")
        val call = ToolCall.of(RemoveShoppingItemTool.NAME, MarkBoughtArgs.serializer(), MarkBoughtArgs(names = listOf("melk")))

        val pending = tools.registry.execute(call, nl)
        assertEquals(ToolOutcome.NeedsConfirmation(RemoveShoppingItemTool.NAME, "melk van de lijst verwijderen?", call), pending)
        assertEquals(2, repo.openItems().size)

        val done = tools.registry.execute(call, nl, confirmed = true)
        assertEquals("Verwijderd: Melk", done.summary)
        assertEquals(listOf("Kaas"), repo.openItems().map { it.name })
    }

    @Test
    fun `registry reports friendly failures instead of throwing`() = runTest {
        val missing = tools.registry.execute(
            ToolCall.of(MarkBoughtTool.NAME, MarkBoughtArgs.serializer(), MarkBoughtArgs(names = listOf("kaas"))),
            nl,
        )
        assertEquals(ToolOutcome.Failed(MarkBoughtTool.NAME, "Ik vind kaas niet op de lijst."), missing)

        val unknown = tools.registry.execute(ToolCall("fly_to_moon", JsonObject(emptyMap())), en)
        assertTrue(unknown is ToolOutcome.Failed)

        val badArgs = tools.registry.execute(ToolCall(AddShoppingItemTool.NAME, JsonObject(emptyMap())), en)
        assertEquals(ToolOutcome.Failed(AddShoppingItemTool.NAME, "I didn't quite get that."), badArgs)

        val blank = tools.registry.execute(
            ToolCall.of(AddShoppingItemTool.NAME, AddShoppingItemArgs.serializer(), AddShoppingItemArgs(" ")),
            en,
        )
        assertEquals(ToolOutcome.Failed(AddShoppingItemTool.NAME, "What should go on the list?"), blank)
    }
}
