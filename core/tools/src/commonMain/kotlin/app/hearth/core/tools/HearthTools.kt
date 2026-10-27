package app.hearth.core.tools

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.data.backup.BackupRepository
import app.hearth.core.tools.system.BackupNowTool
import app.hearth.core.tools.system.BackupStore
import app.hearth.core.tools.system.CalendarApi
import app.hearth.core.tools.system.ListBackupsTool
import app.hearth.core.tools.system.NoGoogle
import app.hearth.core.tools.system.RestoreBackupTool
import app.hearth.core.tools.system.SaveConnectionTool
import app.hearth.core.tools.system.SetGoogleSyncTool
import app.hearth.core.tools.system.SyncCalendarTool
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.tools.board.AddIdeaTool
import app.hearth.core.tools.week.AddTranscriptLineTool
import app.hearth.core.tools.week.AddWinTool
import app.hearth.core.tools.week.ClearTranscriptTool
import app.hearth.core.tools.week.FixTranscriptLineTool
import app.hearth.core.tools.week.ReadConversationTool
import app.hearth.core.tools.week.FinishWeekoverlegTool
import app.hearth.core.tools.week.ListHuddlesTool
import app.hearth.core.tools.week.MarkTranscriptSortedTool
import app.hearth.core.tools.week.RemoveWinTool
import app.hearth.core.tools.week.CheckHouseRuleTool
import app.hearth.core.tools.week.RateWeekTool
import app.hearth.core.tools.week.SetWeekMetersTool
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
import app.hearth.core.tools.backlog.RateHabitTool
import app.hearth.core.tools.backlog.SetSmallStepTool
import app.hearth.core.tools.backlog.SetAnchorTool
import app.hearth.core.tools.backlog.SetRoutineScheduleTool
import app.hearth.core.tools.backlog.AddRoutineTool
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
    /** The Weekoverleg's notes (wins, the week's weather, house-rule and habit checks, Luister mee, summaries). */
    meeting: MeetingRepository,
    /** The household as one file; with [backupStore] and [calendarApi] for the opt-in Google features. */
    backups: BackupRepository? = null,
    backupStore: BackupStore = NoGoogle,
    calendarApi: CalendarApi = NoGoogle,
    /** Web pages for importing a recipe from a link. */
    pages: app.hearth.core.tools.week.PageFetcher = app.hearth.core.tools.week.NoPages,
    /** The household on more devices: the merge (on this device) and where devices meet (the relay). */
    sync: app.hearth.core.data.sync.SyncRepository? = null,
    syncCloud: app.hearth.core.tools.system.SyncCloud = app.hearth.core.tools.system.NoSyncCloud,
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
    val setRoutineSchedule = SetRoutineScheduleTool(backlog)
    val markUrgent = MarkUrgentTool(backlog)
    val waitForReply = WaitForReplyTool(backlog)
    val moveBacklogItem = MoveBacklogItemTool(backlog, board)
    val addProjectStep = AddProjectStepTool(backlog)
    val rateHabit = RateHabitTool(backlog, meeting, board)
    val setSmallStep = SetSmallStepTool(backlog)
    val setHabitPairing = app.hearth.core.tools.backlog.SetHabitPairingTool(backlog)
    val reviewHabit = app.hearth.core.tools.backlog.ReviewHabitTool(backlog, meeting, board)
    val setAnchor = SetAnchorTool(backlog)
    val addRoutine = AddRoutineTool(backlog)
    val moveStep = app.hearth.core.tools.backlog.MoveStepTool(backlog)

    val addMember = AddMemberTool(board)
    val removeMember = RemoveMemberTool(board)
    val addHouseRule = AddHouseRuleTool(board)
    val logFriction = LogFrictionTool(board)
    val addGoal = AddGoalTool(board)
    val addIdea = AddIdeaTool(board)
    val listBoard = ListBoardTool(board)
    val resolveFriction = ResolveFrictionTool(board, backlog, addRoutine)
    val markFriction = app.hearth.core.tools.board.MarkFrictionTool(board)
    val setGoalStep = SetGoalStepTool(board, backlog, addRoutine)
    val achieveGoal = AchieveGoalTool(board)
    val removeBoardItem = RemoveBoardItemTool(board)

    val planMeal = PlanMealTool(week, addShoppingItem)
    val listMealPlan = ListMealPlanTool(week)
    val removeMeal = RemoveMealTool(week)
    val weekoverlegAgenda = WeekoverlegAgendaTool(week, backlog, board, shopping, meeting, settings)
    val finishWeekoverleg = FinishWeekoverlegTool(week, meeting, backlog, board, settings)
    val addWin = AddWinTool(meeting, board)
    val removeWin = RemoveWinTool(meeting)
    val rateWeek = RateWeekTool(meeting)
    val setWeekMeters = SetWeekMetersTool(settings)
    val checkHouseRule = CheckHouseRuleTool(meeting, board)
    val addTranscriptLine = AddTranscriptLineTool(meeting)
    val clearTranscript = ClearTranscriptTool(meeting)
    val markTranscriptSorted = MarkTranscriptSortedTool(meeting)
    val fixTranscriptLine = FixTranscriptLineTool(meeting)
    val readConversation = ReadConversationTool(meeting)
    val listHuddles = ListHuddlesTool(meeting)
    val saveRecipe = SaveRecipeTool(week)
    val importRecipe = app.hearth.core.tools.week.ImportRecipeTool(week, pages)
    val listRecipes = ListRecipesTool(week)
    val removeRecipe = RemoveRecipeTool(week)
    val setRecipePhoto = SetRecipePhotoTool(week)

    val saveConnection = SaveConnectionTool(settings)
    val setGoogleSync = SetGoogleSyncTool(settings)
    val backupNow = backups?.let { BackupNowTool(it, backupStore, settings) }
    val listBackups = ListBackupsTool(backupStore)
    val restoreBackup = backups?.let { RestoreBackupTool(it, backupStore) }
    val syncCalendar = SyncCalendarTool(backlog, calendarApi, settings)
    val loadExamples = app.hearth.core.tools.system.LoadExamplesTool(this, settings)
    val setLanguage = app.hearth.core.tools.system.SetLanguageTool(settings)
    val setSmartListening = app.hearth.core.tools.system.SetSmartListeningTool(settings)
    val setInterpreter = app.hearth.core.tools.system.SetInterpreterTool(settings)

    val syncEngine = sync?.let { app.hearth.core.tools.system.SyncEngine(it, settings, syncCloud) }
    val syncNow = syncEngine?.let { app.hearth.core.tools.system.SyncNowTool(it) }
    val startSync = syncEngine?.let { app.hearth.core.tools.system.StartSyncTool(it, syncCloud) }
    val shareSync = syncEngine?.let { app.hearth.core.tools.system.ShareSyncTool(it, syncCloud) }
    val joinSync = syncEngine?.let { app.hearth.core.tools.system.JoinSyncTool(it, syncCloud) }
    val stopSync = syncEngine?.let { app.hearth.core.tools.system.StopSyncTool(it) }

    val registry = ToolRegistry(
        listOf(
            addShoppingItem, listShoppingItems, markBought, unmarkBought, removeShoppingItem, clearBoughtItems,
            listStock, addStockItem, addStorageSpot, setStockState, removeStockItem, moveStockItem, removeStorageSpot,
            addToBacklog, listBacklog, planBacklogItem, completeBacklogItem, reopenBacklogItem, removeBacklogItem,
            setRoutineInterval, setRoutineSchedule, markUrgent, waitForReply, moveBacklogItem, addProjectStep, rateHabit, setSmallStep, setAnchor, addRoutine, moveStep,
            setHabitPairing, reviewHabit,
            addMember, removeMember, addHouseRule, logFriction, addGoal, listBoard, resolveFriction, markFriction, setGoalStep,
            achieveGoal, removeBoardItem,
            planMeal, listMealPlan, removeMeal, weekoverlegAgenda, finishWeekoverleg,
            addWin, removeWin, rateWeek, setWeekMeters, checkHouseRule, addTranscriptLine, markTranscriptSorted, fixTranscriptLine, readConversation, clearTranscript, listHuddles, addIdea,
            saveRecipe, importRecipe, listRecipes, removeRecipe, setRecipePhoto,
            saveConnection, setGoogleSync, listBackups, syncCalendar, loadExamples, setLanguage, setSmartListening, setInterpreter,
        ) + listOfNotNull(backupNow, restoreBackup, syncNow, startSync, shareSync, joinSync, stopSync),
    )
}
