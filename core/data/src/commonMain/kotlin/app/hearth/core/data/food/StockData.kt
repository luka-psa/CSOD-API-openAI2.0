package app.hearth.core.data.food

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

    /** A first set of spots and everyday items, so the stock page is useful from day one. */
    suspend fun ensureDefaults(lang: Lang, shopIdFor: suspend (ShopKind) -> String?) {
        if (dao.spots().isNotEmpty()) return
        val defaults = if (lang == Lang.NL) DEFAULTS_NL else DEFAULTS_EN
        val shops = defaults.map { it.second }.distinct().associateWith { if (it == ShopKind.SUPERMARKET) null else shopIdFor(it) }
        // One transaction: in the browser every transaction is a round trip to the SQLite worker.
        db.withWriteTransaction {
            if (dao.spots().isNotEmpty()) return@withWriteTransaction
            val now = clock()
            defaults.forEachIndexed { order, (spotName, kind, names) ->
                val spot = StorageSpotEntity(newId(), spotName, normalizeName(spotName), order, now, now)
                dao.insertSpot(spot)
                names.forEach { dao.insertItem(StockItemEntity(newId(), it, normalizeName(it), spot.id, StockState.IN_STOCK, shops[kind], now, now)) }
            }
        }
    }

    private companion object {
        val DEFAULTS_NL = listOf(
            Triple("Keuken", ShopKind.SUPERMARKET, listOf("Melk", "Brood", "Eieren", "Boter", "Kaas", "Koffie", "Thee", "Pasta", "Rijst", "Olijfolie", "Tomatensaus", "Fruit")),
            Triple("Badkamer", ShopKind.SUPERMARKET, listOf("WC-papier", "Tandpasta", "Shampoo", "Douchegel", "Handzeep", "Deodorant")),
            Triple("Wasplaats", ShopKind.SUPERMARKET, listOf("Waspoeder", "Wasverzachter", "Afwasmiddel", "Vaatwastabletten", "Vuilniszakken", "Keukenrol", "Allesreiniger")),
            Triple("Medicijnkast", ShopKind.PHARMACY, listOf("Paracetamol", "Ibuprofen", "Pleisters", "Ontsmettingsmiddel")),
        )
        val DEFAULTS_EN = listOf(
            Triple("Kitchen", ShopKind.SUPERMARKET, listOf("Milk", "Bread", "Eggs", "Butter", "Cheese", "Coffee", "Tea", "Pasta", "Rice", "Olive oil", "Tomato sauce", "Fruit")),
            Triple("Bathroom", ShopKind.SUPERMARKET, listOf("Toilet paper", "Toothpaste", "Shampoo", "Shower gel", "Hand soap", "Deodorant")),
            Triple("Utility room", ShopKind.SUPERMARKET, listOf("Laundry detergent", "Fabric softener", "Dish soap", "Dishwasher tablets", "Bin bags", "Kitchen roll", "Cleaner")),
            Triple("Medicine cabinet", ShopKind.PHARMACY, listOf("Paracetamol", "Ibuprofen", "Plasters", "Antiseptic")),
        )
    }
}
