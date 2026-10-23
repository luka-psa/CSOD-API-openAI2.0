package app.hearth.core.data.food

import androidx.room3.withWriteTransaction
import app.hearth.core.data.HearthDatabase
import app.hearth.core.model.Lang
import app.hearth.core.model.Shop
import app.hearth.core.model.ShopKind
import app.hearth.core.model.ShoppingItem
import app.hearth.core.model.displayName
import app.hearth.core.model.newId
import app.hearth.core.model.normalizeName
import app.hearth.core.model.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Storage for shops and the shopping list. Screens may observe the Flows; every write
 * must come from a tool in core:tools, never from UI code directly.
 */
class ShoppingRepository(
    private val db: HearthDatabase,
    private val clock: () -> Long = ::nowMillis,
) {
    private val shopDao = db.shopDao()
    private val itemDao = db.shoppingItemDao()

    val shops: Flow<List<Shop>> = shopDao.observeAll().map { list -> list.map { it.toModel() } }

    fun visibleItems(boughtSince: Long): Flow<List<ShoppingItem>> =
        itemDao.observeVisible(boughtSince).map { list -> list.map { it.toModel() } }

    suspend fun allShops(): List<Shop> = shopDao.all().map { it.toModel() }

    suspend fun addShop(name: String, kind: ShopKind): Shop = transaction { insertShop(name, kind) }

    private suspend fun insertShop(name: String, kind: ShopKind): Shop {
        val now = clock()
        val entity = ShopEntity(
            id = newId(),
            name = displayName(name),
            normalizedName = normalizeName(name),
            kind = kind,
            sortOrder = shopDao.maxSortOrder() + 1,
            createdAt = now,
            updatedAt = now,
        )
        shopDao.insert(entity)
        return entity.toModel()
    }

    /** Seeds the usual shops the first time the app runs. Safe to call on every start. */
    suspend fun ensureDefaultShops(lang: Lang) = transaction {
        if (shopDao.all().isEmpty()) {
            DEFAULT_SHOPS.forEach { (kind, nl, en) -> insertShop(if (lang == Lang.NL) nl else en, kind) }
        }
    }

    suspend fun openItems(): List<ShoppingItem> = itemDao.open().map { it.toModel() }

    suspend fun findOpenItem(name: String): ShoppingItem? = itemDao.findOpenByName(normalizeName(name))?.toModel()

    suspend fun itemsByIds(ids: List<String>): List<ShoppingItem> = itemDao.byIds(ids).map { it.toModel() }

    suspend fun openItemsByNames(names: List<String>): List<ShoppingItem> =
        itemDao.openByNames(names.map(::normalizeName)).map { it.toModel() }

    suspend fun boughtItemsByNames(names: List<String>): List<ShoppingItem> =
        itemDao.boughtByNames(names.map(::normalizeName)).map { it.toModel() }

    suspend fun insertItem(
        name: String,
        shopId: String,
        quantity: String?,
        note: String?,
        stockItemId: String? = null,
        addedById: String? = null,
    ): ShoppingItem {
        val now = clock()
        val entity = ShoppingItemEntity(
            id = newId(),
            name = displayName(name),
            normalizedName = normalizeName(name),
            quantity = quantity,
            note = note,
            shopId = shopId,
            stockItemId = stockItemId,
            bought = false,
            boughtAt = null,
            addedById = addedById,
            createdAt = now,
            updatedAt = now,
        )
        itemDao.insert(entity)
        return entity.toModel()
    }

    suspend fun updateItem(id: String, quantity: String?, note: String?, shopId: String) {
        val current = itemDao.byIds(listOf(id)).firstOrNull() ?: return
        itemDao.update(current.copy(quantity = quantity, note = note, shopId = shopId, updatedAt = clock()))
    }

    suspend fun setBought(ids: List<String>, bought: Boolean) {
        if (ids.isEmpty()) return
        val now = clock()
        itemDao.setBought(ids, bought, if (bought) now else null, now)
    }

    suspend fun openForStock(stockIds: List<String>): List<ShoppingItem> =
        if (stockIds.isEmpty()) emptyList() else itemDao.openByStockIds(stockIds).map { it.toModel() }

    suspend fun boughtItems(): List<ShoppingItem> = itemDao.bought().map { it.toModel() }

    /** Ties a shopping item to the stock item it restocks. */
    suspend fun link(itemId: String, stockId: String) = itemDao.link(itemId, stockId, clock())

    suspend fun deleteItems(ids: List<String>) {
        if (ids.isNotEmpty()) itemDao.delete(ids)
    }

    suspend fun <R> transaction(block: suspend () -> R): R = db.withWriteTransaction { block() }

    private companion object {
        val DEFAULT_SHOPS = listOf(
            Triple(ShopKind.SUPERMARKET, "Supermarkt", "Supermarket"),
            Triple(ShopKind.BAKERY, "Bakker", "Bakery"),
            Triple(ShopKind.PHARMACY, "Apotheek", "Pharmacy"),
            Triple(ShopKind.DIY, "Gamma", "Gamma"),
            Triple(ShopKind.FURNITURE, "IKEA", "IKEA"),
        )
    }
}
