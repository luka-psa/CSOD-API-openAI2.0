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
import app.hearth.core.model.SCORE_MAX
import app.hearth.core.model.score
import app.hearth.core.model.Huddle
import app.hearth.core.model.WeekRating
import app.hearth.core.model.RuleCheck
import app.hearth.core.model.TranscriptLine
import app.hearth.core.model.Win
import app.hearth.core.model.Topic
import app.hearth.core.model.newId
import app.hearth.core.model.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * The weekoverleg's notes: Wat ging goed (WIN, with text), how the week went (WEEK, on the weather scale) and the
 * house-rule check (RULE_CHECK). FEELING rows from v0.6 (per person) and the FLOW / STRAIN meters before v0.20 are
 * kept in old meetings but no longer read.
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
    /** WEEK: what Hearth suggested (same codes as [value]). */
    @ColumnInfo(defaultValue = "NULL") val suggested: Int? = null,
    /** WIN: the topic it was named under ([Topic] name); null for a win in general. */
    @ColumnInfo(defaultValue = "NULL") val topic: String? = null,
) {
    fun toWin() = Win(id, text, memberId, huddleId, createdAt, topic?.let { t -> Topic.entries.firstOrNull { it.name == t } })

    /** WEEK rows: [text] is "agreed" or "neutral" (no agreement, no weather). */
    fun toWeek() = WeekRating(if (text == AGREED) scoreOf(value) else null, suggested?.let(::scoreOf), text == AGREED, huddleId)
    /** RULE_CHECK rows: [text] is the rule's id; [value] a score (see [scoreOf]). */
    fun toRuleCheck() = RuleCheck(text, scoreOf(value), huddleId)

    companion object {
        const val WIN = "WIN"
        const val FEELING = "FEELING"
        const val RULE_CHECK = "RULE_CHECK"
        /** A habit's weather in one meeting: [text] is the habit's id, [value] as for RULE_CHECK. */
        const val HABIT_CHECK = "HABIT_CHECK"
        const val WEEK = "WEEK"
        const val AGREED = "agreed"

        /**
         * A score (0–5) is stored as 10 + score. Rows from before the scale hold the weather: 1 = loopt vanzelf,
         * 2 = vraagt moeite, 0 = te zwaar (older rule checks: 1 worked, 0 not); they read as that weather's score.
         */
        fun scoreOf(value: Int?): Int = when (value) {
            in SCORE_BASE..SCORE_BASE + SCORE_MAX -> value!! - SCORE_BASE
            1 -> Ease.EASY.score
            2 -> Ease.EFFORT.score
            else -> Ease.HARD.score
        }
        fun codeOf(score: Int) = SCORE_BASE + score.coerceIn(0, SCORE_MAX)
        private const val SCORE_BASE = 10
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

    /** Every rating of every house rule, oldest first: the last one per rule is how it goes now. */
    @Query("SELECT * FROM reflection WHERE kind = 'RULE_CHECK' ORDER BY updatedAt")
    fun observeAllRuleChecks(): Flow<List<ReflectionEntity>>

    @Query("SELECT * FROM reflection WHERE kind = 'RULE_CHECK' ORDER BY updatedAt")
    suspend fun allRuleChecks(): List<ReflectionEntity>

    /** Every habit's score, meeting by meeting, oldest first. */
    @Query("SELECT * FROM reflection WHERE kind = 'HABIT_CHECK' ORDER BY createdAt")
    fun observeAllHabitChecks(): Flow<List<ReflectionEntity>>

    @Query("SELECT * FROM reflection WHERE kind = 'HABIT_CHECK' AND text = :itemId ORDER BY createdAt")
    suspend fun habitChecksOf(itemId: String): List<ReflectionEntity>

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

    /** Words the recognizer got wrong, fixed by the family (only in the meeting that's still open). */
    @Query("UPDATE transcript_line SET text = :text, updatedAt = :now WHERE id = :id AND huddleId IS NULL")
    suspend fun fixLine(id: String, text: String, now: Long): Int

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

    suspend fun addWin(text: String, memberId: String?, topic: Topic? = null): Win {
        val now = clock()
        val entity = ReflectionEntity(newId(), ReflectionEntity.WIN, text.trim(), null, memberId, null, now, now, topic = topic?.name)
        dao.putReflection(entity)
        return entity.toWin()
    }

    suspend fun deleteWin(id: String) = dao.deleteReflections(listOf(id))

    /** How this week went, once the family said it (null: not yet). */
    val week: Flow<WeekRating?> = dao.observeCurrentReflections().map { list -> list.firstOrNull { it.kind == ReflectionEntity.WEEK }?.toWeek() }
    val ruleChecks: Flow<List<RuleCheck>> = dao.observeCurrentReflections().map { list -> list.filter { it.kind == ReflectionEntity.RULE_CHECK }.map { it.toRuleCheck() } }

    /** How each house rule goes, by rule id: this week's score, or the last one from an earlier meeting. */
    val ruleScores: Flow<Map<String, Int>> = dao.observeAllRuleChecks().map { list -> list.associate { it.text to it.toRuleCheck().score } }
    /** Each house rule's score per meeting, oldest first, by rule id. */
    val ruleHistory: Flow<Map<String, List<Int>>> = dao.observeAllRuleChecks().map { list ->
        list.groupBy { it.text }.mapValues { (_, rows) -> rows.map { ReflectionEntity.scoreOf(it.value) } }
    }
    /** This meeting's score per habit, by habit id (not rated yet: missing). */
    val habitChecks: Flow<Map<String, Int>> = dao.observeCurrentReflections().map { list ->
        list.filter { it.kind == ReflectionEntity.HABIT_CHECK }.associate { it.text to ReflectionEntity.scoreOf(it.value) }
    }

    /** Each habit's score per meeting, oldest first, by habit id: what the habit coach reads. */
    val habitHistory: Flow<Map<String, List<Int>>> = dao.observeAllHabitChecks().map { list ->
        list.groupBy { it.text }.mapValues { (_, rows) -> rows.map { ReflectionEntity.scoreOf(it.value) } }
    }

    /** How each house rule goes now, by rule id: its last score. */
    suspend fun ruleScores(): Map<String, Int> = dao.allRuleChecks().associate { it.text to it.toRuleCheck().score }

    suspend fun habitHistory(itemId: String): List<Int> = dao.habitChecksOf(itemId).map { ReflectionEntity.scoreOf(it.value) }
    suspend fun currentWeek(): WeekRating? = dao.currentReflections().firstOrNull { it.kind == ReflectionEntity.WEEK }?.toWeek()
    suspend fun currentRuleChecks(): List<RuleCheck> = dao.currentReflections().filter { it.kind == ReflectionEntity.RULE_CHECK }.map { it.toRuleCheck() }
    suspend fun ruleChecksOf(huddleId: String): List<RuleCheck> = dao.reflectionsOf(huddleId).filter { it.kind == ReflectionEntity.RULE_CHECK }.map { it.toRuleCheck() }
    suspend fun weekOf(huddleId: String): WeekRating? = dao.reflectionsOf(huddleId).firstOrNull { it.kind == ReflectionEntity.WEEK }?.toWeek()

    /** How the week went, agreed together (0–5); null [score] = no agreement. Saying it again replaces it. */
    suspend fun setWeek(score: Int?, suggested: Int?): WeekRating = db.withWriteTransaction {
        val now = clock()
        val old = dao.currentReflections().firstOrNull { it.kind == ReflectionEntity.WEEK }
        val entity = ReflectionEntity(
            old?.id ?: newId(), ReflectionEntity.WEEK, if (score == null) "neutral" else ReflectionEntity.AGREED,
            score?.let(ReflectionEntity::codeOf), null, null, old?.createdAt ?: now, now, suggested?.let(ReflectionEntity::codeOf),
        )
        dao.putReflection(entity)
        entity.toWeek()
    }

    /** How a house rule went this week; saying it again replaces it. */
    suspend fun setRuleCheck(ruleId: String, score: Int): RuleCheck = db.withWriteTransaction {
        val now = clock()
        val old = dao.currentReflections().firstOrNull { it.kind == ReflectionEntity.RULE_CHECK && it.text == ruleId }
        val entity = ReflectionEntity(old?.id ?: newId(), ReflectionEntity.RULE_CHECK, ruleId, ReflectionEntity.codeOf(score), null, null, old?.createdAt ?: now, now)
        dao.putReflection(entity)
        entity.toRuleCheck()
    }

    /** How a habit went since the last meeting; saying it again replaces it, so each meeting keeps one. */
    suspend fun setHabitCheck(itemId: String, score: Int): Unit = db.withWriteTransaction {
        val now = clock()
        val old = dao.currentReflections().firstOrNull { it.kind == ReflectionEntity.HABIT_CHECK && it.text == itemId }
        dao.putReflection(ReflectionEntity(old?.id ?: newId(), ReflectionEntity.HABIT_CHECK, itemId, ReflectionEntity.codeOf(score), null, null, old?.createdAt ?: now, now))
    }

    suspend fun addLine(text: String): TranscriptLine {
        val now = clock()
        val entity = TranscriptLineEntity(newId(), text.trim(), null, now, now)
        dao.insertLine(entity)
        return entity.toModel()
    }

    suspend fun clearTranscript() = dao.clearCurrentTranscript()

    /** False when the line is gone or belongs to a meeting that already ended. */
    suspend fun fixLine(id: String, text: String): Boolean = dao.fixLine(id, text.trim(), clock()) > 0

    suspend fun markSorted(ids: List<String>) {
        if (ids.isNotEmpty()) dao.markSorted(ids, clock())
    }

    /**
     * Ends the meeting: records it with its [summary], hands it this week's wins, its weather, rule checks and transcript,
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
