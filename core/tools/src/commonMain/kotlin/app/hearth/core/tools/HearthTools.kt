package app.hearth.core.tools

import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.inbox.InboxRepository
import app.hearth.core.tools.food.AddShoppingItemTool
import app.hearth.core.tools.food.ListShoppingItemsTool
import app.hearth.core.tools.food.MarkBoughtTool
import app.hearth.core.tools.food.RemoveShoppingItemTool
import app.hearth.core.tools.food.UnmarkBoughtTool
import app.hearth.core.tools.inbox.ArchiveInboxItemTool
import app.hearth.core.tools.inbox.CaptureToInboxTool
import app.hearth.core.tools.inbox.ListInboxTool

/** Every tool in the app, built once and shared by UI, chat and MCP. */
class HearthTools(shopping: ShoppingRepository, inbox: InboxRepository) {
    val addShoppingItem = AddShoppingItemTool(shopping)
    val listShoppingItems = ListShoppingItemsTool(shopping)
    val markBought = MarkBoughtTool(shopping)
    val unmarkBought = UnmarkBoughtTool(shopping)
    val removeShoppingItem = RemoveShoppingItemTool(shopping)

    val captureToInbox = CaptureToInboxTool(inbox)
    val listInbox = ListInboxTool(inbox)
    val archiveInboxItem = ArchiveInboxItemTool(inbox)

    val registry = ToolRegistry(
        listOf(
            addShoppingItem, listShoppingItems, markBought, unmarkBought, removeShoppingItem,
            captureToInbox, listInbox, archiveInboxItem,
        ),
    )
}
