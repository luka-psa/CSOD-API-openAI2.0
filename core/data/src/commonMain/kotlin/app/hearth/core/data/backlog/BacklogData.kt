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

@Entity(tableName = "backlog_item", indices = [Index("done"), Index("plan", "plannedDate"), Index("parentId")])
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
    @ColumnInfo(defaultValue = "0") val urgent: Boolean = false,
    @ColumnInfo(defaultValue = "0") val waiting: Boolean = false,
    val intervalDays: Int? = null,
    val lastDone: String? = null,
    val parentId: String? = null,
) {
    fun toModel() = BacklogItem(
        id, text, section, plan, plannedDate, finishBy, done, createdAt, addedById,
        urgent, waiting, intervalDays, lastDone, parentId,
    )
}

@Dao
interface BacklogDao {
    @Query("SELECT * FROM backlog_item WHERE done = 0 ORDER BY createdAt")
    fun observeOpen(): Flow<List<BacklogEntity>>

    /** Finished items and routines ticked off since [since]. */
    @Query("SELECT COUNT(*) FROM backlog_item WHERE doneAt >= :since")
    fun observeDoneSince(since: Long): Flow<Int>

    /** What got done (or a routine ticked off) since [since], newest first. */
    @Query("SELECT * FROM backlog_item WHERE doneAt >= :since ORDER BY doneAt DESC")
    suspend fun doneItemsSince(since: Long): List<BacklogEntity>

    /** All project steps, finished ones too, so a project can show its progress. */
    @Query("SELECT * FROM backlog_item WHERE parentId IS NOT NULL ORDER BY createdAt")
    fun observeSteps(): Flow<List<BacklogEntity>>

    @Query("SELECT * FROM backlog_item WHERE parentId IN (:parentIds) ORDER BY createdAt")
    suspend fun stepsOf(parentIds: List<String>): List<BacklogEntity>

    @Query("SELECT * FROM backlog_item WHERE done = 0 ORDER BY createdAt")
    suspend fun open(): List<BacklogEntity>

    @Query("SELECT * FROM backlog_item WHERE done = 0 AND normalizedText = :normalizedText AND parentId IS :parentId LIMIT 1")
    suspend fun findOpen(normalizedText: String, parentId: String?): BacklogEntity?

    @Query("SELECT * FROM backlog_item WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<BacklogEntity>

    @Insert
    suspend fun insert(item: BacklogEntity)

    @Query("UPDATE backlog_item SET plan = :plan, plannedDate = :date, finishBy = :finishBy, updatedAt = :now WHERE id IN (:ids)")
    suspend fun plan(ids: List<String>, plan: Plan, date: String?, finishBy: Boolean, now: Long)

    @Query("UPDATE backlog_item SET done = :done, doneAt = :doneAt, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setDone(ids: List<String>, done: Boolean, doneAt: Long?, now: Long)

    /** Finishing a project finishes its open steps too. */
    @Query("UPDATE backlog_item SET done = 1, doneAt = :now, updatedAt = :now WHERE parentId IN (:ids) AND done = 0")
    suspend fun finishSteps(ids: List<String>, now: Long)

    /** A routine that was given a day goes back to "whenever it fits" once done. */
    @Query("UPDATE backlog_item SET lastDone = :day, doneAt = :now, plan = 'UNPLANNED', plannedDate = NULL, finishBy = 0, updatedAt = :now WHERE id IN (:ids)")
    suspend fun logRoutine(ids: List<String>, day: String, now: Long)

    @Query("UPDATE backlog_item SET intervalDays = :days, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setInterval(ids: List<String>, days: Int?, now: Long)

    @Query("UPDATE backlog_item SET urgent = :urgent, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setUrgent(ids: List<String>, urgent: Boolean, now: Long)

    @Query("UPDATE backlog_item SET waiting = :waiting, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setWaiting(ids: List<String>, waiting: Boolean, now: Long)

    @Query("UPDATE backlog_item SET section = :section, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setSection(ids: List<String>, section: Section, now: Long)

    /** Deleting a project deletes its steps. */
    @Query("DELETE FROM backlog_item WHERE id IN (:ids) OR parentId IN (:ids)")
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

    suspend fun doneItemsSince(since: Long): List<BacklogItem> = dao.doneItemsSince(since).map { it.toModel() }

    val steps: Flow<List<BacklogItem>> = dao.observeSteps().map { list -> list.map { it.toModel() } }

    suspend fun stepsOf(projectIds: List<String>): List<BacklogItem> =
        if (projectIds.isEmpty()) emptyList() else dao.stepsOf(projectIds).map { it.toModel() }

    suspend fun openItems(): List<BacklogItem> = dao.open().map { it.toModel() }

    suspend fun itemsByIds(ids: List<String>): List<BacklogItem> = dao.byIds(ids).map { it.toModel() }

    /** Adds [text], or returns the identical open item if it is already there. */
    suspend fun add(
        text: String,
        section: Section,
        plan: Plan,
        date: String?,
        finishBy: Boolean,
        addedById: String?,
        urgent: Boolean = false,
        intervalDays: Int? = null,
        parentId: String? = null,
    ): Pair<BacklogItem, Boolean> =
        db.withWriteTransaction {
            val key = normalizeName(text)
            dao.findOpen(key, parentId)?.let { return@withWriteTransaction it.toModel() to false }
            val now = clock()
            val entity = BacklogEntity(
                newId(), text.trim(), key, section, plan, date, finishBy, false, null, addedById, now, now,
                urgent = urgent, intervalDays = intervalDays, parentId = parentId,
            )
            dao.insert(entity)
            entity.toModel() to true
        }

    suspend fun plan(ids: List<String>, plan: Plan, date: String?, finishBy: Boolean) {
        if (ids.isNotEmpty()) dao.plan(ids, plan, date, finishBy, clock())
    }

    suspend fun setDone(ids: List<String>, done: Boolean) {
        if (ids.isEmpty()) return
        val now = clock()
        db.withWriteTransaction {
            dao.setDone(ids, done, if (done) now else null, now)
            if (done) dao.finishSteps(ids, now)
        }
    }

    /** A routine was done on [day]: it stays in the list and comes back after its interval. */
    suspend fun logRoutine(ids: List<String>, day: String) {
        if (ids.isNotEmpty()) dao.logRoutine(ids, day, clock())
    }

    suspend fun setInterval(ids: List<String>, days: Int?) {
        if (ids.isNotEmpty()) dao.setInterval(ids, days, clock())
    }

    suspend fun setUrgent(ids: List<String>, urgent: Boolean) {
        if (ids.isNotEmpty()) dao.setUrgent(ids, urgent, clock())
    }

    /** Waiting for a reply: comes back under "don't forget" on [followUp]. */
    suspend fun setWaiting(ids: List<String>, waiting: Boolean, followUp: String?) {
        if (ids.isEmpty()) return
        db.withWriteTransaction {
            val now = clock()
            dao.setWaiting(ids, waiting, now)
            if (waiting) dao.plan(ids, Plan.DAY, followUp, false, now)
        }
    }

    suspend fun setSection(ids: List<String>, section: Section) {
        if (ids.isNotEmpty()) dao.setSection(ids, section, clock())
    }

    suspend fun delete(ids: List<String>) {
        if (ids.isNotEmpty()) dao.delete(ids)
    }
}
