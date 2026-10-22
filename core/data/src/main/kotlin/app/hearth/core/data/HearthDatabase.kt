package app.hearth.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.hearth.core.data.food.ShopDao
import app.hearth.core.data.food.ShopEntity
import app.hearth.core.data.food.ShoppingItemDao
import app.hearth.core.data.food.ShoppingItemEntity

@Database(
    entities = [ShopEntity::class, ShoppingItemEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class HearthDatabase : RoomDatabase() {
    abstract fun shopDao(): ShopDao
    abstract fun shoppingItemDao(): ShoppingItemDao

    companion object {
        fun open(context: Context): HearthDatabase =
            Room.databaseBuilder(context, HearthDatabase::class.java, "hearth.db").build()

        fun inMemory(context: Context): HearthDatabase =
            Room.inMemoryDatabaseBuilder(context, HearthDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
