package app.hearth.core.data.backlog

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.withWriteTransaction
import app.hearth.core.data.HearthDatabase
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.newId
import app.hearth.core.model.normalizeName
import app.hearth.core.model.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "backlog_item", indices = [Index("done"), Index("plan", "plannedDate")])
data class BacklogEntity(
    @PrimaryKey val id: String,
    val text: String,
    val normalizedText: String,
    val section: Section,
    @ColumnInfo(defaultValue = "UNPLANNED") val plan: Plan,
    val plannedDate: String?,
    @ColumnInfo(defaultValue = "0") val finishBy: Boolean,
    val done: Boolean,
    val doneAt: Long?,
    val addedById: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toModel() = BacklogItem(id, text, section, plan, plannedDate, finishBy, done, createdAt, addedById)
}

@Dao
interface BacklogDao {
    @Query("SELECT * FROM backlog_item WHERE done = 0 ORDER BY createdAt")
    fun observeOpen(): Flow<List<BacklogEntity>>

    @Query("SELECT COUNT(*) FROM backlog_item WHERE done = 1 AND doneAt >= :since")
    fun observeDoneSince(since: Long): Flow<Int>

    @Query("SELECT * FROM backlog_item WHERE done = 0 ORDER BY createdAt")
    suspend fun open(): List<BacklogEntity>

    @Query("SELECT * FROM backlog_item WHERE done = 0 AND normalizedText = :normalizedText LIMIT 1")
    suspend fun findOpen(normalizedText: String): BacklogEntity?

    @Query("SELECT * FROM backlog_item WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<BacklogEntity>

    @Insert
    suspend fun insert(item: BacklogEntity)

    @Query("UPDATE backlog_item SET plan = :plan, plannedDate = :date, finishBy = :finishBy, updatedAt = :now WHERE id IN (:ids)")
    suspend fun plan(ids: List<String>, plan: Plan, date: String?, finishBy: Boolean, now: Long)

    @Query("UPDATE backlog_item SET done = :done, doneAt = :doneAt, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setDone(ids: List<String>, done: Boolean, doneAt: Long?, now: Long)

    @Query("DELETE FROM backlog_item WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}

/** Storage for the backlog. Screens may observe the Flows; writes come from tools only. */
class BacklogRepository(
    private val db: HearthDatabase,
    private val clock: () -> Long = ::nowMillis,
) {
    private val dao = db.backlogDao()

    val open: Flow<List<BacklogItem>> = dao.observeOpen().map { list -> list.map { it.toModel() } }

    fun doneSince(since: Long): Flow<Int> = dao.observeDoneSince(since)

    suspend fun openItems(): List<BacklogItem> = dao.open().map { it.toModel() }

    suspend fun itemsByIds(ids: List<String>): List<BacklogItem> = dao.byIds(ids).map { it.toModel() }

    /** Adds [text], or returns the identical open item if it is already there. */
    suspend fun add(text: String, section: Section, plan: Plan, date: String?, finishBy: Boolean, addedById: String?): Pair<BacklogItem, Boolean> =
        db.withWriteTransaction {
            val key = normalizeName(text)
            dao.findOpen(key)?.let { return@withWriteTransaction it.toModel() to false }
            val now = clock()
            val entity = BacklogEntity(newId(), text.trim(), key, section, plan, date, finishBy, false, null, addedById, now, now)
            dao.insert(entity)
            entity.toModel() to true
        }

    suspend fun plan(ids: List<String>, plan: Plan, date: String?, finishBy: Boolean) {
        if (ids.isNotEmpty()) dao.plan(ids, plan, date, finishBy, clock())
    }

    suspend fun setDone(ids: List<String>, done: Boolean) {
        if (ids.isEmpty()) return
        val now = clock()
        dao.setDone(ids, done, if (done) now else null, now)
    }

    suspend fun delete(ids: List<String>) {
        if (ids.isNotEmpty()) dao.delete(ids)
    }
}
