package app.hearth.core.data.backup

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.withWriteTransaction
import app.hearth.core.data.HearthDatabase
import app.hearth.core.data.LONG_TRANSACTIONS
import app.hearth.core.data.backlog.BacklogEntity
import app.hearth.core.data.board.BoardEntity
import app.hearth.core.data.board.MemberEntity
import app.hearth.core.data.food.ShopEntity
import app.hearth.core.data.food.ShoppingItemEntity
import app.hearth.core.data.food.StockItemEntity
import app.hearth.core.data.food.StorageSpotEntity
import app.hearth.core.data.settings.SettingEntity
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.DishEntity
import app.hearth.core.data.week.HuddleEntity
import app.hearth.core.data.week.MealEntity
import app.hearth.core.data.week.RecipePhotoEntity
import app.hearth.core.data.week.ReflectionEntity
import app.hearth.core.data.week.TranscriptLineEntity
import app.hearth.core.model.nowMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/** Everything the household has, as one file. [version] is the database version it came from. */
@Serializable
data class HearthBackup(
    val format: Int = FORMAT,
    val version: Int,
    val createdAt: Long,
    val shops: List<ShopEntity> = emptyList(),
    val shoppingItems: List<ShoppingItemEntity> = emptyList(),
    val backlog: List<BacklogEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val board: List<BoardEntity> = emptyList(),
    val storageSpots: List<StorageSpotEntity> = emptyList(),
    val stockItems: List<StockItemEntity> = emptyList(),
    val meals: List<MealEntity> = emptyList(),
    val dishes: List<DishEntity> = emptyList(),
    val huddles: List<HuddleEntity> = emptyList(),
    val settings: List<SettingEntity> = emptyList(),
    val photos: List<BackupPhoto> = emptyList(),
    val reflections: List<ReflectionEntity> = emptyList(),
    val transcript: List<TranscriptLineEntity> = emptyList(),
) {
    /** How much is in it, for "12 taken, 30 boodschappen…" style summaries. */
    val itemCount: Int get() = backlog.size + shoppingItems.size + board.size + stockItems.size + meals.size + dishes.size

    companion object {
        const val FORMAT = 1
    }
}

@Serializable
data class BackupPhoto(val recipeKey: String, val jpegBase64: String, val updatedAt: Long)

@Dao
interface BackupDao {
    @Query("SELECT * FROM shop") suspend fun shops(): List<ShopEntity>
    @Query("SELECT * FROM shopping_item") suspend fun shoppingItems(): List<ShoppingItemEntity>
    @Query("SELECT * FROM backlog_item") suspend fun backlog(): List<BacklogEntity>
    @Query("SELECT * FROM member") suspend fun members(): List<MemberEntity>
    @Query("SELECT * FROM board_item") suspend fun board(): List<BoardEntity>
    @Query("SELECT * FROM storage_spot") suspend fun spots(): List<StorageSpotEntity>
    @Query("SELECT * FROM stock_item") suspend fun stock(): List<StockItemEntity>
    @Query("SELECT * FROM meal") suspend fun meals(): List<MealEntity>
    @Query("SELECT * FROM dish") suspend fun dishes(): List<DishEntity>
    @Query("SELECT * FROM huddle") suspend fun huddles(): List<HuddleEntity>
    @Query("SELECT * FROM setting") suspend fun settings(): List<SettingEntity>
    @Query("SELECT * FROM recipe_photo") suspend fun photos(): List<RecipePhotoEntity>
    @Query("SELECT * FROM reflection") suspend fun reflections(): List<ReflectionEntity>
    @Query("SELECT * FROM transcript_line") suspend fun transcript(): List<TranscriptLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putShops(rows: List<ShopEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putShoppingItems(rows: List<ShoppingItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBacklog(rows: List<BacklogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMembers(rows: List<MemberEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBoard(rows: List<BoardEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSpots(rows: List<StorageSpotEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putStock(rows: List<StockItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMeals(rows: List<MealEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDishes(rows: List<DishEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHuddles(rows: List<HuddleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSettings(rows: List<SettingEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPhotos(rows: List<RecipePhotoEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReflections(rows: List<ReflectionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTranscript(rows: List<TranscriptLineEntity>)

    // Children before parents, so foreign keys never point at nothing.
    @Query("DELETE FROM shopping_item") suspend fun clearShoppingItems()
    @Query("DELETE FROM stock_item") suspend fun clearStock()
    @Query("DELETE FROM storage_spot") suspend fun clearSpots()
    @Query("DELETE FROM shop") suspend fun clearShops()
    @Query("DELETE FROM backlog_item") suspend fun clearBacklog()
    @Query("DELETE FROM board_item") suspend fun clearBoard()
    @Query("DELETE FROM member") suspend fun clearMembers()
    @Query("DELETE FROM meal") suspend fun clearMeals()
    @Query("DELETE FROM dish") suspend fun clearDishes()
    @Query("DELETE FROM recipe_photo") suspend fun clearPhotos()
    @Query("DELETE FROM huddle") suspend fun clearHuddles()
    @Query("DELETE FROM reflection") suspend fun clearReflections()
    @Query("DELETE FROM transcript_line") suspend fun clearTranscript()
    @Query("DELETE FROM setting WHERE key NOT IN (:keep)") suspend fun clearSettingsExcept(keep: List<String>)
}

/**
 * Turns the whole household into one JSON file and back. This tablet's own connection (household id,
 * tablet key, relay) never goes into a backup and survives a restore.
 */
class BackupRepository(
    private val db: HearthDatabase,
    private val clock: () -> Long = ::nowMillis,
) {
    private val dao = db.backupDao()

    suspend fun export(): HearthBackup = HearthBackup(
        version = DATABASE_VERSION,
        createdAt = clock(),
        shops = dao.shops(),
        shoppingItems = dao.shoppingItems(),
        backlog = dao.backlog(),
        members = dao.members(),
        board = dao.board(),
        storageSpots = dao.spots(),
        stockItems = dao.stock(),
        meals = dao.meals(),
        dishes = dao.dishes(),
        huddles = dao.huddles(),
        settings = dao.settings().filter { it.key !in SettingsRepository.DEVICE_ONLY },
        photos = dao.photos().map { BackupPhoto(it.recipeKey, Base64.encode(it.jpeg), it.updatedAt) },
        reflections = dao.reflections(),
        transcript = dao.transcript(),
    )

    suspend fun exportJson(): String = JSON.encodeToString(HearthBackup.serializer(), export())

    /** Reads a backup file; throws [IllegalArgumentException] when it isn't one Hearth can use. */
    fun parse(json: String): HearthBackup {
        val backup = runCatching { JSON.decodeFromString(HearthBackup.serializer(), json) }
            .getOrElse { throw IllegalArgumentException("Not a Hearth backup", it) }
        require(backup.format <= HearthBackup.FORMAT && backup.version <= DATABASE_VERSION) { "Made by a newer Hearth" }
        return backup
    }

    /**
     * Replaces everything on this tablet with [backup], in one transaction (all or nothing). This tablet's
     * own Google settings (which calendar it fills, what's switched on) win over older ones in the backup.
     * The browser preview runs it step by step: its SQLite worker can stall at the end of a long
     * transaction (the data is written, the call never returns).
     */
    suspend fun restore(backup: HearthBackup) {
        if (LONG_TRANSACTIONS) db.withWriteTransaction { replaceAll(backup) } else replaceAll(backup)
    }

    private suspend fun replaceAll(backup: HearthBackup) {
        val local = dao.settings().filter { it.key.startsWith(LOCAL_FIRST) }
        dao.clearShoppingItems()
        dao.clearStock()
        dao.clearSpots()
        dao.clearShops()
        dao.clearBacklog()
        dao.clearBoard()
        dao.clearMembers()
        dao.clearMeals()
        dao.clearDishes()
        dao.clearPhotos()
        dao.clearHuddles()
        dao.clearReflections()
        dao.clearTranscript()
        dao.clearSettingsExcept(SettingsRepository.DEVICE_ONLY.toList())

        dao.putShops(backup.shops)
        dao.putSpots(backup.storageSpots)
        dao.putStock(backup.stockItems)
        dao.putShoppingItems(backup.shoppingItems)
        dao.putMembers(backup.members)
        // Projects before their steps.
        dao.putBacklog(backup.backlog.sortedBy { it.parentId != null })
        dao.putBoard(backup.board)
        dao.putDishes(backup.dishes)
        dao.putMeals(backup.meals)
        dao.putHuddles(backup.huddles)
        dao.putSettings(backup.settings.filter { it.key !in SettingsRepository.DEVICE_ONLY })
        dao.putSettings(local)
        dao.putReflections(backup.reflections)
        dao.putTranscript(backup.transcript)
        dao.putPhotos(backup.photos.map { RecipePhotoEntity(it.recipeKey, Base64.decode(it.jpegBase64), it.updatedAt) })
    }

    companion object {
        const val DATABASE_VERSION = 10
        private const val LOCAL_FIRST = "google."
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
