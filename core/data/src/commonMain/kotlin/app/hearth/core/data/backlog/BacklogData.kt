package app.hearth.core.data.backlog

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
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
import app.hearth.core.model.Ease
import app.hearth.core.model.Anchor
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.newId
import app.hearth.core.model.normalizeName
import app.hearth.core.model.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "backlog_item", indices = [Index("done"), Index("plan", "plannedDate"), Index("parentId")])
@Serializable
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
    @ColumnInfo(defaultValue = "0") val postponed: Int = 0,
    @ColumnInfo(defaultValue = "NULL") val ease: Ease? = null,
    @ColumnInfo(defaultValue = "NULL") val smallStep: String? = null,
    @ColumnInfo(defaultValue = "NULL") val anchor: Anchor? = null,
    /** A step's place among its siblings (1 = first); older steps have none and keep the order they were added in. */
    @ColumnInfo(defaultValue = "NULL") val position: Int? = null,
) {
    fun toModel() = BacklogItem(
        id, text, section, plan, plannedDate, finishBy, done, createdAt, addedById,
        urgent, waiting, intervalDays, lastDone, parentId, postponed, ease, smallStep, anchor,
    )
}

/** One time a habit (routine) was done. Only used to see how easily a habit runs. */
@Entity(tableName = "routine_log", indices = [Index("itemId"), Index("day")])
@Serializable
data class RoutineLogEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    /** ISO date. */
    val day: String,
    val createdAt: Long,
    val updatedAt: Long,
)

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

    /** All steps, finished ones too, so a project can show its progress; in their order. */
    @Query("SELECT * FROM backlog_item WHERE parentId IS NOT NULL ORDER BY COALESCE(position, 0), createdAt")
    fun observeSteps(): Flow<List<BacklogEntity>>

    @Query("SELECT * FROM backlog_item WHERE parentId IN (:parentIds) ORDER BY COALESCE(position, 0), createdAt")
    suspend fun stepsOf(parentIds: List<String>): List<BacklogEntity>

    @Query("SELECT COUNT(*) FROM backlog_item WHERE parentId = :parentId")
    suspend fun countSteps(parentId: String): Int

    @Query("UPDATE backlog_item SET position = :position, updatedAt = :now WHERE id = :id")
    suspend fun setPosition(id: String, position: Int, now: Long)

    @Query("SELECT * FROM backlog_item WHERE done = 0 ORDER BY createdAt")
    suspend fun open(): List<BacklogEntity>

    @Insert
    suspend fun insertLog(log: RoutineLogEntity)

    @Query("UPDATE backlog_item SET postponed = postponed + 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun addPostponed(ids: List<String>, now: Long)

    @Query("UPDATE backlog_item SET ease = :ease, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setEase(ids: List<String>, ease: Ease?, now: Long)

    @Query("UPDATE backlog_item SET anchor = :anchor, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setAnchor(ids: List<String>, anchor: Anchor?, now: Long)

    @Query("UPDATE backlog_item SET smallStep = :step, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setSmallStep(ids: List<String>, step: String?, now: Long)

    @Query("SELECT * FROM routine_log WHERE day >= :fromDay")
    fun observeLogs(fromDay: String): Flow<List<RoutineLogEntity>>

    @Query("SELECT * FROM routine_log WHERE day >= :fromDay")
    suspend fun logsSince(fromDay: String): List<RoutineLogEntity>

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
        anchor: Anchor? = null,
    ): Pair<BacklogItem, Boolean> =
        db.withWriteTransaction {
            val key = normalizeName(text)
            dao.findOpen(key, parentId)?.let { return@withWriteTransaction it.toModel() to false }
            val now = clock()
            // A new step goes last, after the ones already there.
            val position = parentId?.let { dao.countSteps(it) + 1 }
            val entity = BacklogEntity(
                newId(), text.trim(), key, section, plan, date, finishBy, false, null, addedById, now, now,
                urgent = urgent, intervalDays = intervalDays, parentId = parentId, anchor = anchor, position = position,
            )
            dao.insert(entity)
            entity.toModel() to true
        }

    /** Plans items; a day pushed back (a later day, Later, or unplanned again) counts as postponed. */
    suspend fun plan(ids: List<String>, plan: Plan, date: String?, finishBy: Boolean) {
        if (ids.isEmpty()) return
        val now = clock()
        db.withWriteTransaction {
            val pushed = dao.byIds(ids).filter { old ->
                val oldDay = old.plannedDate
                old.plan == Plan.DAY && oldDay != null &&
                    (plan == Plan.LATER || plan == Plan.UNPLANNED || (plan == Plan.DAY && date != null && date > oldDay))
            }
            dao.plan(ids, plan, date, finishBy, now)
            if (pushed.isNotEmpty()) dao.addPostponed(pushed.map { it.id }, now)
        }
    }

    /** Puts a step at [toIndex] (0 = first) among its siblings and numbers them all; returns the new order. */
    suspend fun moveStep(stepId: String, toIndex: Int): List<BacklogItem> = db.withWriteTransaction {
        val step = dao.byIds(listOf(stepId)).firstOrNull() ?: return@withWriteTransaction emptyList()
        val parent = step.parentId ?: return@withWriteTransaction emptyList()
        val order = dao.stepsOf(listOf(parent)).toMutableList()
        order.removeAll { it.id == stepId }
        order.add(toIndex.coerceIn(0, order.size), step)
        val now = clock()
        // Only the steps whose place changes are written.
        order.forEachIndexed { i, s -> if (s.position != i + 1) dao.setPosition(s.id, i + 1, now) }
        dao.stepsOf(listOf(parent)).map { it.toModel() }
    }

    suspend fun byIds(ids: List<String>): List<BacklogItem> = if (ids.isEmpty()) emptyList() else dao.byIds(ids).map { it.toModel() }

    suspend fun setAnchor(ids: List<String>, anchor: Anchor?) {
        if (ids.isNotEmpty()) dao.setAnchor(ids, anchor, clock())
    }

    suspend fun setEase(ids: List<String>, ease: Ease?) {
        if (ids.isNotEmpty()) dao.setEase(ids, ease, clock())
    }

    suspend fun setSmallStep(ids: List<String>, step: String?) {
        if (ids.isNotEmpty()) dao.setSmallStep(ids, step?.trim()?.ifEmpty { null }, clock())
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
        if (ids.isEmpty()) return
        val now = clock()
        db.withWriteTransaction {
            dao.logRoutine(ids, day, now)
            // Kept so Hearth can see which habits run easily and which ask for attention.
            ids.forEach { dao.insertLog(RoutineLogEntity(newId(), it, day, now, now)) }
        }
    }

    /** When each habit was done since [fromDay] (ISO), by item id. */
    fun routineLogs(fromDay: String): Flow<Map<String, List<LocalDate>>> = dao.observeLogs(fromDay).map { toDays(it) }
    suspend fun routineLogsSince(fromDay: String): Map<String, List<LocalDate>> = toDays(dao.logsSince(fromDay))

    private fun toDays(rows: List<RoutineLogEntity>) =
        rows.groupBy { it.itemId }.mapValues { (_, list) -> list.mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() } }

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
