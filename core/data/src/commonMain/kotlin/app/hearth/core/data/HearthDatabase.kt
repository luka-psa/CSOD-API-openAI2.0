package app.hearth.core.data

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import app.hearth.core.data.food.ShopDao
import app.hearth.core.data.food.ShopEntity
import app.hearth.core.data.food.ShoppingItemDao
import app.hearth.core.data.food.ShoppingItemEntity
import app.hearth.core.data.inbox.InboxDao
import app.hearth.core.data.inbox.InboxEntity

/**
 * The one household database. Platform files open it: SQLite on Android (`openHearthDatabase(context)`),
 * SQLite-in-a-web-worker in the browser (`openHearthDatabase()`), in-memory on the JVM for tests.
 */
@Database(
    entities = [ShopEntity::class, ShoppingItemEntity::class, InboxEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@ConstructedBy(HearthDatabaseConstructor::class)
abstract class HearthDatabase : RoomDatabase() {
    abstract fun shopDao(): ShopDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun inboxDao(): InboxDao
}

// Room generates the actual implementations for each platform.
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object HearthDatabaseConstructor : RoomDatabaseConstructor<HearthDatabase> {
    override fun initialize(): HearthDatabase
}

internal const val DATABASE_NAME = "hearth.db"
