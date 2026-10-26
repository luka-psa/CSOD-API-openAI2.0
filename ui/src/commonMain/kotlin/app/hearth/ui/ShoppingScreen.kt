package app.hearth.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.hearth.core.model.Lang
import app.hearth.core.model.Meal
import app.hearth.core.model.ShopKind
import app.hearth.core.model.Topic
import app.hearth.core.model.dayLabel
import app.hearth.core.model.normalizeName
import kotlinx.datetime.LocalDate
import app.hearth.ui.resources.at_home
import app.hearth.ui.resources.menu_tab_empty
import app.hearth.ui.resources.menu_tab_hint
import app.hearth.ui.resources.menu_tab_plan
import app.hearth.ui.resources.nav_menu
import app.hearth.core.model.ShoppingItem
import app.hearth.core.model.StockItem
import app.hearth.core.model.StockState
import app.hearth.core.model.StorageSpot
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.all_bought
import app.hearth.ui.resources.clear_bought
import app.hearth.ui.resources.clear_bought_body
import app.hearth.ui.resources.clear_bought_confirm
import app.hearth.ui.resources.delete_confirm
import app.hearth.ui.resources.delete_confirm_body
import app.hearth.ui.resources.on_list
import app.hearth.ui.resources.shopping_add_hint
import app.hearth.ui.resources.shopping_empty
import app.hearth.ui.resources.shopping_list
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.spot_add
import app.hearth.ui.resources.stock_hold_hint
import app.hearth.ui.resources.stock_move_title
import app.hearth.ui.resources.stock_remove_spot
import app.hearth.ui.resources.stock_remove_spot_body
import app.hearth.ui.resources.spot_name_hint
import app.hearth.ui.resources.stock_add_hint
import app.hearth.ui.resources.stock_hint
import app.hearth.ui.resources.stock_remove_body
import app.hearth.ui.resources.stock_title
import app.hearth.ui.resources.tab_list
import org.jetbrains.compose.resources.stringResource

/** Everything the shopping screen can ask for; each is one tool call in the ViewModel. */
class ShoppingCallbacks(
    val toggleBought: (ShoppingItem) -> Unit,
    val add: (String) -> Unit,
    val remove: (ShoppingItem) -> Unit,
    val clearBought: () -> Unit,
    val toggleStock: (StockItem) -> Unit,
    val addStock: (String, StorageSpot) -> Unit,
    val addSpot: (String) -> Unit,
    val removeStock: (StockItem) -> Unit,
    val moveStock: (StockItem, StorageSpot) -> Unit,
    val removeSpot: (StorageSpot) -> Unit,
    /** To the Weekmenu, when there's nothing planned yet. */
    val goMenu: () -> Unit = {},
)

/** The Weekmenu tab among the storage spots (not a spot id). */
private const val MENU_TAB = "menu"

/**
 * The shared shopping list (grouped by shop) next to the stock (per room or topic): one tap on a stock
 * item puts it on the list; buying it brings it back in the house. On a phone they are two tabs.
 */
@Composable
fun ShoppingScreen(
    sections: List<ShopSection>,
    spots: List<StorageSpot>,
    stock: List<StockItem>,
    meals: List<Meal>,
    today: LocalDate,
    lang: Lang,
    wide: Boolean,
    cb: ShoppingCallbacks,
) {
    var showStock by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<ShoppingItem?>(null) }
    var removingStock by remember { mutableStateOf<StockItem?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var addingSpot by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<StockItem?>(null) }
    var removingSpot by remember { mutableStateOf<StorageSpot?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text(stringResource(Res.string.shopping_list), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.size(14.dp))
        if (wide) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ListPanel(sections, cb, onRemove = { removing = it }, onClear = { clearing = true }, modifier = Modifier.weight(1f), compact = false)
                StockPanel(spots, stock, Menu(meals, sections, today, lang), cb, onRemove = { removingStock = it }, onAddSpot = { addingSpot = true }, onMove = { moving = it }, onRemoveSpot = { removingSpot = it }, modifier = Modifier.weight(1.1f))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(Res.string.tab_list), !showStock, { showStock = false }, badge = sections.sumOf { s -> s.items.count { !it.bought } }, icon = HearthIcons.Cart)
                ChoiceChip(stringResource(Res.string.stock_title), showStock, { showStock = true }, icon = HearthIcons.Home)
            }
            Spacer(Modifier.size(12.dp))
            if (showStock) StockPanel(spots, stock, Menu(meals, sections, today, lang), cb, onRemove = { removingStock = it }, onAddSpot = { addingSpot = true }, onMove = { moving = it }, onRemoveSpot = { removingSpot = it }, modifier = Modifier.weight(1f))
            else ListPanel(sections, cb, onRemove = { removing = it }, onClear = { clearing = true }, modifier = Modifier.weight(1f), compact = true)
        }
    }

    removing?.let { item ->
        ConfirmDialog(stringResource(Res.string.delete_confirm, item.name), stringResource(Res.string.delete_confirm_body), onYes = { cb.remove(item) }) { removing = null }
    }
    removingStock?.let { item ->
        ConfirmDialog(stringResource(Res.string.delete_confirm, item.name), stringResource(Res.string.stock_remove_body), onYes = { cb.removeStock(item) }) { removingStock = null }
    }
    if (clearing) {
        ConfirmDialog(stringResource(Res.string.clear_bought_confirm), stringResource(Res.string.clear_bought_body), onYes = cb.clearBought) { clearing = false }
    }
    moving?.let { item ->
        MoveStockDialog(item, spots, onMove = { cb.moveStock(item, it) }) { moving = null }
    }
    removingSpot?.let { spot ->
        ConfirmDialog(stringResource(Res.string.stock_remove_spot, spot.name) + "?", stringResource(Res.string.stock_remove_spot_body), onYes = { cb.removeSpot(spot) }) { removingSpot = null }
    }
    if (addingSpot) {
        TextDialog(stringResource(Res.string.spot_add), stringResource(Res.string.spot_name_hint), onDone = { text, _ -> cb.addSpot(text) }) { addingSpot = false }
    }
}

// ---------- the list ----------

@Composable
private fun ListPanel(sections: List<ShopSection>, cb: ShoppingCallbacks, onRemove: (ShoppingItem) -> Unit, onClear: () -> Unit, modifier: Modifier, compact: Boolean) {
    val bought = sections.sumOf { s -> s.items.count { it.bought } }
    val open = sections.sumOf { s -> s.items.count { !it.bought } }
    Column(modifier) {
        AddRow(stringResource(Res.string.shopping_add_hint), cb.add, compact = compact)
        Spacer(Modifier.size(12.dp))
        ListSurface(Modifier.weight(1f)) {
            if (sections.isEmpty()) {
                EmptyHint(stringResource(Res.string.shopping_empty), Modifier.padding(24.dp))
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    if (bought > 0) {
                        item(key = "clear") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp)) {
                                if (open == 0) {
                                    Icon(HearthIcons.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(Res.string.all_bought), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                                TextButton(onClick = onClear) {
                                    Icon(HearthIcons.Delete, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(Res.string.clear_bought, bought))
                                }
                            }
                        }
                    }
                    sections.forEach { section ->
                        item(key = section.shop.id) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp)) {
                                Box(Modifier.size(10.dp).background(section.shop.kind.color(), CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text(section.shop.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(section.items.count { !it.bought }.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(section.items, key = { it.id }) { item -> ItemRow(item, cb.toggleBought, onRemove, Modifier.animateItem()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, onToggle: (ShoppingItem) -> Unit, onRemove: (ShoppingItem) -> Unit, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onToggle(item) }.heightIn(min = 60.dp),
    ) {
        RoundCheck(item.bought, { onToggle(item) })
        Spacer(Modifier.width(6.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.bought) TextDecoration.LineThrough else null,
            color = if (item.bought) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        item.quantity?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp))
        }
        if (!item.bought) DeleteButton({ onRemove(item) })
    }
}

// ---------- the stock ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StockPanel(
    spots: List<StorageSpot>,
    stock: List<StockItem>,
    menu: Menu,
    cb: ShoppingCallbacks,
    onRemove: (StockItem) -> Unit,
    onAddSpot: () -> Unit,
    onMove: (StockItem) -> Unit,
    onRemoveSpot: (StorageSpot) -> Unit,
    modifier: Modifier,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // The week's menu first: what we cook decides what we buy. It's open by default while there is a menu.
    val onMenu = selectedId == MENU_TAB || (selectedId == null && menu.meals.isNotEmpty())
    val selected = if (onMenu) null else spots.firstOrNull { it.id == selectedId } ?: spots.firstOrNull()
    Column(modifier) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
            item(key = MENU_TAB) { ChoiceChip(stringResource(Res.string.nav_menu), onMenu, { selectedId = MENU_TAB }, icon = Topic.MENU.icon()) }
            items(spots, key = { it.id }) { spot ->
                val needed = stock.count { it.spotId == spot.id && it.state != StockState.IN_STOCK }
                ChoiceChip(spot.name, spot.id == selected?.id, { selectedId = spot.id }, badge = needed, onLongClick = { onRemoveSpot(spot) })
            }
            item { ChoiceChip("", false, onAddSpot, icon = HearthIcons.Add) }
        }
        Spacer(Modifier.size(12.dp))
        ListSurface(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                if (onMenu) {
                    MenuIngredients(menu, stock, cb)
                    return@Column
                }
                Text(stringResource(Res.string.stock_hint) + " " + stringResource(Res.string.stock_hold_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                if (selected != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        stock.filter { it.spotId == selected.id }.forEach { item -> StockTile(item, { cb.toggleStock(item) }, { onMove(item) }, { onRemove(item) }) }
                    }
                    Spacer(Modifier.size(16.dp))
                    AddRow(stringResource(Res.string.stock_add_hint, selected.name), { cb.addStock(it, selected) }, muted = true, compact = true)
                }
            }
        }
    }
}

/** This week's meals for the Weekmenu tab, with what's on the list already. */
private class Menu(val meals: List<Meal>, sections: List<ShopSection>, val today: LocalDate, val lang: Lang) {
    val onList: Set<String> = sections.flatMap { it.items }.filter { !it.bought }.map { normalizeName(it.name) }.toSet()
}

/**
 * The Weekmenu tab: each meal of the week with its ingredients. What's on the list shows it; what isn't goes on it with
 * one tap, so the list and the menu never drift apart.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuIngredients(menu: Menu, stock: List<StockItem>, cb: ShoppingCallbacks) {
    val inHouse = stock.filter { it.state == StockState.IN_STOCK }.map { normalizeName(it.name) }.toSet()
    Text(stringResource(Res.string.menu_tab_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.size(12.dp))
    if (menu.meals.isEmpty()) {
        EmptyHint(stringResource(Res.string.menu_tab_empty))
        TextButton(onClick = cb.goMenu) { Text(stringResource(Res.string.menu_tab_plan)) }
        return
    }
    menu.meals.sortedBy { it.date }.forEach { meal ->
        val day = runCatching { LocalDate.parse(meal.date) }.getOrNull()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            Text(
                day?.let { dayLabel(it, menu.today, menu.lang).replaceFirstChar { c -> c.uppercase() } }.orEmpty(),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp),
            )
            Text(meal.dish, style = MaterialTheme.typography.titleMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            meal.ingredients.forEach { name ->
                val key = normalizeName(name)
                IngredientChip(name, key in menu.onList, key in inHouse) { cb.add(name) }
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}

/** On the list (cart, "op de lijst"), at home, or a tap away from the list. */
@Composable
private fun IngredientChip(name: String, listed: Boolean, atHome: Boolean, onAdd: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (listed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
            .clickable(enabled = !listed, onClick = onAdd).heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(
            when { listed -> HearthIcons.Cart; atHome -> HearthIcons.Check; else -> HearthIcons.Add },
            null, tint = if (listed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            when {
                listed -> Text(stringResource(Res.string.on_list), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                atHome -> Text(stringResource(Res.string.at_home), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Tap: "it's out" (goes on the list). Tap again: "we have it". Hold: move it to another place. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StockTile(item: StockItem, onToggle: () -> Unit, onHold: () -> Unit, onRemove: () -> Unit) {
    val needed = item.state != StockState.IN_STOCK
    val bg by animateColorAsState(if (needed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(bg).combinedClickable(onClick = onToggle, onLongClick = onHold).heightIn(min = 56.dp).padding(start = 16.dp, end = 2.dp),
    ) {
        if (needed) {
            Icon(HearthIcons.Cart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            if (needed) Text(stringResource(Res.string.on_list), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        DeleteButton(onRemove)
    }
}

/** "Chips hoort bij": every place as a chip; one tap moves it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveStockDialog(item: StockItem, spots: List<StorageSpot>, onMove: (StorageSpot) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.stock_move_title, item.name)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                spots.forEach { spot -> ChoiceChip(spot.name, spot.id == item.spotId, { onMove(spot); onDismiss() }) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/** A calm colour per kind of shop, so the eye finds "the pharmacy bit" quickly. */
private fun ShopKind.color() = when (this) {
    ShopKind.SUPERMARKET -> Color(0xFF4F7A5E)
    ShopKind.PHARMACY -> Color(0xFF3F6FB0)
    ShopKind.BAKERY -> Color(0xFFC79A5B)
    ShopKind.DIY -> Color(0xFFE0612F)
    ShopKind.FURNITURE -> Color(0xFF7A5BB0)
    ShopKind.OTHER -> Color(0xFF8A8079)
}
