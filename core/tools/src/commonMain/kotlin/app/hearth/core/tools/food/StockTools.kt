package app.hearth.core.tools.food

import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.model.ShopKind
import app.hearth.core.model.StockItem
import app.hearth.core.model.StockState
import app.hearth.core.model.StorageSpot
import app.hearth.core.model.normalizeName
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolResult
import kotlinx.serialization.Serializable

// Stock: what the household always wants in the house, per room or topic.
// The restock loop: LOW/OUT puts it on the shopping list; buying it brings it back IN_STOCK.

@Serializable
data class StockItemsResult(val items: List<StockItem>, override val summary: String) : ToolResult

/** Keeps the shopping list in step with a stock item's state. */
internal suspend fun syncShopping(item: StockItem, state: StockState, shopping: ShoppingRepository) {
    val linked = shopping.openForStock(listOf(item.id))
    if (state == StockState.IN_STOCK) {
        shopping.deleteItems(linked.map { it.id })
        return
    }
    if (linked.isNotEmpty()) return
    val sameName = shopping.findOpenItem(item.name)
    if (sameName != null) {
        shopping.link(sameName.id, item.id)
        return
    }
    val shopId = item.shopId ?: ShopResolver.defaultShop(shopping.allShops())?.id ?: return
    shopping.insertItem(item.name, shopId, quantity = null, note = null, stockItemId = item.id)
}

private suspend fun StockRepository.find(ids: List<String>, names: List<String>): List<StockItem> {
    val all = allItems()
    val byName = names.flatMap { n ->
        val key = normalizeName(n)
        all.filter { normalizeName(it.name) == key }.ifEmpty { all.filter { normalizeName(it.name).contains(key) } }
    }
    return (all.filter { it.id in ids } + byName).distinctBy { it.id }
}

private fun notFound(ctx: ToolContext): Nothing =
    throw ToolException(ctx.t("Dat staat niet in de voorraad.", "That's not in the stock."))

// ---------- list_stock ----------

@Serializable
data class ListStockArgs(
    @Describe("Only this room or topic, e.g. 'Keuken', 'Badkamer'. Leave out for everything.")
    val spot: String? = null,
    @Describe("Only items that are LOW or OUT.")
    val onlyNeeded: Boolean = false,
)

@Serializable
data class ListStockResult(val spots: List<StorageSpot>, val items: List<StockItem>, override val summary: String) : ToolResult

class ListStockTool(private val stock: StockRepository) : Tool<ListStockArgs, ListStockResult> {
    override val name = NAME
    override val description = "Show the stock per room or topic (kitchen, bathroom, ...), with what is low or out."
    override val argsSerializer = ListStockArgs.serializer()
    override val resultSerializer = ListStockResult.serializer()

    override suspend fun run(args: ListStockArgs, ctx: ToolContext): ListStockResult {
        val spots = stock.allSpots().filter { args.spot.isNullOrBlank() || normalizeName(it.name) == normalizeName(args.spot) }
        if (spots.isEmpty()) throw ToolException(ctx.t("Die plek ken ik niet: ${args.spot}", "I don't know that place: ${args.spot}"))
        val ids = spots.map { it.id }.toSet()
        val items = stock.allItems().filter { it.spotId in ids && (!args.onlyNeeded || it.state != StockState.IN_STOCK) }
        val summary = spots.mapNotNull { spot ->
            val here = items.filter { it.spotId == spot.id }.ifEmpty { return@mapNotNull null }
            "${spot.name}: " + here.joinToString(", ") { it.name + stateLabel(it.state, ctx) }
        }.joinToString("\n").ifEmpty { ctx.t("Alles is in huis.", "Everything is in the house.") }
        return ListStockResult(spots, items, summary)
    }

    companion object {
        const val NAME = "list_stock"
    }
}

private fun stateLabel(state: StockState, ctx: ToolContext) = when (state) {
    StockState.IN_STOCK -> ""
    StockState.LOW -> ctx.t(" (bijna op)", " (low)")
    StockState.OUT -> ctx.t(" (op)", " (out)")
}

// ---------- add_stock_item / add_storage_spot ----------

@Serializable
data class AddStockItemArgs(
    @Describe("What to keep in the house, e.g. 'Koffie'.")
    val name: String,
    @Describe("Room or topic, e.g. 'Keuken', 'Badkamer'. Created if new.")
    val spot: String,
    @Describe("Optional shop or type where it's bought, e.g. 'Apotheek'.")
    val shop: String? = null,
)

@Serializable
data class StockItemResult(val item: StockItem, val added: Boolean, override val summary: String) : ToolResult

class AddStockItemTool(private val stock: StockRepository, private val shopping: ShoppingRepository) : Tool<AddStockItemArgs, StockItemResult> {
    override val name = NAME
    override val description = "Add something the household always wants in the house to a room or topic of the stock."
    override val argsSerializer = AddStockItemArgs.serializer()
    override val resultSerializer = StockItemResult.serializer()

    override suspend fun run(args: AddStockItemArgs, ctx: ToolContext): StockItemResult {
        val name = args.name.trim().ifEmpty { throw ToolException(ctx.t("Wat wil je in voorraad houden?", "What do you want to keep in stock?")) }
        val spot = stock.spot(args.spot.trim().ifEmpty { ctx.t("Keuken", "Kitchen") })
        val shopId = args.shop?.takeIf { it.isNotBlank() }?.let { s ->
            val shops = shopping.allShops()
            (ShopResolver.resolve(s, shops) ?: shopping.addShop(s.trim(), ShopResolver.guessKind(s))).id
        }
        val (item, added) = stock.addItem(name, spot.id, shopId)
        val summary = if (added) ctx.t("${item.name} staat in de voorraad · ${spot.name}", "${item.name} is in the stock · ${spot.name}")
        else ctx.t("${item.name} staat al in de voorraad.", "${item.name} is already in the stock.")
        return StockItemResult(item, added, summary)
    }

    companion object {
        const val NAME = "add_stock_item"
    }
}

@Serializable
data class AddStorageSpotArgs(@Describe("A room or topic, e.g. 'Kelder', 'Baby', 'Huisdieren'.") val name: String)

@Serializable
data class StorageSpotResult(val spot: StorageSpot, override val summary: String) : ToolResult

class AddStorageSpotTool(private val stock: StockRepository) : Tool<AddStorageSpotArgs, StorageSpotResult> {
    override val name = NAME
    override val description = "Add a room or topic to group stock items (kelder, baby, huisdieren...)."
    override val argsSerializer = AddStorageSpotArgs.serializer()
    override val resultSerializer = StorageSpotResult.serializer()

    override suspend fun run(args: AddStorageSpotArgs, ctx: ToolContext): StorageSpotResult {
        val name = args.name.trim().ifEmpty { throw ToolException(ctx.t("Welke plek?", "Which place?")) }
        val spot = stock.spot(name)
        return StorageSpotResult(spot, ctx.t("Plek in de voorraad: ${spot.name}", "Place in the stock: ${spot.name}"))
    }

    companion object {
        const val NAME = "add_storage_spot"
    }
}

// ---------- set_stock_state ----------

@Serializable
data class SetStockStateArgs(
    @Describe("Ids of stock items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Names of stock items, when ids are not known.")
    val names: List<String> = emptyList(),
    @Describe("IN_STOCK, LOW (almost out) or OUT. LOW and OUT put it on the shopping list.")
    val state: StockState,
)

class SetStockStateTool(private val stock: StockRepository, private val shopping: ShoppingRepository) : Tool<SetStockStateArgs, StockItemsResult> {
    override val name = NAME
    override val description =
        "Say how much of a stock item is left. LOW or OUT puts it on the shopping list (once); IN_STOCK takes it off again."
    override val argsSerializer = SetStockStateArgs.serializer()
    override val resultSerializer = StockItemsResult.serializer()

    override suspend fun run(args: SetStockStateArgs, ctx: ToolContext): StockItemsResult = shopping.transaction {
        val items = stock.find(args.itemIds, args.names).ifEmpty { notFound(ctx) }
        stock.setState(items.map { it.id }, args.state)
        items.forEach { syncShopping(it, args.state, shopping) }
        val names = items.joinToString(", ") { it.name }
        val summary = when (args.state) {
            StockState.IN_STOCK -> ctx.t("$names is weer in huis.", "$names is back in the house.")
            StockState.LOW -> ctx.t("$names is bijna op · op de lijst", "$names is running low · on the list")
            StockState.OUT -> ctx.t("$names is op · op de lijst", "$names is out · on the list")
        }
        StockItemsResult(items.map { it.copy(state = args.state) }, summary)
    }

    companion object {
        const val NAME = "set_stock_state"
    }
}

// ---------- remove_stock_item (destructive) ----------

@Serializable
data class StockRefArgs(
    @Describe("Ids of stock items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Names of stock items, when ids are not known.")
    val names: List<String> = emptyList(),
)

class RemoveStockItemTool(private val stock: StockRepository) : Tool<StockRefArgs, StockItemsResult> {
    override val name = NAME
    override val description = "Stop keeping something in stock (it leaves the stock page; the shopping list is untouched)."
    override val destructive = true
    override val argsSerializer = StockRefArgs.serializer()
    override val resultSerializer = StockItemsResult.serializer()

    override fun confirmQuestion(args: StockRefArgs, ctx: ToolContext): String {
        val what = args.names.joinToString(", ").ifEmpty { ctx.t("dit", "this") }
        return ctx.t("$what uit de voorraad halen?", "Remove $what from the stock?")
    }

    override suspend fun run(args: StockRefArgs, ctx: ToolContext): StockItemsResult {
        val items = stock.find(args.itemIds, args.names).ifEmpty { notFound(ctx) }
        stock.delete(items.map { it.id })
        return StockItemsResult(items, ctx.t("Uit de voorraad: ${items.joinToString(", ") { it.name }}", "Removed from stock: ${items.joinToString(", ") { it.name }}"))
    }

    companion object {
        const val NAME = "remove_stock_item"
    }
}

/** For the default shop of seeded items. */
suspend fun ShoppingRepository.shopIdFor(kind: ShopKind): String? = allShops().firstOrNull { it.kind == kind }?.id

// ---------- move_stock_item ----------

@Serializable
data class MoveStockItemArgs(
    @Describe("Ids of stock items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Names of stock items, when ids are not known.")
    val names: List<String> = emptyList(),
    @Describe("The room or category it belongs in, e.g. 'Snacks', 'Droge voeding'. Created if new.")
    val spot: String,
)

class MoveStockItemTool(private val stock: StockRepository) : Tool<MoveStockItemArgs, StockItemsResult> {
    override val name = NAME
    override val description = "Move stock items to another room or category (e.g. chips to Snacks)."
    override val argsSerializer = MoveStockItemArgs.serializer()
    override val resultSerializer = StockItemsResult.serializer()

    override suspend fun run(args: MoveStockItemArgs, ctx: ToolContext): StockItemsResult {
        val items = stock.find(args.itemIds, args.names).ifEmpty { notFound(ctx) }
        val spot = stock.spot(args.spot.trim().ifEmpty { throw ToolException(ctx.t("Naar welke plek?", "To which place?")) })
        stock.move(items.map { it.id }, spot.id)
        val names = items.joinToString(", ") { it.name }
        return StockItemsResult(items.map { it.copy(spotId = spot.id) }, ctx.t("$names staat nu bij ${spot.name}", "$names is now under ${spot.name}"))
    }

    companion object {
        const val NAME = "move_stock_item"
    }
}

// ---------- remove_storage_spot (destructive) ----------

@Serializable
data class RemoveStorageSpotArgs(@Describe("The room or category to remove, with its items.") val spot: String)

class RemoveStorageSpotTool(private val stock: StockRepository) : Tool<RemoveStorageSpotArgs, StorageSpotResult> {
    override val name = NAME
    override val description = "Remove a room or category from the stock, with the items in it."
    override val destructive = true
    override val argsSerializer = RemoveStorageSpotArgs.serializer()
    override val resultSerializer = StorageSpotResult.serializer()

    override fun confirmQuestion(args: RemoveStorageSpotArgs, ctx: ToolContext) =
        ctx.t("${args.spot} met alles erin uit de voorraad halen?", "Remove ${args.spot} and everything in it from the stock?")

    override suspend fun run(args: RemoveStorageSpotArgs, ctx: ToolContext): StorageSpotResult {
        val spot = stock.allSpots().firstOrNull { normalizeName(it.name) == normalizeName(args.spot) || it.id == args.spot }
            ?: throw ToolException(ctx.t("Die plek ken ik niet: ${args.spot}", "I don't know that place: ${args.spot}"))
        stock.deleteSpot(spot.id)
        return StorageSpotResult(spot, ctx.t("${spot.name} is uit de voorraad.", "${spot.name} was removed from the stock."))
    }

    companion object {
        const val NAME = "remove_storage_spot"
    }
}
