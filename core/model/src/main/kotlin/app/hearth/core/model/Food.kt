package app.hearth.core.model

import kotlinx.serialization.Serializable

enum class ShopKind { SUPERMARKET, PHARMACY, BAKERY, DIY, FURNITURE, OTHER }

@Serializable
data class Shop(
    val id: String,
    val name: String,
    val kind: ShopKind,
    val sortOrder: Int,
)

/**
 * One line on the shared shopping list. A restock has [stockItemId]; a one-off buy does not.
 */
@Serializable
data class ShoppingItem(
    val id: String,
    val name: String,
    val quantity: String? = null,
    val note: String? = null,
    val shopId: String,
    val stockItemId: String? = null,
    val bought: Boolean = false,
    val boughtAt: Long? = null,
    val addedById: String? = null,
) {
    val isOneOff: Boolean get() = stockItemId == null
}
