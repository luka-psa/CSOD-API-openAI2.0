package app.hearth.core.data.sync

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.withWriteTransaction
import app.hearth.core.data.HearthDatabase
import app.hearth.core.data.LONG_TRANSACTIONS
import app.hearth.core.data.backlog.BacklogEntity
import app.hearth.core.data.backlog.RoutineLogEntity
import app.hearth.core.data.backup.BackupPhoto
import app.hearth.core.data.backup.BackupRepository
import app.hearth.core.data.backup.HearthBackup
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64

/** The household as this device last agreed it with the others: the merge base, like git's. */
@Entity(tableName = "sync_base")
data class SyncBaseEntity(@PrimaryKey val space: String, val version: Long, val json: String, val updatedAt: Long)

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_base WHERE space = :space") suspend fun base(space: String): SyncBaseEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBase(base: SyncBaseEntity)
    @Query("DELETE FROM sync_base") suspend fun clearBase()

    // Insert what's new, update what exists: never REPLACE, which would delete a row (and cascade to its children) first.
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addShops(rows: List<ShopEntity>)
    @Update suspend fun updateShops(rows: List<ShopEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addShoppingItems(rows: List<ShoppingItemEntity>)
    @Update suspend fun updateShoppingItems(rows: List<ShoppingItemEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addBacklog(rows: List<BacklogEntity>)
    @Update suspend fun updateBacklog(rows: List<BacklogEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addMembers(rows: List<MemberEntity>)
    @Update suspend fun updateMembers(rows: List<MemberEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addBoard(rows: List<BoardEntity>)
    @Update suspend fun updateBoard(rows: List<BoardEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addSpots(rows: List<StorageSpotEntity>)
    @Update suspend fun updateSpots(rows: List<StorageSpotEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addStock(rows: List<StockItemEntity>)
    @Update suspend fun updateStock(rows: List<StockItemEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addMeals(rows: List<MealEntity>)
    @Update suspend fun updateMeals(rows: List<MealEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addDishes(rows: List<DishEntity>)
    @Update suspend fun updateDishes(rows: List<DishEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addHuddles(rows: List<HuddleEntity>)
    @Update suspend fun updateHuddles(rows: List<HuddleEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addSettings(rows: List<SettingEntity>)
    @Update suspend fun updateSettings(rows: List<SettingEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addPhotos(rows: List<RecipePhotoEntity>)
    @Update suspend fun updatePhotos(rows: List<RecipePhotoEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addReflections(rows: List<ReflectionEntity>)
    @Update suspend fun updateReflections(rows: List<ReflectionEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addTranscript(rows: List<TranscriptLineEntity>)
    @Update suspend fun updateTranscript(rows: List<TranscriptLineEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addRoutineLogs(rows: List<RoutineLogEntity>)
    @Update suspend fun updateRoutineLogs(rows: List<RoutineLogEntity>)

    // A row about to go first steps aside, so the row that replaces it (same name, other id) fits the unique index.
    @Query("UPDATE shop SET normalizedName = '~' || id WHERE id IN (:ids)") suspend fun asideShops(ids: List<String>)
    @Query("UPDATE storage_spot SET normalizedName = '~' || id WHERE id IN (:ids)") suspend fun asideSpots(ids: List<String>)
    @Query("UPDATE member SET normalizedName = '~' || id WHERE id IN (:ids)") suspend fun asideMembers(ids: List<String>)

    @Query("DELETE FROM shop WHERE id IN (:ids)") suspend fun deleteShops(ids: List<String>)
    @Query("DELETE FROM shopping_item WHERE id IN (:ids)") suspend fun deleteShoppingItems(ids: List<String>)
    @Query("DELETE FROM backlog_item WHERE id IN (:ids)") suspend fun deleteBacklog(ids: List<String>)
    @Query("DELETE FROM member WHERE id IN (:ids)") suspend fun deleteMembers(ids: List<String>)
    @Query("DELETE FROM board_item WHERE id IN (:ids)") suspend fun deleteBoard(ids: List<String>)
    @Query("DELETE FROM storage_spot WHERE id IN (:ids)") suspend fun deleteSpots(ids: List<String>)
    @Query("DELETE FROM stock_item WHERE id IN (:ids)") suspend fun deleteStock(ids: List<String>)
    @Query("DELETE FROM meal WHERE id IN (:ids)") suspend fun deleteMeals(ids: List<String>)
    @Query("DELETE FROM dish WHERE normalizedName IN (:ids)") suspend fun deleteDishes(ids: List<String>)
    @Query("DELETE FROM huddle WHERE id IN (:ids)") suspend fun deleteHuddles(ids: List<String>)
    @Query("DELETE FROM setting WHERE `key` IN (:ids)") suspend fun deleteSettings(ids: List<String>)
    @Query("DELETE FROM recipe_photo WHERE recipeKey IN (:ids)") suspend fun deletePhotos(ids: List<String>)
    @Query("DELETE FROM reflection WHERE id IN (:ids)") suspend fun deleteReflections(ids: List<String>)
    @Query("DELETE FROM transcript_line WHERE id IN (:ids)") suspend fun deleteTranscript(ids: List<String>)
    @Query("DELETE FROM routine_log WHERE id IN (:ids)") suspend fun deleteRoutineLogs(ids: List<String>)
}

/** The household this device last agreed on, at the remote's [version]. */
class SyncBase(val version: Long, val snapshot: JsonObject)

/** What a merge did: rows this device took from the others ([pulled]) and rows only it had ([pushed]). */
data class SyncMergeResult(val merged: JsonObject, val pulled: Int, val pushed: Int)

/**
 * Keeps devices of one household in step, the way git keeps clones in step: the household is one snapshot,
 * each device remembers the snapshot it last agreed on (the base), and a sync merges base, this device and
 * the remote row by row. Only rows that differ are written, in one transaction.
 */
class SyncRepository(
    private val db: HearthDatabase,
    private val backups: BackupRepository,
    private val clock: () -> Long = ::nowMillis,
) {
    private val dao = db.syncDao()

    /** What this device shares: everything a backup holds except its own settings (language, Google, sync). */
    suspend fun local(): JsonObject = SyncMerge.shareable(BackupRepository.JSON.encodeToJsonElement(HearthBackup.serializer(), backups.export()).jsonObject)

    suspend fun base(space: String): SyncBase? = dao.base(space)?.let { SyncBase(it.version, BackupRepository.JSON.parseToJsonElement(it.json).jsonObject) }

    suspend fun setBase(space: String, version: Long, snapshot: JsonObject) =
        dao.putBase(SyncBaseEntity(space, version, snapshot.toString(), clock()))

    suspend fun clearBase() = dao.clearBase()

    /** True when this device changed something since it last agreed with the others. */
    suspend fun changed(space: String): Boolean = base(space)?.snapshot?.let { !SyncMerge.same(it, local()) } ?: true

    /**
     * Merges [remote] (null: nobody has shared anything yet) into this device on top of [base], in one write
     * transaction, so a tap during a sync is never lost. Returns the merged household, ready to push.
     */
    suspend fun mergeIn(base: JsonObject?, remote: JsonObject?): SyncMergeResult {
        remote?.let(::checkVersion)
        val work: suspend () -> SyncMergeResult = {
            val local = local()
            val result = SyncMerge.merge(base, local, remote ?: SyncMerge.empty())
            apply(local, result.merged)
            result
        }
        return if (LONG_TRANSACTIONS) db.withWriteTransaction { work() } else work()
    }

    /** Takes [remote] as it is: a device joining the household. Its own settings stay. */
    suspend fun replaceWith(remote: JsonObject) {
        checkVersion(remote)
        backups.restore(BackupRepository.JSON.decodeFromJsonElement(HearthBackup.serializer(), withLocalSettings(remote)))
    }

    private fun checkVersion(snapshot: JsonObject) {
        val version = (snapshot["version"] as? JsonPrimitive)?.longOrNull ?: 0
        if (version > BackupRepository.DATABASE_VERSION) throw SyncNeedsUpdate()
    }

    private suspend fun withLocalSettings(remote: JsonObject): JsonObject {
        val own = BackupRepository.JSON.encodeToJsonElement(ListSerializer(SettingEntity.serializer()), db.backupDao().settings().filter { !SyncMerge.shared(it.key) }) as JsonArray
        val theirs = (remote["settings"] as? JsonArray).orEmpty().filter { SyncMerge.shared(it.jsonObject.str("key")) }
        return JsonObject(remote + ("settings" to JsonArray(theirs + own)) + ("createdAt" to JsonPrimitive(clock())))
    }

    /**
     * Writes only what differs between [local] and [merged]. Rows that go first step aside (their unique name),
     * then parents and children are added or updated, then what's gone is deleted children first: a child is
     * never left pointing at nothing, and a parent is never deleted while something still points at it.
     */
    private suspend fun apply(local: JsonObject, merged: JsonObject) {
        val diffs = SyncMerge.TABLES.associate { t -> t.name to SyncMerge.diff(t, local, merged) }
        fun gone(table: String) = diffs.getValue(table).deleted
        gone("shops").chunked(500).forEach { dao.asideShops(it) }
        gone("storageSpots").chunked(500).forEach { dao.asideSpots(it) }
        gone("members").chunked(500).forEach { dao.asideMembers(it) }
        // One meal per day: the old plan for a day goes before the new one comes.
        gone("meals").chunked(500).forEach { dao.deleteMeals(it) }
        for (t in SyncMerge.TABLES) {
            val rows = diffs.getValue(t.name).upserted
            if (rows.isEmpty()) continue
            when (t.name) {
                "shops" -> rows.decode(ShopEntity.serializer()).let { dao.addShops(it); dao.updateShops(it) }
                "shoppingItems" -> rows.decode(ShoppingItemEntity.serializer()).let { dao.addShoppingItems(it); dao.updateShoppingItems(it) }
                // Projects before their steps.
                "backlog" -> rows.decode(BacklogEntity.serializer()).sortedBy { it.parentId != null }.let { dao.addBacklog(it); dao.updateBacklog(it) }
                "members" -> rows.decode(MemberEntity.serializer()).let { dao.addMembers(it); dao.updateMembers(it) }
                "board" -> rows.decode(BoardEntity.serializer()).let { dao.addBoard(it); dao.updateBoard(it) }
                "storageSpots" -> rows.decode(StorageSpotEntity.serializer()).let { dao.addSpots(it); dao.updateSpots(it) }
                "stockItems" -> rows.decode(StockItemEntity.serializer()).let { dao.addStock(it); dao.updateStock(it) }
                "meals" -> rows.decode(MealEntity.serializer()).let { dao.addMeals(it); dao.updateMeals(it) }
                "dishes" -> rows.decode(DishEntity.serializer()).let { dao.addDishes(it); dao.updateDishes(it) }
                "huddles" -> rows.decode(HuddleEntity.serializer()).let { dao.addHuddles(it); dao.updateHuddles(it) }
                "settings" -> rows.decode(SettingEntity.serializer()).let { dao.addSettings(it); dao.updateSettings(it) }
                "photos" -> rows.decode(BackupPhoto.serializer()).map { RecipePhotoEntity(it.recipeKey, Base64.decode(it.jpegBase64), it.updatedAt) }
                    .let { dao.addPhotos(it); dao.updatePhotos(it) }
                "reflections" -> rows.decode(ReflectionEntity.serializer()).let { dao.addReflections(it); dao.updateReflections(it) }
                "transcript" -> rows.decode(TranscriptLineEntity.serializer()).let { dao.addTranscript(it); dao.updateTranscript(it) }
                "routineLogs" -> rows.decode(RoutineLogEntity.serializer()).let { dao.addRoutineLogs(it); dao.updateRoutineLogs(it) }
            }
        }
        for (t in SyncMerge.TABLES.reversed()) {
            if (t.name == "meals") continue
            gone(t.name).chunked(500).forEach { ids ->
                when (t.name) {
                    "shops" -> dao.deleteShops(ids)
                    "shoppingItems" -> dao.deleteShoppingItems(ids)
                    "backlog" -> dao.deleteBacklog(ids)
                    "members" -> dao.deleteMembers(ids)
                    "board" -> dao.deleteBoard(ids)
                    "storageSpots" -> dao.deleteSpots(ids)
                    "stockItems" -> dao.deleteStock(ids)
                    "dishes" -> dao.deleteDishes(ids)
                    "huddles" -> dao.deleteHuddles(ids)
                    "settings" -> dao.deleteSettings(ids)
                    "photos" -> dao.deletePhotos(ids)
                    "reflections" -> dao.deleteReflections(ids)
                    "transcript" -> dao.deleteTranscript(ids)
                    "routineLogs" -> dao.deleteRoutineLogs(ids)
                }
            }
        }
    }

    private fun <T> List<JsonObject>.decode(serializer: KSerializer<T>): List<T> = map { BackupRepository.JSON.decodeFromJsonElement(serializer, it) }
}

/** The household was shared by a newer Hearth: this device needs the update before it can merge. */
class SyncNeedsUpdate : Exception("This household was synced by a newer Hearth")

private fun JsonObject.str(field: String): String? = (this[field] as? JsonPrimitive)?.contentOrNull

/**
 * The merge itself, on the snapshot's JSON so one rule covers every table. Per row: whoever changed it since
 * the base wins; when both did, the latest change wins, and a change always beats a delete (nothing anyone
 * added is lost). Then names that must be unique (two devices both added "Koelkast") become one row, and a
 * row whose parent was deleted elsewhere brings its parent back.
 */
object SyncMerge {
    class Table(
        val name: String,
        val key: String,
        /** Unique besides the key: duplicates fold into one row. */
        val unique: String? = null,
        /** On a unique clash, keep the latest change (a meal per day) instead of the oldest row (a shop). */
        val newestWins: Boolean = false,
        /** Columns pointing at another table's key; [required] ones bring a deleted parent back. */
        val refs: Map<String, String> = emptyMap(),
        val required: Set<String> = emptySet(),
    )

    /** Parents before children. */
    val TABLES = listOf(
        Table("shops", "id", unique = "normalizedName"),
        Table("storageSpots", "id", unique = "normalizedName"),
        Table("members", "id", unique = "normalizedName"),
        Table("stockItems", "id", refs = mapOf("spotId" to "storageSpots", "shopId" to "shops"), required = setOf("spotId")),
        Table("shoppingItems", "id", refs = mapOf("shopId" to "shops"), required = setOf("shopId")),
        Table("backlog", "id", refs = mapOf("parentId" to "backlog"), required = setOf("parentId")),
        Table("board", "id", refs = mapOf("memberId" to "members")),
        Table("dishes", "normalizedName"),
        Table("meals", "id", unique = "date", newestWins = true),
        Table("huddles", "id"),
        Table("settings", "key"),
        Table("photos", "recipeKey"),
        Table("reflections", "id", refs = mapOf("memberId" to "members")),
        Table("transcript", "id"),
        Table("routineLogs", "id"),
    )

    /** Settings that belong to one device: its language, its Google link, its sync keys, its relay link. */
    fun shared(key: String?): Boolean = key != null && key !in SettingsRepository.DEVICE_ONLY &&
        !key.startsWith("google.") && !key.startsWith("sync.") && key != "app.lang"

    fun empty(): JsonObject = JsonObject(emptyMap())

    /** A snapshot without this device's own settings and without its creation time (which changes every export). */
    fun shareable(snapshot: JsonObject): JsonObject {
        val settings = (snapshot["settings"] as? JsonArray).orEmpty().filter { shared(it.jsonObject.str("key")) }
        return JsonObject(snapshot - "createdAt" + ("settings" to JsonArray(settings)))
    }

    fun merge(base: JsonObject?, local: JsonObject, remote: JsonObject): SyncMergeResult {
        val out = local.toMutableMap()
        var pulled = 0
        var pushed = 0
        val merged = mutableMapOf<String, MutableMap<String, JsonObject>>()
        for (t in TABLES) {
            val b = rows(base, t)
            val l = rows(local, t)
            val r = rows(remote, t)
            val m = LinkedHashMap<String, JsonObject>()
            for (id in (l.keys + r.keys + b.keys)) {
                val pick = pick(b[id], l[id], r[id]) ?: continue
                m[id] = pick
            }
            merged[t.name] = m
        }
        // Rows of the same name from two devices become one; what pointed at the other follows.
        for (t in TABLES) {
            val unique = t.unique ?: continue
            val m = merged.getValue(t.name)
            val inRemote = rows(remote, t).keys
            for ((_, dupes) in m.values.groupBy { it.str(unique) }.filter { it.key != null && it.value.size > 1 }) {
                val keep = if (t.newestWins) dupes.maxBy { it.long("updatedAt") }
                else dupes.minWith(compareBy<JsonObject>({ it.str(t.key) !in inRemote }, { it.long("createdAt") }, { it.str(t.key) }))
                val keepId = keep.str(t.key)!!
                for (gone in dupes) {
                    val goneId = gone.str(t.key)!!
                    if (goneId == keepId) continue
                    m.remove(goneId)
                    for (child in TABLES) for ((column, parent) in child.refs) {
                        if (parent != t.name) continue
                        val rowsOfChild = merged.getValue(child.name)
                        rowsOfChild.entries.filter { it.value.str(column) == goneId }.forEach { (id, row) ->
                            rowsOfChild[id] = JsonObject(row + (column to JsonPrimitive(keepId)))
                        }
                    }
                }
            }
        }
        // A step ticked here while its project was deleted there: the project comes back.
        for (t in TABLES) for (column in t.required) {
            val parent = TABLES.first { it.name == t.refs.getValue(column) }
            val parents = merged.getValue(parent.name)
            for (row in merged.getValue(t.name).values.toList()) {
                val id = row.str(column) ?: continue
                if (id in parents) continue
                val back = rows(local, parent)[id] ?: rows(remote, parent)[id] ?: rows(base, parent)[id] ?: continue
                parents[id] = back
            }
        }
        for (t in TABLES) {
            val m = merged.getValue(t.name)
            val l = rows(local, t)
            val r = rows(remote, t)
            pulled += m.count { (id, row) -> l[id] != row } + l.keys.count { it !in m }
            pushed += m.count { (id, row) -> r[id] != row } + r.keys.count { it !in m }
            out[t.name] = JsonArray(m.values.toList())
        }
        return SyncMergeResult(JsonObject(out), pulled, pushed)
    }

    /** Per row, three ways: who changed it since the base? */
    private fun pick(b: JsonObject?, l: JsonObject?, r: JsonObject?): JsonObject? = when {
        l == r -> l
        l == b -> r
        r == b -> l
        l == null -> r
        r == null -> l
        else -> if (l.long("updatedAt") > r.long("updatedAt")) l else r
    }

    /** The same rows in every table (order and the export's own fields don't count). */
    fun same(a: JsonObject, b: JsonObject): Boolean = TABLES.all { rows(a, it) == rows(b, it) }

    class Diff(val upserted: List<JsonObject>, val deleted: List<String>)

    fun diff(t: Table, before: JsonObject, after: JsonObject): Diff {
        val b = rows(before, t)
        val a = rows(after, t)
        return Diff(a.filter { (id, row) -> b[id] != row }.values.toList(), b.keys.filter { it !in a })
    }

    private fun rows(snapshot: JsonObject?, t: Table): Map<String, JsonObject> =
        (snapshot?.get(t.name) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { row -> row.str(t.key)?.let { it to row } }.toMap()

    private fun JsonObject.long(field: String): Long = (this[field] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
