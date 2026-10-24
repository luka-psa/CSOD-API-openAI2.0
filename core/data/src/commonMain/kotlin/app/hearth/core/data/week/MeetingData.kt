package app.hearth.core.data.week

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.withWriteTransaction
import app.hearth.core.data.HearthDatabase
import app.hearth.core.model.Huddle
import app.hearth.core.model.TranscriptLine
import app.hearth.core.model.WeekFeeling
import app.hearth.core.model.Win
import app.hearth.core.model.newId
import app.hearth.core.model.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** Wat ging goed (WIN, with text) and how heavy the week felt (FEELING, with a value 1–5). */
@Entity(tableName = "reflection", indices = [Index("huddleId")])
@Serializable
data class ReflectionEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val text: String,
    @ColumnInfo(defaultValue = "NULL") val value: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val memberId: String? = null,
    /** Null until the meeting it belongs to ends. */
    @ColumnInfo(defaultValue = "NULL") val huddleId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toWin() = Win(id, text, memberId, huddleId, createdAt)
    fun toFeeling() = WeekFeeling(id, value ?: 3, memberId, huddleId, createdAt)

    companion object {
        const val WIN = "WIN"
        const val FEELING = "FEELING"
    }
}

/** What Luister mee heard, one line per stretch of talking. */
@Entity(tableName = "transcript_line", indices = [Index("huddleId")])
@Serializable
data class TranscriptLineEntity(
    @PrimaryKey val id: String,
    val text: String,
    @ColumnInfo(defaultValue = "NULL") val huddleId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Already went through a Voorstel, so it isn't proposed twice. */
    @ColumnInfo(defaultValue = "0") val sorted: Boolean = false,
) {
    fun toModel() = TranscriptLine(id, text, huddleId, createdAt, sorted)
}

@Dao
interface MeetingDao {
    @Query("SELECT * FROM reflection WHERE huddleId IS NULL ORDER BY createdAt")
    fun observeCurrentReflections(): Flow<List<ReflectionEntity>>

    @Query("SELECT * FROM reflection WHERE huddleId IS NULL ORDER BY createdAt")
    suspend fun currentReflections(): List<ReflectionEntity>

    @Query("SELECT * FROM reflection WHERE huddleId = :huddleId ORDER BY createdAt")
    suspend fun reflectionsOf(huddleId: String): List<ReflectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putReflection(item: ReflectionEntity)

    @Query("DELETE FROM reflection WHERE id IN (:ids)")
    suspend fun deleteReflections(ids: List<String>)

    @Query("UPDATE reflection SET huddleId = :huddleId, updatedAt = :now WHERE huddleId IS NULL")
    suspend fun closeReflections(huddleId: String, now: Long)

    @Query("SELECT * FROM transcript_line WHERE huddleId IS NULL ORDER BY createdAt")
    fun observeCurrentTranscript(): Flow<List<TranscriptLineEntity>>

    @Query("SELECT * FROM transcript_line WHERE huddleId IS NULL ORDER BY createdAt")
    suspend fun currentTranscript(): List<TranscriptLineEntity>

    @Query("SELECT * FROM transcript_line WHERE huddleId = :huddleId ORDER BY createdAt")
    fun observeTranscriptOf(huddleId: String): Flow<List<TranscriptLineEntity>>

    @Insert
    suspend fun insertLine(line: TranscriptLineEntity)

    @Query("UPDATE transcript_line SET sorted = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun markSorted(ids: List<String>, now: Long)

    @Query("DELETE FROM transcript_line WHERE huddleId IS NULL")
    suspend fun clearCurrentTranscript()

    @Query("UPDATE transcript_line SET huddleId = :huddleId, updatedAt = :now WHERE huddleId IS NULL")
    suspend fun closeTranscript(huddleId: String, now: Long)

    /** Older meetings' transcripts go once a newer meeting has ended; their summaries stay. */
    @Query("DELETE FROM transcript_line WHERE huddleId IS NOT NULL AND huddleId != :keep")
    suspend fun deleteTranscriptsExcept(keep: String)

    @Query("SELECT * FROM huddle ORDER BY heldOn DESC, createdAt DESC LIMIT :limit")
    fun observeHuddles(limit: Int): Flow<List<HuddleEntity>>

    @Query("SELECT * FROM huddle ORDER BY heldOn DESC, createdAt DESC LIMIT :limit")
    suspend fun huddles(limit: Int): List<HuddleEntity>

    @Insert
    suspend fun insertHuddle(huddle: HuddleEntity)
}

/**
 * The Weekoverleg's own notes: wins and week feelings gathered for the next meeting, what Luister mee
 * heard, and the summaries of meetings held.
 */
class MeetingRepository(
    private val db: HearthDatabase,
    private val clock: () -> Long = ::nowMillis,
) {
    private val dao = db.meetingDao()

    val wins: Flow<List<Win>> = dao.observeCurrentReflections().map { list -> list.filter { it.kind == ReflectionEntity.WIN }.map { it.toWin() } }
    val feelings: Flow<List<WeekFeeling>> = dao.observeCurrentReflections().map { list -> list.filter { it.kind == ReflectionEntity.FEELING }.map { it.toFeeling() } }
    val transcript: Flow<List<TranscriptLine>> = dao.observeCurrentTranscript().map { list -> list.map { it.toModel() } }
    fun transcriptOf(huddleId: String): Flow<List<TranscriptLine>> = dao.observeTranscriptOf(huddleId).map { list -> list.map { it.toModel() } }
    fun huddles(limit: Int = 12): Flow<List<Huddle>> = dao.observeHuddles(limit).map { list -> list.map { it.toModel() } }

    suspend fun currentWins(): List<Win> = dao.currentReflections().filter { it.kind == ReflectionEntity.WIN }.map { it.toWin() }
    suspend fun currentFeelings(): List<WeekFeeling> = dao.currentReflections().filter { it.kind == ReflectionEntity.FEELING }.map { it.toFeeling() }
    suspend fun currentTranscript(): List<TranscriptLine> = dao.currentTranscript().map { it.toModel() }
    suspend fun recentHuddles(limit: Int = 12): List<Huddle> = dao.huddles(limit).map { it.toModel() }
    suspend fun feelingsOf(huddleId: String): List<WeekFeeling> = dao.reflectionsOf(huddleId).filter { it.kind == ReflectionEntity.FEELING }.map { it.toFeeling() }

    suspend fun addWin(text: String, memberId: String?): Win {
        val now = clock()
        val entity = ReflectionEntity(newId(), ReflectionEntity.WIN, text.trim(), null, memberId, null, now, now)
        dao.putReflection(entity)
        return entity.toWin()
    }

    suspend fun deleteWin(id: String) = dao.deleteReflections(listOf(id))

    /** One feeling per person per meeting: saying it again replaces it. */
    suspend fun setFeeling(value: Int, memberId: String?): WeekFeeling = db.withWriteTransaction {
        val now = clock()
        val old = dao.currentReflections().firstOrNull { it.kind == ReflectionEntity.FEELING && it.memberId == memberId }
        val entity = ReflectionEntity(old?.id ?: newId(), ReflectionEntity.FEELING, "", value.coerceIn(1, 5), memberId, null, old?.createdAt ?: now, now)
        dao.putReflection(entity)
        entity.toFeeling()
    }

    suspend fun addLine(text: String): TranscriptLine {
        val now = clock()
        val entity = TranscriptLineEntity(newId(), text.trim(), null, now, now)
        dao.insertLine(entity)
        return entity.toModel()
    }

    suspend fun clearTranscript() = dao.clearCurrentTranscript()

    suspend fun markSorted(ids: List<String>) {
        if (ids.isNotEmpty()) dao.markSorted(ids, clock())
    }

    /**
     * Ends the meeting: records it with its [summary], hands it this week's wins, feelings and transcript,
     * and lets the transcript of the meeting before go.
     */
    suspend fun close(date: String, summary: String): Huddle = db.withWriteTransaction {
        val now = clock()
        val huddle = HuddleEntity(newId(), date, now, now, summary)
        dao.insertHuddle(huddle)
        dao.closeReflections(huddle.id, now)
        dao.closeTranscript(huddle.id, now)
        dao.deleteTranscriptsExcept(huddle.id)
        huddle.toModel()
    }
}
