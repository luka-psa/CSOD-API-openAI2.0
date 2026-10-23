package app.hearth.core.tools.food

import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.model.StockState
import app.hearth.core.model.Lang
import app.hearth.core.model.Shop
import app.hearth.core.model.ShopKind
import app.hearth.core.model.ShoppingItem
import app.hearth.core.model.displayName
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolResult
import kotlinx.serialization.Serializable

// ---------- add_shopping_item ----------

@Serializable
data class AddShoppingItemArgs(
    @Describe("What to buy, e.g. 'milk', 'melk', 'paracetamol'.")
    val name: String,
    @Describe("Optional amount, e.g. '2' or '1 kg'.")
    val quantity: String? = null,
    @Describe("Shop name or type, e.g. 'Apotheek', 'pharmacy', 'Gamma'. Leave out for the supermarket.")
    val shop: String? = null,
    @Describe("Optional short note.")
    val note: String? = null,
)

@Serializable
data class AddShoppingItemResult(
    val item: ShoppingItem,
    val shopName: String,
    val alreadyOnList: Boolean,
    override val summary: String,
) : ToolResult

class AddShoppingItemTool(private val repo: ShoppingRepository, private val stock: StockRepository? = null) : Tool<AddShoppingItemArgs, AddShoppingItemResult> {
    override val name = NAME
    override val description =
        "Put something on the shared shopping list. Matches an item already on the list instead of adding a " +
            "duplicate. Use 'shop' for one-off buys elsewhere, e.g. medicine from the pharmacy."
    override val argsSerializer = AddShoppingItemArgs.serializer()
    override val resultSerializer = AddShoppingItemResult.serializer()

    override suspend fun run(args: AddShoppingItemArgs, ctx: ToolContext): AddShoppingItemResult {
        val itemName = args.name.trim()
        if (itemName.isEmpty()) throw ToolException(ctx.t("Wat moet er op de lijst?", "What should go on the list?"))
        val quantity = args.quantity?.trim()?.ifEmpty { null }
        val note = args.note?.trim()?.ifEmpty { null }

        return repo.transaction {
            val shop = resolveShop(args.shop, ctx.lang)
            val existing = repo.findOpenItem(itemName)
            if (existing != null) {
                val newShopId = if (args.shop != null) shop.id else existing.shopId
                val updated = existing.copy(
                    quantity = quantity ?: existing.quantity,
                    note = note ?: existing.note,
                    shopId = newShopId,
                )
                if (updated != existing) repo.updateItem(existing.id, updated.quantity, updated.note, newShopId)
                val shopName = repo.allShops().first { it.id == newShopId }.name
                AddShoppingItemResult(
                    item = updated,
                    shopName = shopName,
                    alreadyOnList = true,
                    summary = ctx.t(
                        "${label(updated)} stond al op de lijst · $shopName",
                        "${label(updated)} was already on the list · $shopName",
                    ),
                )
            } else {
                // Something we keep in stock: link it, so buying it brings the stock back.
                val stockItem = stock?.findItem(itemName)
                stockItem?.let { stock?.setState(listOf(it.id), StockState.LOW) }
                val item = repo.insertItem(itemName, shop.id, quantity, note, stockItemId = stockItem?.id, addedById = ctx.actorMemberId)
                AddShoppingItemResult(
                    item = item,
                    shopName = shop.name,
                    alreadyOnList = false,
                    summary = ctx.t("${label(item)} staat op de lijst · ${shop.name}", "Added ${label(item)} · ${shop.name}"),
                )
            }
        }
    }

    private suspend fun resolveShop(spoken: String?, lang: Lang): Shop {
        val shops = repo.allShops()
        if (spoken.isNullOrBlank()) {
            return ShopResolver.defaultShop(shops)
                ?: repo.addShop(if (lang == Lang.NL) "Supermarkt" else "Supermarket", ShopKind.SUPERMARKET)
        }
        return ShopResolver.resolve(spoken, shops)
            ?: repo.addShop(displayName(ShopResolver.clean(spoken)), ShopResolver.guessKind(spoken))
    }

    companion object {
        const val NAME = "add_shopping_item"
    }
}

// ---------- list_shopping_items ----------

@Serializable
data class ListShoppingItemsArgs(
    @Describe("Only this shop (name or type). Leave out for the whole list.")
    val shop: String? = null,
)

@Serializable
data class ShopGroup(val shop: Shop, val items: List<ShoppingItem>)

@Serializable
data class ListShoppingItemsResult(
    val groups: List<ShopGroup>,
    override val summary: String,
) : ToolResult

class ListShoppingItemsTool(private val repo: ShoppingRepository) : Tool<ListShoppingItemsArgs, ListShoppingItemsResult> {
    override val name = NAME
    override val description = "Show what's still on the shopping list, grouped by shop."
    override val argsSerializer = ListShoppingItemsArgs.serializer()
    override val resultSerializer = ListShoppingItemsResult.serializer()

    override suspend fun run(args: ListShoppingItemsArgs, ctx: ToolContext): ListShoppingItemsResult {
        val shops = repo.allShops()
        val only = args.shop?.takeIf { it.isNotBlank() }?.let {
            ShopResolver.resolve(it, shops) ?: throw ToolException(ctx.t("Die winkel ken ik nog niet: $it", "I don't know that shop yet: $it"))
        }
        val items = repo.openItems().groupBy { it.shopId }
        val groups = shops
            .filter { only == null || it.id == only.id }
            .mapNotNull { shop -> items[shop.id]?.let { ShopGroup(shop, it) } }

        val summary = if (groups.isEmpty()) {
            ctx.t("Niets nodig op dit moment.", "Nothing needed right now.")
        } else {
            groups.joinToString("\n") { g -> "${g.shop.name}: " + g.items.joinToString(", ") { label(it) } }
        }
        return ListShoppingItemsResult(groups, summary)
    }

    companion object {
        const val NAME = "list_shopping_items"
    }
}

// ---------- mark_bought / unmark_bought ----------

@Serializable
data class MarkBoughtArgs(
    @Describe("Ids of shopping items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Names of shopping items, when ids are not known.")
    val names: List<String> = emptyList(),
)

@Serializable
data class MarkBoughtResult(
    val items: List<ShoppingItem>,
    override val summary: String,
) : ToolResult

class MarkBoughtTool(private val repo: ShoppingRepository, private val stock: StockRepository? = null) : Tool<MarkBoughtArgs, MarkBoughtResult> {
    override val name = NAME
    override val description = "Tick shopping items as bought. They leave the list; stock items are back in the house."
    override val argsSerializer = MarkBoughtArgs.serializer()
    override val resultSerializer = MarkBoughtResult.serializer()

    override suspend fun run(args: MarkBoughtArgs, ctx: ToolContext): MarkBoughtResult = repo.transaction {
        val items = (repo.itemsByIds(args.itemIds).filterNot { it.bought } + repo.openItemsByNames(args.names))
            .distinctBy { it.id }
        if (items.isEmpty()) throw notFound(args, ctx)
        repo.setBought(items.map { it.id }, bought = true)
        stock?.setState(items.mapNotNull { it.stockItemId }, StockState.IN_STOCK)
        val names = items.joinToString(", ") { label(it) }
        MarkBoughtResult(items.map { it.copy(bought = true) }, ctx.t("Gekocht: $names", "Bought: $names"))
    }

    companion object {
        const val NAME = "mark_bought"
    }
}

class UnmarkBoughtTool(private val repo: ShoppingRepository) : Tool<MarkBoughtArgs, MarkBoughtResult> {
    override val name = NAME
    override val description = "Undo 'bought': put shopping items back on the list."
    override val argsSerializer = MarkBoughtArgs.serializer()
    override val resultSerializer = MarkBoughtResult.serializer()

    override suspend fun run(args: MarkBoughtArgs, ctx: ToolContext): MarkBoughtResult = repo.transaction {
        val byName = repo.boughtItemsByNames(args.names).distinctBy { it.name.lowercase() }
        val items = (repo.itemsByIds(args.itemIds).filter { it.bought } + byName).distinctBy { it.id }
        if (items.isEmpty()) throw notFound(args, ctx)
        repo.setBought(items.map { it.id }, bought = false)
        val names = items.joinToString(", ") { label(it) }
        MarkBoughtResult(
            items.map { it.copy(bought = false, boughtAt = null) },
            ctx.t("$names staat terug op de lijst", "$names is back on the list"),
        )
    }

    companion object {
        const val NAME = "unmark_bought"
    }
}

// ---------- remove_shopping_item (destructive) ----------

class RemoveShoppingItemTool(private val repo: ShoppingRepository, private val stock: StockRepository? = null) : Tool<MarkBoughtArgs, MarkBoughtResult> {
    override val name = NAME
    override val description =
        "Delete shopping items that were added by mistake. To tick something off as bought, use mark_bought instead."
    override val destructive = true
    override val argsSerializer = MarkBoughtArgs.serializer()
    override val resultSerializer = MarkBoughtResult.serializer()

    override fun confirmQuestion(args: MarkBoughtArgs, ctx: ToolContext): String {
        val what = args.names.joinToString(", ").ifEmpty { ctx.t("dit", "this") }
        return ctx.t("$what van de lijst verwijderen?", "Remove $what from the list?")
    }

    override suspend fun run(args: MarkBoughtArgs, ctx: ToolContext): MarkBoughtResult = repo.transaction {
        val items = (repo.itemsByIds(args.itemIds) + repo.openItemsByNames(args.names)).distinctBy { it.id }
        if (items.isEmpty()) throw notFound(args, ctx)
        repo.deleteItems(items.map { it.id })
        // Not needed after all: the stock item counts as in the house again.
        stock?.setState(items.filter { !it.bought }.mapNotNull { it.stockItemId }, StockState.IN_STOCK)
        val names = items.joinToString(", ") { label(it) }
        MarkBoughtResult(items, ctx.t("Verwijderd: $names", "Removed: $names"))
    }

    companion object {
        const val NAME = "remove_shopping_item"
    }
}

// ---------- clear_bought_items (destructive) ----------

@Serializable
class ClearBoughtArgs

class ClearBoughtItemsTool(private val repo: ShoppingRepository) : Tool<ClearBoughtArgs, MarkBoughtResult> {
    override val name = NAME
    override val description = "Clear everything that was ticked as bought from the shopping list."
    override val destructive = true
    override val argsSerializer = ClearBoughtArgs.serializer()
    override val resultSerializer = MarkBoughtResult.serializer()

    override fun confirmQuestion(args: ClearBoughtArgs, ctx: ToolContext) =
        ctx.t("Alles wat gekocht is van de lijst halen?", "Clear everything bought from the list?")

    override suspend fun run(args: ClearBoughtArgs, ctx: ToolContext): MarkBoughtResult = repo.transaction {
        val bought = repo.boughtItems()
        repo.deleteItems(bought.map { it.id })
        val summary = if (bought.isEmpty()) ctx.t("Er was niets gekocht om op te ruimen.", "Nothing bought to clear.")
        else ctx.t("Lijst opgeruimd: ${bought.size} gekocht. Goed gedaan!", "List cleared: ${bought.size} bought. Well done!")
        MarkBoughtResult(bought, summary)
    }

    companion object {
        const val NAME = "clear_bought_items"
    }
}

private fun notFound(args: MarkBoughtArgs, ctx: ToolContext): ToolException {
    val what = args.names.joinToString(", ").ifEmpty { ctx.t("dat", "that") }
    return ToolException(ctx.t("Ik vind $what niet op de lijst.", "I can't find $what on the list."))
}

/** "Melk" or "Melk (2)". */
internal fun label(item: ShoppingItem): String =
    if (item.quantity != null) "${item.name} (${item.quantity})" else item.name
