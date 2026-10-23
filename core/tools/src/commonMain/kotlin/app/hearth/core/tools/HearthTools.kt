package app.hearth.core.tools

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.tools.backlog.AddProjectStepTool
import app.hearth.core.tools.backlog.AddToBacklogTool
import app.hearth.core.tools.backlog.CompleteBacklogItemTool
import app.hearth.core.tools.backlog.ListBacklogTool
import app.hearth.core.tools.backlog.MarkUrgentTool
import app.hearth.core.tools.backlog.MoveBacklogItemTool
import app.hearth.core.tools.backlog.PlanBacklogItemTool
import app.hearth.core.tools.backlog.RemoveBacklogItemTool
import app.hearth.core.tools.backlog.ReopenBacklogItemTool
import app.hearth.core.tools.backlog.SetRoutineIntervalTool
import app.hearth.core.tools.backlog.WaitForReplyTool
import app.hearth.core.tools.food.AddShoppingItemTool
import app.hearth.core.tools.food.ListShoppingItemsTool
import app.hearth.core.tools.food.MarkBoughtTool
import app.hearth.core.tools.food.RemoveShoppingItemTool
import app.hearth.core.tools.food.UnmarkBoughtTool

/** Every tool in the app, built once and shared by UI, chat and MCP. */
class HearthTools(shopping: ShoppingRepository, backlog: BacklogRepository) {
    val addShoppingItem = AddShoppingItemTool(shopping)
    val listShoppingItems = ListShoppingItemsTool(shopping)
    val markBought = MarkBoughtTool(shopping)
    val unmarkBought = UnmarkBoughtTool(shopping)
    val removeShoppingItem = RemoveShoppingItemTool(shopping)

    val addToBacklog = AddToBacklogTool(backlog)
    val listBacklog = ListBacklogTool(backlog)
    val planBacklogItem = PlanBacklogItemTool(backlog)
    val completeBacklogItem = CompleteBacklogItemTool(backlog)
    val reopenBacklogItem = ReopenBacklogItemTool(backlog)
    val removeBacklogItem = RemoveBacklogItemTool(backlog)
    val setRoutineInterval = SetRoutineIntervalTool(backlog)
    val markUrgent = MarkUrgentTool(backlog)
    val waitForReply = WaitForReplyTool(backlog)
    val moveBacklogItem = MoveBacklogItemTool(backlog)
    val addProjectStep = AddProjectStepTool(backlog)

    val registry = ToolRegistry(
        listOf(
            addShoppingItem, listShoppingItems, markBought, unmarkBought, removeShoppingItem,
            addToBacklog, listBacklog, planBacklogItem, completeBacklogItem, reopenBacklogItem, removeBacklogItem,
            setRoutineInterval, markUrgent, waitForReply, moveBacklogItem, addProjectStep,
        ),
    )
}
