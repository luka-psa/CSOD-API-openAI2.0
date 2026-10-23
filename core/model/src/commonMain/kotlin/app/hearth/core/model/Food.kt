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

/** How much is left. LOW and OUT put the item on the shopping list; buying it brings it back IN_STOCK. */
enum class StockState { IN_STOCK, LOW, OUT }

/** A room or topic where things are kept: Keuken, Badkamer, Wasplaats, Medicijnkast. */
@Serializable
data class StorageSpot(val id: String, val name: String, val sortOrder: Int)

/** Something the household always wants in the house. */
@Serializable
data class StockItem(
    val id: String,
    val name: String,
    val spotId: String,
    val state: StockState = StockState.IN_STOCK,
    /** Where it's usually bought; null = the default shop. */
    val shopId: String? = null,
)

/** What we eat on one day (the evening meal); its ingredients went onto the shopping list. */
@Serializable
data class Meal(val id: String, val date: String, val dish: String, val ingredients: List<String> = emptyList())

/**
 * A dish we know: saved on purpose as a recipe (with how we made it and maybe a photo), or remembered
 * automatically the first time it was planned. [key] is its normalized name.
 */
@Serializable
data class Recipe(
    val key: String,
    val name: String,
    val ingredients: List<String> = emptyList(),
    /** How we made it, in our own words. */
    val notes: String? = null,
    val hasPhoto: Boolean = false,
    /** Saved on purpose (shows under Recepten), not just remembered from a plan. */
    val isRecipe: Boolean = false,
    /** ISO date it was last on the menu, or null. */
    val lastUsedOn: String? = null,
)
