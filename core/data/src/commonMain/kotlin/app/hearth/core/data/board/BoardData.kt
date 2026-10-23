package app.hearth.core.data.board

import kotlinx.serialization.Serializable
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.withWriteTransaction
import app.hearth.core.data.HearthDatabase
import app.hearth.core.model.BoardItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.BoardStatus
import app.hearth.core.model.GoalScope
import app.hearth.core.model.MEMBER_COLORS
import app.hearth.core.model.Member
import app.hearth.core.model.newId
import app.hearth.core.model.normalizeName
import app.hearth.core.model.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "member", indices = [Index("normalizedName", unique = true)])
@Serializable
data class MemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val color: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toModel() = Member(id, name, color, createdAt)
}

@Entity(tableName = "board_item", indices = [Index("kind", "status")])
@Serializable
data class BoardEntity(
    @PrimaryKey val id: String,
    val kind: BoardKind,
    val text: String,
    val normalizedText: String,
    val status: BoardStatus,
    val memberId: String?,
    val scope: GoalScope?,
    val nextStep: String?,
    val linkedId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val closedAt: Long?,
) {
    fun toModel() = BoardItem(id, kind, text, status, memberId, scope, nextStep, linkedId, createdAt, closedAt)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM member ORDER BY createdAt")
    fun observeAll(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM member ORDER BY createdAt")
    suspend fun all(): List<MemberEntity>

    @Insert
    suspend fun insert(member: MemberEntity)

    @Query("DELETE FROM member WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BoardDao {
    /** Everything on the board; closed frictions and goals stay visible as wins. */
    @Query("SELECT * FROM board_item ORDER BY createdAt")
    fun observeAll(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM board_item ORDER BY createdAt")
    suspend fun all(): List<BoardEntity>

    @Query("SELECT * FROM board_item WHERE kind = :kind AND status = 'OPEN' AND normalizedText = :normalizedText LIMIT 1")
    suspend fun findOpen(kind: BoardKind, normalizedText: String): BoardEntity?

    @Insert
    suspend fun insert(item: BoardEntity)

    @Query("UPDATE board_item SET status = :status, linkedId = :linkedId, closedAt = :closedAt, updatedAt = :now WHERE id = :id")
    suspend fun close(id: String, status: BoardStatus, linkedId: String?, closedAt: Long?, now: Long)

    @Query("UPDATE board_item SET nextStep = :nextStep, linkedId = :linkedId, updatedAt = :now WHERE id = :id")
    suspend fun setNextStep(id: String, nextStep: String?, linkedId: String?, now: Long)

    @Query("UPDATE board_item SET memberId = NULL, scope = CASE WHEN scope = 'PERSONAL' THEN 'FAMILY' ELSE scope END WHERE memberId = :memberId")
    suspend fun forgetMember(memberId: String)

    @Query("DELETE FROM board_item WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}

/** Storage for the Werkbord and the household's members. Writes come from tools only. */
class BoardRepository(
    private val db: HearthDatabase,
    private val clock: () -> Long = ::nowMillis,
) {
    private val members = db.memberDao()
    private val board = db.boardDao()

    val memberList: Flow<List<Member>> = members.observeAll().map { list -> list.map { it.toModel() } }
    val items: Flow<List<BoardItem>> = board.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun allMembers(): List<Member> = members.all().map { it.toModel() }
    suspend fun allItems(): List<BoardItem> = board.all().map { it.toModel() }

    /** Adds a person, or returns the one with that name. */
    suspend fun addMember(name: String): Pair<Member, Boolean> = db.withWriteTransaction {
        val key = normalizeName(name)
        val all = members.all()
        all.firstOrNull { it.normalizedName == key }?.let { return@withWriteTransaction it.toModel() to false }
        val now = clock()
        val color = MEMBER_COLORS[all.size % MEMBER_COLORS.size].toInt()
        val entity = MemberEntity(newId(), name.trim(), key, color, now, now)
        members.insert(entity)
        entity.toModel() to true
    }

    /** Their goals become family goals; nothing else is lost. */
    suspend fun removeMember(id: String) = db.withWriteTransaction {
        board.forgetMember(id)
        members.delete(id)
    }

    /** Adds to the board, or returns the identical open item. */
    suspend fun add(
        kind: BoardKind,
        text: String,
        memberId: String? = null,
        scope: GoalScope? = null,
        nextStep: String? = null,
    ): Pair<BoardItem, Boolean> = db.withWriteTransaction {
        val key = normalizeName(text)
        board.findOpen(kind, key)?.let { return@withWriteTransaction it.toModel() to false }
        val now = clock()
        val entity = BoardEntity(newId(), kind, text.trim(), key, BoardStatus.OPEN, memberId, scope, nextStep, null, now, now, null)
        board.insert(entity)
        entity.toModel() to true
    }

    suspend fun close(id: String, status: BoardStatus, linkedId: String? = null) {
        val now = clock()
        board.close(id, status, linkedId, if (status == BoardStatus.OPEN) null else now, now)
    }

    suspend fun setNextStep(id: String, nextStep: String?, linkedId: String?) = board.setNextStep(id, nextStep, linkedId, clock())

    suspend fun delete(ids: List<String>) {
        if (ids.isNotEmpty()) board.delete(ids)
    }
}
