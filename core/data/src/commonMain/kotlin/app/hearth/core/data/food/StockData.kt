package app.hearth.core.data.food

import kotlinx.serialization.Serializable
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.withWriteTransaction
import app.hearth.core.data.HearthDatabase
import app.hearth.core.model.Lang
import app.hearth.core.model.ShopKind
import app.hearth.core.model.StockItem
import app.hearth.core.model.StockState
import app.hearth.core.model.StorageSpot
import app.hearth.core.model.displayName
import app.hearth.core.model.newId
import app.hearth.core.model.normalizeName
import app.hearth.core.model.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "storage_spot", indices = [Index("normalizedName", unique = true)])
@Serializable
data class StorageSpotEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toModel() = StorageSpot(id, name, sortOrder)
}

@Entity(
    tableName = "stock_item",
    foreignKeys = [ForeignKey(entity = StorageSpotEntity::class, parentColumns = ["id"], childColumns = ["spotId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("spotId"), Index("normalizedName")],
)
@Serializable
data class StockItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val spotId: String,
    val state: StockState,
    val shopId: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toModel() = StockItem(id, name, spotId, state, shopId)
}

@Dao
interface StockDao {
    @Query("SELECT * FROM storage_spot ORDER BY sortOrder, name")
    fun observeSpots(): Flow<List<StorageSpotEntity>>

    @Query("SELECT * FROM storage_spot ORDER BY sortOrder, name")
    suspend fun spots(): List<StorageSpotEntity>

    @Query("SELECT * FROM stock_item ORDER BY name")
    fun observeItems(): Flow<List<StockItemEntity>>

    @Query("SELECT * FROM stock_item ORDER BY name")
    suspend fun items(): List<StockItemEntity>

    @Query("SELECT * FROM stock_item WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByName(normalizedName: String): StockItemEntity?

    @Insert
    suspend fun insertSpot(spot: StorageSpotEntity)

    @Insert
    suspend fun insertItem(item: StockItemEntity)

    @Query("UPDATE stock_item SET state = :state, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setState(ids: List<String>, state: StockState, now: Long)

    @Query("DELETE FROM stock_item WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("UPDATE stock_item SET spotId = :spotId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun move(ids: List<String>, spotId: String, now: Long)

    @Query("DELETE FROM storage_spot WHERE id = :id")
    suspend fun deleteSpot(id: String)
}

/** Storage for stock: spots (rooms or topics) and the items kept there. Writes come from tools only. */
class StockRepository(
    private val db: HearthDatabase,
    private val clock: () -> Long = ::nowMillis,
) {
    private val dao = db.stockDao()

    val spots: Flow<List<StorageSpot>> = dao.observeSpots().map { list -> list.map { it.toModel() } }
    val items: Flow<List<StockItem>> = dao.observeItems().map { list -> list.map { it.toModel() } }

    suspend fun allSpots(): List<StorageSpot> = dao.spots().map { it.toModel() }
    suspend fun allItems(): List<StockItem> = dao.items().map { it.toModel() }
    suspend fun findItem(name: String): StockItem? = dao.findByName(normalizeName(name))?.toModel()

    /** Finds a spot by name, or adds it. */
    suspend fun spot(name: String): StorageSpot = db.withWriteTransaction {
        val key = normalizeName(name)
        val all = dao.spots()
        all.firstOrNull { it.normalizedName == key }?.let { return@withWriteTransaction it.toModel() }
        val now = clock()
        val entity = StorageSpotEntity(newId(), displayName(name), key, (all.maxOfOrNull { it.sortOrder } ?: -1) + 1, now, now)
        dao.insertSpot(entity)
        entity.toModel()
    }

    /** Adds [name] to [spotId], or returns the stock item with that name. */
    suspend fun addItem(name: String, spotId: String, shopId: String? = null): Pair<StockItem, Boolean> = db.withWriteTransaction {
        dao.findByName(normalizeName(name))?.let { return@withWriteTransaction it.toModel() to false }
        val now = clock()
        val entity = StockItemEntity(newId(), displayName(name), normalizeName(name), spotId, StockState.IN_STOCK, shopId, now, now)
        dao.insertItem(entity)
        entity.toModel() to true
    }

    suspend fun setState(ids: List<String>, state: StockState) {
        if (ids.isNotEmpty()) dao.setState(ids, state, clock())
    }

    suspend fun delete(ids: List<String>) {
        if (ids.isNotEmpty()) dao.delete(ids)
    }

    /** Moves items to another room or category. */
    suspend fun move(ids: List<String>, spotId: String) {
        if (ids.isNotEmpty()) dao.move(ids, spotId, clock())
    }

    /** Removes a room or category and the items in it. */
    suspend fun deleteSpot(id: String) = dao.deleteSpot(id)

    /**
     * Rooms and categories with everyday items, so the stock page is useful from day one. Households
     * that started with the first layout (one "Keuken") get the new categories once; their items move along.
     */
    suspend fun ensureDefaults(lang: Lang, shopIdFor: suspend (ShopKind) -> String?) {
        val settings = db.settingDao()
        if (settings.all().any { it.key == LAYOUT_KEY && it.value == LAYOUT }) return
        val defaults = if (lang == Lang.NL) DEFAULTS_NL else DEFAULTS_EN
        val shops = defaults.map { it.second }.distinct().associateWith { if (it == ShopKind.SUPERMARKET) null else shopIdFor(it) }
        // One transaction: in the browser every transaction is a round trip to the SQLite worker.
        db.withWriteTransaction {
            val now = clock()
            val spots = dao.spots().associateBy { it.normalizedName }.toMutableMap()
            val items = dao.items().associateBy { it.normalizedName }
            val firstRun = spots.isEmpty()
            defaults.forEachIndexed { order, (spotName, kind, names) ->
                val spot = spots[normalizeName(spotName)] ?: StorageSpotEntity(newId(), spotName, normalizeName(spotName), order, now, now).also {
                    dao.insertSpot(it)
                    spots[it.normalizedName] = it
                }
                names.forEach { name ->
                    val existing = items[normalizeName(name)]
                    when {
                        existing == null -> dao.insertItem(StockItemEntity(newId(), name, normalizeName(name), spot.id, StockState.IN_STOCK, shops[kind], now, now))
                        // From the first layout's catch-all kitchen to its own category.
                        !firstRun && existing.spotId in OLD_KITCHENS.mapNotNull { spots[it]?.id } -> dao.move(listOf(existing.id), spot.id, now)
                    }
                }
            }
            // An old "Keuken" that is now empty goes.
            OLD_KITCHENS.mapNotNull { spots[it] }.forEach { kitchen ->
                if (dao.items().none { it.spotId == kitchen.id }) dao.deleteSpot(kitchen.id)
            }
            settings.put(app.hearth.core.data.settings.SettingEntity(LAYOUT_KEY, LAYOUT, now))
        }
    }

    private companion object {
        const val LAYOUT_KEY = "stock.layout"
        const val LAYOUT = "2"
        val OLD_KITCHENS = listOf("keuken", "kitchen")

        val DEFAULTS_NL = listOf(
            Triple("Koelkast", ShopKind.SUPERMARKET, listOf("Melk", "Boter", "Kaas", "Eieren", "Yoghurt", "Beleg")),
            Triple("Vers", ShopKind.SUPERMARKET, listOf("Brood", "Fruit", "Groenten")),
            Triple("Droge voeding", ShopKind.SUPERMARKET, listOf("Pasta", "Rijst", "Bloem", "Suiker", "Koffie", "Thee", "Tomatensaus", "Olijfolie", "Ontbijtgranen")),
            Triple("Snacks", ShopKind.SUPERMARKET, listOf("Chips", "Koekjes", "Chocolade", "Noten", "Snoep")),
            Triple("Diepvries", ShopKind.SUPERMARKET, listOf("Diepvriesgroenten", "Frietjes", "IJs")),
            Triple("Drank", ShopKind.SUPERMARKET, listOf("Water", "Frisdrank", "Fruitsap")),
            Triple("Badkamer", ShopKind.SUPERMARKET, listOf("WC-papier", "Tandpasta", "Shampoo", "Douchegel", "Handzeep", "Deodorant")),
            Triple("Wasplaats", ShopKind.SUPERMARKET, listOf("Waspoeder", "Wasverzachter", "Afwasmiddel", "Vaatwastabletten", "Vuilniszakken", "Keukenrol", "Allesreiniger")),
            Triple("Medicijnkast", ShopKind.PHARMACY, listOf("Paracetamol", "Ibuprofen", "Pleisters", "Ontsmettingsmiddel")),
        )
        val DEFAULTS_EN = listOf(
            Triple("Fridge", ShopKind.SUPERMARKET, listOf("Milk", "Butter", "Cheese", "Eggs", "Yoghurt", "Sandwich fillings")),
            Triple("Fresh", ShopKind.SUPERMARKET, listOf("Bread", "Fruit", "Vegetables")),
            Triple("Dry food", ShopKind.SUPERMARKET, listOf("Pasta", "Rice", "Flour", "Sugar", "Coffee", "Tea", "Tomato sauce", "Olive oil", "Cereal")),
            Triple("Snacks", ShopKind.SUPERMARKET, listOf("Crisps", "Biscuits", "Chocolate", "Nuts", "Sweets")),
            Triple("Freezer", ShopKind.SUPERMARKET, listOf("Frozen vegetables", "Fries", "Ice cream")),
            Triple("Drinks", ShopKind.SUPERMARKET, listOf("Water", "Soft drinks", "Juice")),
            Triple("Bathroom", ShopKind.SUPERMARKET, listOf("Toilet paper", "Toothpaste", "Shampoo", "Shower gel", "Hand soap", "Deodorant")),
            Triple("Utility room", ShopKind.SUPERMARKET, listOf("Laundry detergent", "Fabric softener", "Dish soap", "Dishwasher tablets", "Bin bags", "Kitchen roll", "Cleaner")),
            Triple("Medicine cabinet", ShopKind.PHARMACY, listOf("Paracetamol", "Ibuprofen", "Plasters", "Antiseptic")),
        )
    }
}
