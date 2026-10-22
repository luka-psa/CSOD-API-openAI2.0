package app.hearth.core.tools

import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.tools.food.AddShoppingItemTool
import app.hearth.core.tools.food.ListShoppingItemsTool
import app.hearth.core.tools.food.MarkBoughtTool
import app.hearth.core.tools.food.UnmarkBoughtTool

/** Every tool in the app, built once and shared by UI, chat and MCP. */
class HearthTools(shopping: ShoppingRepository) {
    val addShoppingItem = AddShoppingItemTool(shopping)
    val listShoppingItems = ListShoppingItemsTool(shopping)
    val markBought = MarkBoughtTool(shopping)
    val unmarkBought = UnmarkBoughtTool(shopping)

    val registry = ToolRegistry(listOf(addShoppingItem, listShoppingItems, markBought, unmarkBought))
}
