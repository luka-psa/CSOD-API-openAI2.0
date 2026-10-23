package app.hearth.core.tools

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.tools.system.SaveConnectionTool
import app.hearth.core.tools.week.FinishWeekoverlegTool
import app.hearth.core.tools.week.ListMealPlanTool
import app.hearth.core.tools.week.PlanMealTool
import app.hearth.core.tools.week.ListRecipesTool
import app.hearth.core.tools.week.RemoveMealTool
import app.hearth.core.tools.week.RemoveRecipeTool
import app.hearth.core.tools.week.SaveRecipeTool
import app.hearth.core.tools.week.SetRecipePhotoTool
import app.hearth.core.tools.week.WeekoverlegAgendaTool
import app.hearth.core.tools.food.AddStockItemTool
import app.hearth.core.tools.food.AddStorageSpotTool
import app.hearth.core.tools.food.ClearBoughtItemsTool
import app.hearth.core.tools.food.ListStockTool
import app.hearth.core.tools.food.MoveStockItemTool
import app.hearth.core.tools.food.RemoveStorageSpotTool
import app.hearth.core.tools.food.RemoveStockItemTool
import app.hearth.core.tools.food.SetStockStateTool
import app.hearth.core.tools.board.AchieveGoalTool
import app.hearth.core.tools.board.AddGoalTool
import app.hearth.core.tools.board.AddHouseRuleTool
import app.hearth.core.tools.board.AddMemberTool
import app.hearth.core.tools.board.ListBoardTool
import app.hearth.core.tools.board.LogFrictionTool
import app.hearth.core.tools.board.RemoveBoardItemTool
import app.hearth.core.tools.board.RemoveMemberTool
import app.hearth.core.tools.board.ResolveFrictionTool
import app.hearth.core.tools.board.SetGoalStepTool
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
class HearthTools(
    shopping: ShoppingRepository,
    backlog: BacklogRepository,
    board: BoardRepository,
    stock: StockRepository,
    week: WeekRepository,
    settings: SettingsRepository,
) {
    val addShoppingItem = AddShoppingItemTool(shopping, stock)
    val listShoppingItems = ListShoppingItemsTool(shopping)
    val markBought = MarkBoughtTool(shopping, stock)
    val unmarkBought = UnmarkBoughtTool(shopping)
    val removeShoppingItem = RemoveShoppingItemTool(shopping, stock)
    val clearBoughtItems = ClearBoughtItemsTool(shopping)

    val listStock = ListStockTool(stock)
    val addStockItem = AddStockItemTool(stock, shopping)
    val addStorageSpot = AddStorageSpotTool(stock)
    val setStockState = SetStockStateTool(stock, shopping)
    val removeStockItem = RemoveStockItemTool(stock)
    val moveStockItem = MoveStockItemTool(stock)
    val removeStorageSpot = RemoveStorageSpotTool(stock)

    val addToBacklog = AddToBacklogTool(backlog, board)
    val listBacklog = ListBacklogTool(backlog)
    val planBacklogItem = PlanBacklogItemTool(backlog)
    val completeBacklogItem = CompleteBacklogItemTool(backlog)
    val reopenBacklogItem = ReopenBacklogItemTool(backlog)
    val removeBacklogItem = RemoveBacklogItemTool(backlog)
    val setRoutineInterval = SetRoutineIntervalTool(backlog)
    val markUrgent = MarkUrgentTool(backlog)
    val waitForReply = WaitForReplyTool(backlog)
    val moveBacklogItem = MoveBacklogItemTool(backlog, board)
    val addProjectStep = AddProjectStepTool(backlog)

    val addMember = AddMemberTool(board)
    val removeMember = RemoveMemberTool(board)
    val addHouseRule = AddHouseRuleTool(board)
    val logFriction = LogFrictionTool(board)
    val addGoal = AddGoalTool(board)
    val listBoard = ListBoardTool(board)
    val resolveFriction = ResolveFrictionTool(board, backlog)
    val setGoalStep = SetGoalStepTool(board, backlog)
    val achieveGoal = AchieveGoalTool(board)
    val removeBoardItem = RemoveBoardItemTool(board)

    val planMeal = PlanMealTool(week, addShoppingItem)
    val listMealPlan = ListMealPlanTool(week)
    val removeMeal = RemoveMealTool(week)
    val weekoverlegAgenda = WeekoverlegAgendaTool(week, backlog, board, shopping)
    val finishWeekoverleg = FinishWeekoverlegTool(week)
    val saveRecipe = SaveRecipeTool(week)
    val listRecipes = ListRecipesTool(week)
    val removeRecipe = RemoveRecipeTool(week)
    val setRecipePhoto = SetRecipePhotoTool(week)

    val saveConnection = SaveConnectionTool(settings)

    val registry = ToolRegistry(
        listOf(
            addShoppingItem, listShoppingItems, markBought, unmarkBought, removeShoppingItem, clearBoughtItems,
            listStock, addStockItem, addStorageSpot, setStockState, removeStockItem, moveStockItem, removeStorageSpot,
            addToBacklog, listBacklog, planBacklogItem, completeBacklogItem, reopenBacklogItem, removeBacklogItem,
            setRoutineInterval, markUrgent, waitForReply, moveBacklogItem, addProjectStep,
            addMember, removeMember, addHouseRule, logFriction, addGoal, listBoard, resolveFriction, setGoalStep,
            achieveGoal, removeBoardItem,
            planMeal, listMealPlan, removeMeal, weekoverlegAgenda, finishWeekoverleg,
            saveRecipe, listRecipes, removeRecipe, setRecipePhoto,
            saveConnection,
        ),
    )
}
