package app.hearth.core.data.inbox

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.withWriteTransaction
import app.hearth.core.data.HearthDatabase
import app.hearth.core.model.InboxItem
import app.hearth.core.model.Section
import app.hearth.core.model.newId
import app.hearth.core.model.normalizeName
import app.hearth.core.model.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "inbox_item", indices = [Index("archived")])
data class InboxEntity(
    @PrimaryKey val id: String,
    val text: String,
    val normalizedText: String,
    val section: Section,
    val archived: Boolean,
    val archivedAt: Long?,
    val addedById: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toModel() = InboxItem(id, text, section, archived, createdAt, addedById)
}

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox_item WHERE archived = 0 ORDER BY createdAt")
    fun observeOpen(): Flow<List<InboxEntity>>

    @Query("SELECT * FROM inbox_item WHERE archived = 0 ORDER BY createdAt")
    suspend fun open(): List<InboxEntity>

    @Query("SELECT * FROM inbox_item WHERE archived = 0 AND normalizedText = :normalizedText LIMIT 1")
    suspend fun findOpen(normalizedText: String): InboxEntity?

    @Query("SELECT * FROM inbox_item WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<InboxEntity>

    @Insert
    suspend fun insert(item: InboxEntity)

    @Query("UPDATE inbox_item SET archived = 1, archivedAt = :now, updatedAt = :now WHERE id IN (:ids)")
    suspend fun archive(ids: List<String>, now: Long)
}

/** Storage for the inbox. Screens may observe [open]; writes come from tools only. */
class InboxRepository(
    private val db: HearthDatabase,
    private val clock: () -> Long = ::nowMillis,
) {
    private val dao = db.inboxDao()

    val open: Flow<List<InboxItem>> = dao.observeOpen().map { list -> list.map { it.toModel() } }

    suspend fun openItems(): List<InboxItem> = dao.open().map { it.toModel() }

    suspend fun itemsByIds(ids: List<String>): List<InboxItem> = dao.byIds(ids).map { it.toModel() }

    /** Adds [text], or returns the identical open item if it is already there. */
    suspend fun capture(text: String, section: Section, addedById: String?): Pair<InboxItem, Boolean> =
        db.withWriteTransaction {
            val key = normalizeName(text)
            dao.findOpen(key)?.let { return@withWriteTransaction it.toModel() to false }
            val now = clock()
            val entity = InboxEntity(newId(), text.trim(), key, section, false, null, addedById, now, now)
            dao.insert(entity)
            entity.toModel() to true
        }

    suspend fun archive(ids: List<String>) {
        if (ids.isNotEmpty()) dao.archive(ids, clock())
    }
}
