package app.hearth.core.data.food

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.hearth.core.model.Shop
import app.hearth.core.model.ShopKind
import app.hearth.core.model.ShoppingItem

@Entity(tableName = "shop", indices = [Index("normalizedName", unique = true)])
data class ShopEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val kind: ShopKind,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toModel() = Shop(id = id, name = name, kind = kind, sortOrder = sortOrder)
}

@Entity(
    tableName = "shopping_item",
    foreignKeys = [ForeignKey(entity = ShopEntity::class, parentColumns = ["id"], childColumns = ["shopId"])],
    indices = [Index("shopId"), Index("normalizedName"), Index("bought")],
)
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val quantity: String?,
    val note: String?,
    val shopId: String,
    /** Set for restocks; null for one-off buys. Linked to StockItem from slice 2. */
    val stockItemId: String?,
    val bought: Boolean,
    val boughtAt: Long?,
    val addedById: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toModel() = ShoppingItem(
        id = id,
        name = name,
        quantity = quantity,
        note = note,
        shopId = shopId,
        stockItemId = stockItemId,
        bought = bought,
        boughtAt = boughtAt,
        addedById = addedById,
    )
}
