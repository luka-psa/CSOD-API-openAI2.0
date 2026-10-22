package app.hearth.core.data.food

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {
    @Query("SELECT * FROM shop ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ShopEntity>>

    @Query("SELECT * FROM shop ORDER BY sortOrder, name")
    suspend fun all(): List<ShopEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM shop")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(shop: ShopEntity)
}

@Dao
interface ShoppingItemDao {
    /** Open items, plus items bought since [boughtSince] so a fresh tick stays visible for a while. */
    @Query(
        """SELECT * FROM shopping_item
           WHERE bought = 0 OR boughtAt >= :boughtSince
           ORDER BY bought, createdAt"""
    )
    fun observeVisible(boughtSince: Long): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_item WHERE bought = 0 ORDER BY createdAt")
    suspend fun open(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_item WHERE bought = 0 AND normalizedName = :normalizedName LIMIT 1")
    suspend fun findOpenByName(normalizedName: String): ShoppingItemEntity?

    @Query("SELECT * FROM shopping_item WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_item WHERE bought = 1 AND normalizedName IN (:names) ORDER BY boughtAt DESC")
    suspend fun boughtByNames(names: List<String>): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_item WHERE bought = 0 AND normalizedName IN (:names)")
    suspend fun openByNames(names: List<String>): List<ShoppingItemEntity>

    @Insert
    suspend fun insert(item: ShoppingItemEntity)

    @Update
    suspend fun update(item: ShoppingItemEntity)

    @Query("UPDATE shopping_item SET bought = :bought, boughtAt = :boughtAt, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setBought(ids: List<String>, bought: Boolean, boughtAt: Long?, now: Long)
}
