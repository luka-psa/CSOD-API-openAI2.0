package app.hearth.core.data

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.RenameColumn
import androidx.room3.RenameTable
import androidx.room3.migration.AutoMigrationSpec
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import app.hearth.core.data.food.ShopDao
import app.hearth.core.data.food.ShopEntity
import app.hearth.core.data.food.ShoppingItemDao
import app.hearth.core.data.food.ShoppingItemEntity
import app.hearth.core.data.backlog.BacklogDao
import app.hearth.core.data.backlog.BacklogEntity
import app.hearth.core.data.board.BoardDao
import app.hearth.core.data.board.BoardEntity
import app.hearth.core.data.board.MemberDao
import app.hearth.core.data.board.MemberEntity
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The one household database. Platform files open it: SQLite on Android (`openHearthDatabase(context)`),
 * SQLite-in-a-web-worker in the browser (`openHearthDatabase()`), in-memory on the JVM for tests.
 */
@Database(
    entities = [ShopEntity::class, ShoppingItemEntity::class, BacklogEntity::class, MemberEntity::class, BoardEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = InboxToBacklog::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = RulesToBoard::class),
    ],
)
@ConstructedBy(HearthDatabaseConstructor::class)
abstract class HearthDatabase : RoomDatabase() {
    abstract fun shopDao(): ShopDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun backlogDao(): BacklogDao
    abstract fun memberDao(): MemberDao
    abstract fun boardDao(): BoardDao
}

/** v5: house rules and frictions leave the backlog for the new Werkbord. */
class RulesToBoard : AutoMigrationSpec {
    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL(
            """INSERT INTO board_item (id, kind, text, normalizedText, status, memberId, scope, nextStep, linkedId, createdAt, updatedAt, closedAt)
               SELECT id, CASE section WHEN 'NOTE' THEN 'RULE' ELSE 'FRICTION' END, text, normalizedText, 'OPEN',
                      NULL, NULL, NULL, NULL, createdAt, updatedAt, NULL
               FROM backlog_item WHERE done = 0 AND parentId IS NULL AND section IN ('NOTE', 'FRICTION')""",
        )
        connection.execSQL("DELETE FROM backlog_item WHERE done = 0 AND parentId IS NULL AND section IN ('NOTE', 'FRICTION')")
    }
}

/** v3: the inbox grew into the plannable backlog. */
@RenameTable(fromTableName = "inbox_item", toTableName = "backlog_item")
@RenameColumn(tableName = "inbox_item", fromColumnName = "archived", toColumnName = "done")
@RenameColumn(tableName = "inbox_item", fromColumnName = "archivedAt", toColumnName = "doneAt")
class InboxToBacklog : AutoMigrationSpec

// Room generates the actual implementations for each platform.
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object HearthDatabaseConstructor : RoomDatabaseConstructor<HearthDatabase> {
    override fun initialize(): HearthDatabase
}

internal const val DATABASE_NAME = "hearth.db"
