package app.hearth.ui

import app.hearth.core.model.GoalScope
import app.hearth.core.model.isRoutine
import app.hearth.core.model.isRoutineStep
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    // In the order of the topics everywhere: the tasks, food (menu before shopping), the Werkbord, then the meeting
    // that brings them together.
    TODAY(HearthIcons.Sun, Res.string.nav_today),
    TASKS(HearthIcons.Backlog, Res.string.nav_tasks),
    MENU(HearthIcons.Restaurant, Res.string.nav_menu),
    SHOPPING(HearthIcons.Cart, Res.string.nav_shopping),
    BOARD(HearthIcons.Board, Res.string.nav_board),
    WEEK(HearthIcons.People, Res.string.nav_week),
    CONNECT(HearthIcons.Settings, Res.string.nav_connect, inNav = false),
}

/**
 * The app shell: a navigation rail on the tablet (bottom bar on a phone), the selected screen,
 * and the capture bar underneath every screen.
 */
@Composable
fun HearthScreen(viewModel: HomeViewModel, dimmed: Boolean) {
    val lang by viewModel.lang.collectAsStateWithLifecycle()
    // Kept outside the rebuild, so switching the language stays on the same screen.
    val destination = rememberSaveable { mutableStateOf(Destination.TODAY) }
    // The chosen language wins over the device's; switching rebuilds the screen in the new language.
    CompositionLocalProvider(LocalAppLocale provides lang.localeTag, LocalLang provides lang) {
        key(lang) { HearthContent(viewModel, lang, dimmed, destination) }
    }
}

@Composable
private fun HearthContent(viewModel: HomeViewModel, lang: Lang, dimmed: Boolean, destinationState: MutableState<Destination>) {
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
    val google by viewModel.google.collectAsStateWithLifecycle()
    val wins by viewModel.wins.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val weekOn by viewModel.weekOn.collectAsStateWithLifecycle()
    val ruleChecks by viewModel.ruleChecks.collectAsStateWithLifecycle()
    val ruleScores by viewModel.ruleScores.collectAsStateWithLifecycle()
    val ruleReviews by viewModel.ruleReviews.collectAsStateWithLifecycle()
    val ruleHistory by viewModel.ruleHistory.collectAsStateWithLifecycle()
    val habitChecks by viewModel.habitChecks.collectAsStateWithLifecycle()
    val habitReviews by viewModel.habitReviews.collectAsStateWithLifecycle()
    val ease by viewModel.ease.collectAsStateWithLifecycle()
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val lastTranscript by viewModel.lastTranscript.collectAsStateWithLifecycle()
    val huddles by viewModel.huddles.collectAsStateWithLifecycle()
    val voorstel by viewModel.voorstel.collectAsStateWithLifecycle()
    val heardLines by viewModel.heardLines.collectAsStateWithLifecycle()
    val processing by viewModel.processing.collectAsStateWithLifecycle()
    val learnedWords by viewModel.learnedWords.collectAsStateWithLifecycle()
    val vocabulary by viewModel.vocabulary.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val joinCode by viewModel.joinCode.collectAsStateWithLifecycle()
    // Back on screen (a tablet waking, a phone opened): catch up with the household's other devices.
    LifecycleResumeEffect(Unit) {
        viewModel.syncSoon()
        onPauseOrDispose {}
    }
    val agree by viewModel.agree.collectAsStateWithLifecycle()
    val googleBusy by viewModel.googleBusy.collectAsStateWithLifecycle()
    val backupList by viewModel.backupList.collectAsStateWithLifecycle()
    val stock by viewModel.stockItems.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<BacklogItem?>(null) }
    val doneToday by viewModel.doneToday.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val smartListening by viewModel.listening.collectAsStateWithLifecycle()
    val interpreterState by viewModel.interpreting.collectAsStateWithLifecycle()
    val writingState by viewModel.writingState.collectAsStateWithLifecycle()
    val writingLeft by viewModel.writingLeft.collectAsStateWithLifecycle()

    var destination by destinationState
    var planning by remember { mutableStateOf<BacklogItem?>(null) }
    var conversation by remember { mutableStateOf(false) }

    // Luister mee: hoisted here so it keeps listening while the meeting looks at a list.
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    val saveFile = rememberSaveFile()
    var listenStatus by remember { mutableStateOf<ListenStatus?>(null) }
    val transcriber = rememberTranscriber(
        language = if (lang == Lang.NL) "nl-BE" else "en-GB",
        vocabulary = vocabulary,
        onLine = viewModel::heard,
        onPartial = { partial = it },
        onActive = { listening = it; if (!it) partial = "" },
        onStatus = { listenStatus = it },
        writing = viewModel.writing.takeIf { viewModel.writingAvailable },
        onWritten = viewModel::written,
    )
    val listen = ListenControl(transcriber.available, listening, partial, listenStatus, transcriber.fix, writingLeft) { if (listening) transcriber.stop() else transcriber.start() }

    val counts = mapOf(
        Destination.TASKS to dontForget(backlog, today).size,
        Destination.BOARD to board.count { it.isWorkingPoint && it.isOpen },
        Destination.SHOPPING to shopping.sumOf { s -> s.items.count { !it.bought } },
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        val wide = maxWidth >= 840.dp
        val narrow = maxWidth < 600.dp
        val content = @Composable { modifier: Modifier ->
            Column(modifier) { CompositionLocalProvider(LocalNarrow provides narrow) {
                AnimatedContent(destination, transitionSpec = { fadeIn() togetherWith fadeOut() }, modifier = Modifier.weight(1f)) { d ->
                    when (d) {
                        Destination.TODAY -> TodayScreen(
                            backlog, steps, shopping, board, meals, wins, huddleDue(lastHuddle, today) && today.dayOfWeek.ordinal >= 4, doneToday, today, lang, wide,
                            onComplete = viewModel::complete,
                            onPlan = { item, w -> viewModel.plan(item, w) },
                            onOpenItem = { planning = it },
                            onGoTasks = { destination = Destination.TASKS },
                            onGoShopping = { destination = Destination.SHOPPING },
                            onGoBoard = { destination = Destination.BOARD },
                            onGoMenu = { destination = Destination.MENU },
                            onGoWeek = { destination = Destination.WEEK },
                            onGoConnect = { destination = Destination.CONNECT },
                            ruleScores = ruleScores,
                        )
                        Destination.MENU -> MenuScreen(
                            meals, recipes, today, lang, wide, photoVersion, lang.speechTag,
                            MenuCallbacks(
                                planRecipe = viewModel::planRecipe,
                                planDish = viewModel::planMeal,
                                removeMeal = viewModel::removeMeal,
                                saveRecipe = viewModel::saveRecipe,
                                deleteRecipe = viewModel::deleteRecipe,
                                loadPhoto = viewModel::photo,
                                importRecipe = viewModel::importRecipe,
                            ),
                        )
                        Destination.WEEK -> WeekScreen(
                            agenda, lastHuddle, backlog, steps, board, meals, dishes, shopping, today, lang, wide,
                            MeetingState(wins, week, weekOn, ruleChecks, members, transcript, lastTranscript, huddles, voorstel, heardLines, agree, ruleScores, habitChecks, habitReviews, ruleHistory, ruleReviews, processing, learnedWords),
                            listen,
                            WeekCallbacks(
                                loadAgenda = viewModel::loadAgenda,
                                planMeal = viewModel::planMeal,
                                removeMeal = viewModel::removeMeal,
                                addShopping = viewModel::addShopping,
                                goShopping = { destination = Destination.SHOPPING },
                                plan = { item, w, d -> viewModel.plan(item, w, d) },
                                complete = viewModel::complete,
                                resolveFriction = viewModel::resolveFriction,
                                markFriction = viewModel::markFriction,
                                setGoalStep = viewModel::setGoalStep,
                                addGoal = { viewModel.addGoal(it, GoalScope.FAMILY, null) },
                                achieveGoal = viewModel::achieveGoal,
                                finish = viewModel::finishWeekoverleg,
                                goTasks = { destination = Destination.TASKS },
                                goMenu = { destination = Destination.MENU },
                                addWin = viewModel::addWin,
                                removeWin = viewModel::removeWin,
                                rateWeek = { e, g -> viewModel.rateWeek(e, g) },
                                setWeekOn = viewModel::setWeekOn,
                                checkRule = { r, e -> viewModel.checkRule(r, e) },
                                lessOften = viewModel::lessOften,
                                makeEasier = viewModel::makeEasier,
                                rateHabit = { i, e -> viewModel.rateHabit(i, e) },
                                smallStep = { i, t -> viewModel.setSmallStep(i, t) },
                                heard = viewModel::heard,
                                addTo = { t, section -> viewModel.addTask(t, section) },
                                addRule = viewModel::addRule,
                                setAnchor = { i, a -> viewModel.setAnchor(i, a) },
                                prepareFinish = viewModel::prepareFinish,
                                closeAgree = viewModel::closeAgree,
                                clearTranscript = viewModel::clearTranscript,
                                openVoorstel = viewModel::openVoorstel,
                                closeVoorstel = viewModel::closeVoorstel,
                                moveVoorstel = { i, p -> viewModel.moveVoorstel(i, p) },
                                toggleVoorstel = { viewModel.toggleVoorstel(it) },
                                addVoorstel = viewModel::addVoorstel,
                                editVoorstel = { i, c -> viewModel.editVoorstel(i, c) },
                                asNewVoorstel = { viewModel.asNewVoorstel(it) },
                                fixLine = viewModel::fixLine,
                                forgetWord = viewModel::forgetWord,
                                habitTip = { i, t -> viewModel.applyTip(i, t) },
                                pairing = { i, t -> viewModel.setPairing(i, t) },
                                openItem = { planning = it },
                                ruleHelp = viewModel::setRuleHelp,
                                editHuddle = viewModel::editHuddle,
                                exportConversation = { lines -> viewModel.exportConversation(lines, saveFile) },
                            ),
                        )
                        Destination.CONNECT -> ConnectScreen(
                            connection, relayStatus, linkedApps, pairingCode, mcpActivity,
                            ConnectCallbacks(
                                save = viewModel::saveConnection,
                                showCode = viewModel::showPairingCode,
                                hideCode = viewModel::hidePairingCode,
                                unlink = viewModel::unlinkApp,
                                loadExamples = if (viewModel.offersExamples) viewModel::loadExamples else null,
                                setLanguage = viewModel::setLanguage,
                                setSmartListening = viewModel::setSmartListening,
                                retrySmartListening = viewModel::retrySmartListening,
                                setInterpreter = viewModel::setInterpreter,
                                retryInterpreter = viewModel::retryInterpreter,
                                setTranscriber = viewModel::setTranscriber,
                                retryTranscriber = viewModel::retryTranscriber,
                            ),
                            google, viewModel.googleAvailable, googleBusy, backupList,
                            GoogleCallbacks(
                                connectBackup = viewModel::connectBackup,
                                backupNow = viewModel::backupNow,
                                openBackups = viewModel::openBackups,
                                closeBackups = viewModel::closeBackups,
                                restore = viewModel::restoreBackup,
                                connectCalendar = viewModel::connectCalendar,
                                syncCalendar = viewModel::syncCalendar,
                                set = { b, c -> viewModel.setGoogleSync(b, c) },
                            ),
                            sync, joinCode,
                            SyncCallbacks(
                                start = viewModel::startSync,
                                share = viewModel::shareSync,
                                hideCode = viewModel::hideJoinCode,
                                join = { code, keep -> viewModel.joinSync(code, keep) },
                                syncNow = viewModel::syncNow,
                                stop = viewModel::stopSync,
                            ),
                            listening = smartListening,
                            listeningAvailable = viewModel.listeningAvailable,
                            interpreter = interpreterState,
                            interpreterAvailable = viewModel.interpretingAvailable,
                            transcriber = writingState,
                            transcriberAvailable = viewModel.writingAvailable,
                        )
                        Destination.BOARD -> BoardScreen(
                            board, members, backlog, ruleScores, today, wide,
                            BoardCallbacks(
                                addMember = viewModel::addMember,
                                removeMember = viewModel::removeMember,
                                addRule = viewModel::addRule,
                                logFriction = viewModel::logFriction,
                                resolveFriction = viewModel::resolveFriction,
                                markFriction = viewModel::markFriction,
                                rateRule = { r, e -> viewModel.checkRule(r, e) },
                                addGoal = viewModel::addGoal,
                                setGoalStep = viewModel::setGoalStep,
                                achieveGoal = viewModel::achieveGoal,
                                delete = viewModel::deleteBoardItem,
                                rename = viewModel::renameBoardItem,
                                ruleHelp = viewModel::setRuleHelp,
                                reopenGoal = viewModel::reopenGoal,
                                addWin = { text, topic -> viewModel.addWin(text, topic) },
                            ),
                            ruleReviews = ruleReviews,
                        )
                        Destination.TASKS -> TasksScreen(
                            backlog, steps, today, lang, ease,
                            TaskCallbacks(
                                complete = viewModel::complete,
                                reopen = viewModel::reopen,
                                open = { planning = it },
                                add = viewModel::addTask,
                                addStep = viewModel::addStep,
                                delete = { deleting = it },
                                rateHabit = { i, e -> viewModel.rateHabit(i, e) },
                                smallStep = { i, t -> viewModel.setSmallStep(i, t) },
                            ),
                        )
                        Destination.SHOPPING -> ShoppingScreen(
                            shopping, spots, stock, meals, today, lang, wide,
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
                                goMenu = { destination = Destination.MENU },
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (listening && destination != Destination.WEEK) {
                    ListenPill(transcript.size, partial, onBack = { destination = Destination.WEEK }, onStop = { transcriber.stop() }, modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp).align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(10.dp))
                }
                mcpPending.firstOrNull()?.let { p ->
                    McpAsk(p.question, onYes = { viewModel.answerMcp(p, true) }, onNo = { viewModel.answerMcp(p, false) }, modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp).align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(10.dp))
                }
                CaptureBar(
                    messages, busy, lang.speechTag,
                    onSend = viewModel::send,
                    onAnswer = viewModel::answer,
                    onOpenConversation = { conversation = true },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp).align(Alignment.CenterHorizontally),
                )
            } }
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

    // The sheet follows the item as it changes, so a routine's moment, rhythm and days can be set in one go.
    planning?.let { opened -> backlog.firstOrNull { it.id == opened.id } ?: opened }?.let { item ->
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
                anchor = { viewModel.setAnchor(item, it) },
                addStep = { viewModel.addRoutineStep(item, it) },
                removeStep = { viewModel.delete(it) },
                moveStep = { step, place -> viewModel.moveStep(step, place) },
                schedule = { viewModel.setSchedule(item, it) },
                pairing = { viewModel.setPairing(item, it) },
                smallStep = { viewModel.setSmallStep(item, it) },
                tip = { viewModel.applyTip(item, it) },
                rate = { viewModel.rateHabit(item, it) },
            ),
            onDismiss = { planning = null },
            steps = steps.filter { it.parentId == item.id && it.isRoutineStep },
            review = habitReviews[item.id],
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
                // A touch smaller than labelSmall, so "Weekoverleg" fits a sixth of a phone.
                label = { Text(stringResource(d.label), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = (-0.1).sp), maxLines = 1, softWrap = false, overflow = TextOverflow.Visible) },
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
