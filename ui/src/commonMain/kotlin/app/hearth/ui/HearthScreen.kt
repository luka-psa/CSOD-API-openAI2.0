package app.hearth.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.BoardKind
import app.hearth.core.model.Lang
import app.hearth.core.model.dontForget
import app.hearth.core.model.huddleDue
import app.hearth.ui.resources.Res
import app.hearth.core.model.TaskKind
import app.hearth.ui.resources.delete_confirm
import app.hearth.ui.resources.delete_confirm_body
import app.hearth.ui.resources.delete_confirm_project
import app.hearth.ui.resources.nav_board
import app.hearth.ui.resources.nav_connect
import app.hearth.ui.resources.mcp_asks
import app.hearth.ui.resources.no
import app.hearth.ui.resources.yes
import app.hearth.ui.resources.nav_menu
import app.hearth.ui.resources.nav_week
import app.hearth.ui.resources.nav_tasks
import app.hearth.ui.resources.nav_shopping
import app.hearth.ui.resources.nav_today
import app.hearth.ui.resources.tap_to_wake
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class Destination(val icon: ImageVector, val label: StringResource, val inNav: Boolean = true) {
    TODAY(HearthIcons.Sun, Res.string.nav_today),
    TASKS(HearthIcons.Backlog, Res.string.nav_tasks),
    MENU(HearthIcons.Restaurant, Res.string.nav_menu),
    WEEK(HearthIcons.People, Res.string.nav_week),
    BOARD(HearthIcons.Board, Res.string.nav_board),
    SHOPPING(HearthIcons.Cart, Res.string.nav_shopping),
    CONNECT(HearthIcons.Link, Res.string.nav_connect, inNav = false),
}

/**
 * The app shell: a navigation rail on the tablet (bottom bar on a phone), the selected screen,
 * and the capture bar underneath every screen.
 */
@Composable
fun HearthScreen(viewModel: HomeViewModel, lang: Lang, dimmed: Boolean) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val shopping by viewModel.shopping.collectAsStateWithLifecycle()
    val backlog by viewModel.backlog.collectAsStateWithLifecycle()
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val board by viewModel.board.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val spots by viewModel.stockSpots.collectAsStateWithLifecycle()
    val meals by viewModel.meals.collectAsStateWithLifecycle()
    val dishes by viewModel.dishes.collectAsStateWithLifecycle()
    val lastHuddle by viewModel.lastHuddle.collectAsStateWithLifecycle()
    val agenda by viewModel.agenda.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val photoVersion by viewModel.photoVersion.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val relayStatus by viewModel.relayStatus.collectAsStateWithLifecycle()
    val mcpPending by viewModel.mcpPending.collectAsStateWithLifecycle()
    val mcpActivity by viewModel.mcpActivity.collectAsStateWithLifecycle()
    val linkedApps by viewModel.linkedApps.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val stock by viewModel.stockItems.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<BacklogItem?>(null) }
    val doneToday by viewModel.doneToday.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()

    var destination by rememberSaveable { mutableStateOf(Destination.TODAY) }
    var tab by rememberSaveable { mutableStateOf(TaskTab.ROUTINES) }
    var planning by remember { mutableStateOf<BacklogItem?>(null) }
    var conversation by remember { mutableStateOf(false) }

    val counts = mapOf(
        Destination.TASKS to dontForget(backlog, today).size,
        Destination.BOARD to board.count { it.kind == BoardKind.FRICTION && it.isOpen },
        Destination.SHOPPING to shopping.sumOf { s -> s.items.count { !it.bought } },
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        val wide = maxWidth >= 840.dp
        val content = @Composable { modifier: Modifier ->
            Column(modifier) {
                AnimatedContent(destination, transitionSpec = { fadeIn() togetherWith fadeOut() }, modifier = Modifier.weight(1f)) { d ->
                    when (d) {
                        Destination.TODAY -> TodayScreen(
                            backlog, steps, shopping, board, meals, huddleDue(lastHuddle, today) && today.dayOfWeek.ordinal >= 4, doneToday, today, lang, wide,
                            onComplete = viewModel::complete,
                            onPlan = { item, w -> viewModel.plan(item, w) },
                            onOpenItem = { planning = it },
                            onGoTasks = { tab = TaskTab.WEEK; destination = Destination.TASKS },
                            onGoProjects = { tab = TaskTab.PROJECTS; destination = Destination.TASKS },
                            onGoShopping = { destination = Destination.SHOPPING },
                            onGoBoard = { destination = Destination.BOARD },
                            onGoWeek = { destination = Destination.WEEK },
                            onGoConnect = { destination = Destination.CONNECT },
                        )
                        Destination.MENU -> MenuScreen(
                            meals, recipes, today, lang, wide, photoVersion, viewModel.speechLanguage,
                            MenuCallbacks(
                                planRecipe = viewModel::planRecipe,
                                planDish = viewModel::planMeal,
                                removeMeal = viewModel::removeMeal,
                                saveRecipe = viewModel::saveRecipe,
                                deleteRecipe = viewModel::deleteRecipe,
                                loadPhoto = viewModel::photo,
                            ),
                        )
                        Destination.WEEK -> WeekScreen(
                            agenda, lastHuddle, backlog, steps, board, meals, dishes, shopping, today, lang,
                            WeekCallbacks(
                                loadAgenda = viewModel::loadAgenda,
                                planMeal = viewModel::planMeal,
                                removeMeal = viewModel::removeMeal,
                                addShopping = viewModel::addShopping,
                                goShopping = { destination = Destination.SHOPPING },
                                plan = { item, w, d -> viewModel.plan(item, w, d) },
                                complete = viewModel::complete,
                                resolveFriction = viewModel::resolveFriction,
                                setGoalStep = viewModel::setGoalStep,
                                finish = viewModel::finishWeekoverleg,
                            ),
                        )
                        Destination.CONNECT -> ConnectScreen(
                            connection, relayStatus, linkedApps, pairingCode, mcpActivity,
                            ConnectCallbacks(
                                save = { enabled, url -> viewModel.saveConnection(enabled, url) },
                                showCode = viewModel::showPairingCode,
                                hideCode = viewModel::hidePairingCode,
                                unlink = viewModel::unlinkApp,
                            ),
                        )
                        Destination.BOARD -> BoardScreen(
                            board, members, wide,
                            BoardCallbacks(
                                addMember = viewModel::addMember,
                                removeMember = viewModel::removeMember,
                                addRule = viewModel::addRule,
                                logFriction = viewModel::logFriction,
                                resolveFriction = viewModel::resolveFriction,
                                addGoal = viewModel::addGoal,
                                setGoalStep = viewModel::setGoalStep,
                                achieveGoal = viewModel::achieveGoal,
                                delete = viewModel::deleteBoardItem,
                            ),
                        )
                        Destination.TASKS -> TasksScreen(
                            backlog, steps, today, lang, tab, { tab = it },
                            TaskCallbacks(
                                complete = viewModel::complete,
                                reopen = viewModel::reopen,
                                open = { planning = it },
                                add = viewModel::addTask,
                                addStep = viewModel::addStep,
                                delete = { deleting = it },
                            ),
                        )
                        Destination.SHOPPING -> ShoppingScreen(
                            shopping, spots, stock, wide,
                            ShoppingCallbacks(
                                toggleBought = viewModel::toggleBought,
                                add = viewModel::addShopping,
                                remove = viewModel::removeShopping,
                                clearBought = viewModel::clearBought,
                                toggleStock = viewModel::toggleStock,
                                addStock = viewModel::addStock,
                                addSpot = viewModel::addSpot,
                                removeStock = viewModel::removeStock,
                                moveStock = viewModel::moveStock,
                                removeSpot = viewModel::removeSpot,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                mcpPending.firstOrNull()?.let { p ->
                    McpAsk(p.question, onYes = { viewModel.answerMcp(p, true) }, onNo = { viewModel.answerMcp(p, false) }, modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp).align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(10.dp))
                }
                CaptureBar(
                    messages, busy, viewModel.speechLanguage,
                    onSend = viewModel::send,
                    onAnswer = viewModel::answer,
                    onOpenConversation = { conversation = true },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp).align(Alignment.CenterHorizontally),
                )
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Rail(destination, counts) { destination = it }
                content(Modifier.weight(1f).fillMaxHeight().padding(top = 28.dp, end = 32.dp, bottom = 20.dp, start = 8.dp))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                content(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp))
                BottomBar(destination, counts) { destination = it }
            }
        }
        if (dimmed) AmbientOverlay()
    }

    planning?.let { item ->
        PlanSheet(
            item = item,
            today = today,
            lang = lang,
            actions = PlanActions(
                plan = { w, d, finishBy -> viewModel.plan(item, w, d, finishBy) },
                done = { viewModel.complete(item) },
                delete = { viewModel.delete(item) },
                interval = { viewModel.setInterval(item, it) },
                urgent = { viewModel.setUrgent(item, it) },
                waiting = { d, waiting -> viewModel.waitForReply(item, d, waiting) },
                move = { viewModel.move(item, it) },
            ),
            onDismiss = { planning = null },
        )
    }
    deleting?.let { item ->
        ConfirmDialog(
            stringResource(Res.string.delete_confirm, item.text),
            stringResource(if (item.kind == TaskKind.PROJECT && !item.isStep) Res.string.delete_confirm_project else Res.string.delete_confirm_body),
            onYes = { viewModel.delete(item) },
        ) { deleting = null }
    }
    if (conversation) ConversationSheet(messages, viewModel::answer) { conversation = false }
}

@Composable
private fun Rail(selected: Destination, counts: Map<Destination, Int>, onSelect: (Destination) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(128.dp).padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(HearthIcons.Flame, "Hearth", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Destination.entries.filter { it.inNav }.forEach { d -> RailItem(d, d == selected, counts[d], onSelect) }
        // Settings-like places sit at the bottom, out of the way.
        Spacer(Modifier.weight(1f))
        Destination.entries.filterNot { it.inNav }.forEach { d -> RailItem(d, d == selected, counts[d], onSelect) }
    }
}

@Composable
private fun RailItem(d: Destination, isSelected: Boolean, count: Int?, onSelect: (Destination) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onSelect(d) }.padding(vertical = 8.dp, horizontal = 6.dp),
    ) {
        Box(
            Modifier
                .size(width = 64.dp, height = 40.dp)
                .background(if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(d.icon, null, tint = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant)
            Badge(count, Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(d.label),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Something the family's AI wants to do that needs a yes; answered right here on the tablet. */
@Composable
private fun McpAsk(question: String, onYes: () -> Unit, onNo: () -> Unit, modifier: Modifier) {
    androidx.compose.material3.Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.onBackground, shadowElevation = 6.dp, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Icon(HearthIcons.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.mcp_asks), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                Text(question, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.surface)
            }
            androidx.compose.material3.OutlinedButton(onClick = onNo) { Text(stringResource(Res.string.no), color = MaterialTheme.colorScheme.surface) }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Button(onClick = onYes) { Text(stringResource(Res.string.yes)) }
        }
    }
}

@Composable
private fun BottomBar(selected: Destination, counts: Map<Destination, Int>, onSelect: (Destination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Destination.entries.filter { it.inNav }.forEach { d ->
            NavigationBarItem(
                selected = d == selected,
                onClick = { onSelect(d) },
                icon = {
                    Box {
                        Icon(d.icon, null)
                        Badge(counts[d], Modifier.align(Alignment.TopEnd).padding(start = 18.dp))
                    }
                },
                label = { Text(stringResource(d.label), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                // Six places on a phone: only the selected one shows its name.
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.surface,
                    indicatorColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        }
    }
}

/** A quiet count, never red: it says "there is something here", not "you are behind". */
@Composable
private fun Badge(count: Int?, modifier: Modifier) {
    if (count == null || count == 0) return
    Text(
        count.toString(),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)).padding(horizontal = 6.dp),
    )
}

/** Shown while the tablet is dimmed: just the time, readable from across the room. */
@Composable
private fun AmbientOverlay() {
    val now = rememberNow()
    Box(Modifier.fillMaxSize().background(Color(0xF0100C0A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatClock(now), color = Color(0xFFE8D9C8), fontSize = 120.sp)
            Text(stringResource(Res.string.tap_to_wake), color = Color(0x99E8D9C8), fontSize = 20.sp)
        }
    }
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(localNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = localNow()
        }
    }
    return now
}
