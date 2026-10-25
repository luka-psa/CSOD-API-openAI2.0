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
import app.hearth.core.model.Ease
import app.hearth.core.model.Huddle
import app.hearth.core.model.Meter
import app.hearth.core.model.MeterValue
import app.hearth.core.model.NEUTRAL_METER
import app.hearth.core.model.RuleCheck
import app.hearth.core.model.TranscriptLine
import app.hearth.core.model.Win
import app.hearth.core.model.newId
import app.hearth.core.model.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * The weekoverleg's notes: Wat ging goed (WIN, with text), the two household meters (FLOW / STRAIN, value 1–5)
 * and the house-rule check (RULE_CHECK). FEELING rows from v0.6 (per person) are no longer used.
 */
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
    /** Meters: what Hearth suggested. */
    @ColumnInfo(defaultValue = "NULL") val suggested: Int? = null,
) {
    fun toWin() = Win(id, text, memberId, huddleId, createdAt)

    /** FLOW / STRAIN rows: [text] is "agreed" or "neutral". */
    fun toMeter() = MeterValue(Meter.valueOf(kind), value ?: NEUTRAL_METER, suggested, text == AGREED, huddleId)
    /** RULE_CHECK rows: [text] is the rule's id; [value] 1 = loopt vanzelf, 2 = vraagt moeite, 0 = te zwaar (older rows: 1 worked, 0 not). */
    fun toRuleCheck() = RuleCheck(text, when (value) { 1 -> Ease.EASY; 2 -> Ease.EFFORT; else -> Ease.HARD }, huddleId)

    companion object {
        const val WIN = "WIN"
        const val FEELING = "FEELING"
        const val RULE_CHECK = "RULE_CHECK"
        const val AGREED = "agreed"
        val METERS = Meter.entries.map { it.name }.toSet()
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
 * The Weekoverleg's own notes: wins, the household's two meters and the house-rule check gathered for the next meeting, what Luister mee
 * heard, and the summaries of meetings held.
 */
class MeetingRepository(
    private val db: HearthDatabase,
    private val clock: () -> Long = ::nowMillis,
) {
    private val dao = db.meetingDao()

    val wins: Flow<List<Win>> = dao.observeCurrentReflections().map { list -> list.filter { it.kind == ReflectionEntity.WIN }.map { it.toWin() } }
    val transcript: Flow<List<TranscriptLine>> = dao.observeCurrentTranscript().map { list -> list.map { it.toModel() } }
    fun transcriptOf(huddleId: String): Flow<List<TranscriptLine>> = dao.observeTranscriptOf(huddleId).map { list -> list.map { it.toModel() } }
    fun huddles(limit: Int = 12): Flow<List<Huddle>> = dao.observeHuddles(limit).map { list -> list.map { it.toModel() } }

    suspend fun currentWins(): List<Win> = dao.currentReflections().filter { it.kind == ReflectionEntity.WIN }.map { it.toWin() }
    suspend fun currentTranscript(): List<TranscriptLine> = dao.currentTranscript().map { it.toModel() }
    suspend fun recentHuddles(limit: Int = 12): List<Huddle> = dao.huddles(limit).map { it.toModel() }

    suspend fun addWin(text: String, memberId: String?): Win {
        val now = clock()
        val entity = ReflectionEntity(newId(), ReflectionEntity.WIN, text.trim(), null, memberId, null, now, now)
        dao.putReflection(entity)
        return entity.toWin()
    }

    suspend fun deleteWin(id: String) = dao.deleteReflections(listOf(id))

    val meters: Flow<List<MeterValue>> = dao.observeCurrentReflections().map { list -> list.filter { it.kind in ReflectionEntity.METERS }.map { it.toMeter() } }
    val ruleChecks: Flow<List<RuleCheck>> = dao.observeCurrentReflections().map { list -> list.filter { it.kind == ReflectionEntity.RULE_CHECK }.map { it.toRuleCheck() } }
    suspend fun currentMeters(): List<MeterValue> = dao.currentReflections().filter { it.kind in ReflectionEntity.METERS }.map { it.toMeter() }
    suspend fun currentRuleChecks(): List<RuleCheck> = dao.currentReflections().filter { it.kind == ReflectionEntity.RULE_CHECK }.map { it.toRuleCheck() }
    suspend fun ruleChecksOf(huddleId: String): List<RuleCheck> = dao.reflectionsOf(huddleId).filter { it.kind == ReflectionEntity.RULE_CHECK }.map { it.toRuleCheck() }
    suspend fun metersOf(huddleId: String): List<MeterValue> = dao.reflectionsOf(huddleId).filter { it.kind in ReflectionEntity.METERS }.map { it.toMeter() }

    /** The household's meter for this meeting; null [value] = no agreement, so neutral. */
    suspend fun setMeter(meter: Meter, value: Int?, suggested: Int?): MeterValue = db.withWriteTransaction {
        val now = clock()
        val old = dao.currentReflections().firstOrNull { it.kind == meter.name }
        val entity = ReflectionEntity(
            old?.id ?: newId(), meter.name, if (value == null) "neutral" else ReflectionEntity.AGREED,
            value?.coerceIn(1, 5) ?: NEUTRAL_METER, null, null, old?.createdAt ?: now, now, suggested,
        )
        dao.putReflection(entity)
        entity.toMeter()
    }

    /** How a house rule went this week; saying it again replaces it. */
    suspend fun setRuleCheck(ruleId: String, ease: Ease): RuleCheck = db.withWriteTransaction {
        val now = clock()
        val old = dao.currentReflections().firstOrNull { it.kind == ReflectionEntity.RULE_CHECK && it.text == ruleId }
        val value = when (ease) { Ease.EASY -> 1; Ease.EFFORT -> 2; Ease.HARD -> 0 }
        val entity = ReflectionEntity(old?.id ?: newId(), ReflectionEntity.RULE_CHECK, ruleId, value, null, null, old?.createdAt ?: now, now)
        dao.putReflection(entity)
        entity.toRuleCheck()
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
     * Ends the meeting: records it with its [summary], hands it this week's wins, meters, rule checks and transcript,
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
