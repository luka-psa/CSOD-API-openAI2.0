package app.hearth.ui

import app.hearth.ui.resources.recipe_link_hint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.ai.route.RecipeWords
import app.hearth.core.model.Lang
import app.hearth.core.model.Meal
import app.hearth.core.model.Recipe
import app.hearth.core.model.Section
import app.hearth.core.model.dayLabel
import app.hearth.core.model.normalizeName
import app.hearth.core.model.plusDays
import app.hearth.core.model.weekdayName
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.cancel
import app.hearth.ui.resources.delete
import app.hearth.ui.resources.delete_confirm
import app.hearth.ui.resources.meal_add
import app.hearth.ui.resources.meal_dish_hint
import app.hearth.ui.resources.meal_ingredients_hint
import app.hearth.ui.resources.meal_remove_body
import app.hearth.ui.resources.menu_also_recipe
import app.hearth.ui.resources.menu_choose_title
import app.hearth.ui.resources.menu_earlier
import app.hearth.ui.resources.menu_ingredient_add
import app.hearth.ui.resources.menu_ingredients
import app.hearth.ui.resources.menu_new_recipe
import app.hearth.ui.resources.menu_no_ingredients
import app.hearth.ui.resources.menu_on_menu
import app.hearth.ui.resources.menu_other_dish
import app.hearth.ui.resources.menu_photo_choose
import app.hearth.ui.resources.menu_photo_other
import app.hearth.ui.resources.menu_photo_remove
import app.hearth.ui.resources.menu_photo_take
import app.hearth.ui.resources.menu_recipe_body
import app.hearth.ui.resources.menu_recipe_name
import app.hearth.ui.resources.menu_recipe_title
import app.hearth.ui.resources.menu_recipes
import app.hearth.ui.resources.menu_recipes_empty
import app.hearth.ui.resources.menu_story_hint
import app.hearth.ui.resources.menu_story_title
import app.hearth.ui.resources.menu_subtitle
import app.hearth.ui.resources.menu_tab_recipes
import app.hearth.ui.resources.menu_tab_week
import app.hearth.ui.resources.menu_week
import app.hearth.ui.resources.menu_x_ingredients
import app.hearth.ui.resources.edit
import app.hearth.ui.resources.nav_menu
import app.hearth.ui.resources.save
import app.hearth.ui.theme.style
import app.hearth.core.model.Topic
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Everything the week menu can ask for; each is one tool call in the ViewModel. */
class MenuCallbacks(
    val planRecipe: (LocalDate, Recipe) -> Unit,
    val planDish: (LocalDate, String, String) -> Unit,
    val removeMeal: (Meal) -> Unit,
    val saveRecipe: (name: String, ingredients: List<String>, notes: String?, previousKey: String?, photo: ByteArray?) -> Unit,
    val deleteRecipe: (Recipe) -> Unit,
    val loadPhoto: suspend (String) -> ByteArray?,
    val importRecipe: (String) -> Unit = {},
)

/**
 * The week menu next to the family's recipes. A recipe is made by telling what you cooked (typed or spoken)
 * and adding a photo; it then goes on a day in one tap, with its ingredients onto the shopping list.
 */
@Composable
fun MenuScreen(
    meals: List<Meal>,
    recipes: List<Recipe>,
    today: LocalDate,
    lang: Lang,
    wide: Boolean,
    photoVersion: Int,
    speechLanguage: String,
    cb: MenuCallbacks,
) {
    var showRecipes by rememberSaveable { mutableStateOf(false) }
    var choosingFor by remember { mutableStateOf<LocalDate?>(null) }
    var opened by remember { mutableStateOf<Recipe?>(null) }
    var editing by remember { mutableStateOf<Recipe?>(null) }
    var creating by remember { mutableStateOf(false) }
    var removingMeal by remember { mutableStateOf<Meal?>(null) }
    var deleting by remember { mutableStateOf<Recipe?>(null) }
    val byKey = recipes.associateBy { it.key }

    val planner = @Composable { m: Modifier ->
        Planner(meals, byKey, today, lang, photoVersion, cb.loadPhoto, onChoose = { choosingFor = it }, onOpen = { opened = it }, onRemove = { removingMeal = it }, modifier = m)
    }
    val library = @Composable { m: Modifier ->
        Library(recipes, photoVersion, cb.loadPhoto, onNew = { creating = true }, onOpen = { opened = it }, onMakeRecipe = { editing = it }, onImport = cb.importRecipe, modifier = m)
    }

    Column(Modifier.fillMaxSize()) {
        Text(stringResource(Res.string.nav_menu), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(Res.string.menu_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        if (wide) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                planner(Modifier.weight(0.9f))
                library(Modifier.weight(1.1f))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(Res.string.menu_tab_week), !showRecipes, { showRecipes = false }, icon = HearthIcons.Calendar)
                ChoiceChip(stringResource(Res.string.menu_tab_recipes), showRecipes, { showRecipes = true }, icon = HearthIcons.Restaurant)
            }
            Spacer(Modifier.size(12.dp))
            if (showRecipes) library(Modifier.weight(1f)) else planner(Modifier.weight(1f))
        }
    }

    choosingFor?.let { day ->
        ChooseMealSheet(day, today, lang, recipes, photoVersion, cb.loadPhoto,
            onRecipe = { cb.planRecipe(day, it) },
            onDish = { dish, ingredients, alsoRecipe ->
                cb.planDish(day, dish, ingredients)
                if (alsoRecipe) cb.saveRecipe(dish, app.hearth.core.tools.week.splitIngredients(ingredients), null, null, null)
            },
            onNewRecipe = { creating = true },
        ) { choosingFor = null }
    }
    opened?.let { recipe ->
        RecipeSheet(recipe, today, lang, photoVersion, cb.loadPhoto,
            onPlan = { day -> cb.planRecipe(day, recipe) },
            onEdit = { editing = recipe },
            onDelete = { deleting = recipe },
        ) { opened = null }
    }
    if (creating || editing != null) {
        RecipeEditor(editing, speechLanguage, photoVersion, cb.loadPhoto, onSave = cb.saveRecipe) {
            creating = false
            editing = null
        }
    }
    removingMeal?.let { meal ->
        ConfirmDialog(stringResource(Res.string.delete_confirm, meal.dish), stringResource(Res.string.meal_remove_body), onYes = { cb.removeMeal(meal) }) { removingMeal = null }
    }
    deleting?.let { recipe ->
        ConfirmDialog(stringResource(Res.string.delete_confirm, recipe.name), stringResource(Res.string.meal_remove_body), onYes = { cb.deleteRecipe(recipe); opened = null }) { deleting = null }
    }
}

// ---------- the week ----------

@Composable
private fun Planner(
    meals: List<Meal>,
    recipes: Map<String, Recipe>,
    today: LocalDate,
    lang: Lang,
    photoVersion: Int,
    loadPhoto: suspend (String) -> ByteArray?,
    onChoose: (LocalDate) -> Unit,
    onOpen: (Recipe) -> Unit,
    onRemove: (Meal) -> Unit,
    modifier: Modifier,
) {
    ListSurface(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text(stringResource(Res.string.menu_week), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            (0 until 7).map { today.plusDays(it) }.forEach { day ->
                val meal = meals.firstOrNull { it.date == day.toString() }
                val recipe = meal?.let { recipes[normalizeName(it.dish)] }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .clickable { if (recipe != null) onOpen(recipe) else onChoose(day) }
                        .heightIn(min = 68.dp).padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Text(
                        menuDayName(day, today, lang),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.width(124.dp),
                    )
                    if (meal == null) {
                        Icon(HearthIcons.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.meal_add), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    } else {
                        RecipeThumb(recipe, meal.dish, photoVersion, loadPhoto, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(meal.dish, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (meal.ingredients.isNotEmpty()) {
                                Text(meal.ingredients.joinToString(", "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        DeleteButton({ onRemove(meal) })
                    }
                }
            }
        }
    }
}

internal fun menuDayName(day: LocalDate, today: LocalDate, lang: Lang): String =
    if (day.toEpochDays() - today.toEpochDays() < 2) dayLabel(day, today, lang).replaceFirstChar { it.uppercase() }
    else "${weekdayName(day.dayOfWeek, lang).replaceFirstChar { it.uppercase() }} ${day.day}"

// ---------- the recipes ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Library(
    recipes: List<Recipe>,
    photoVersion: Int,
    loadPhoto: suspend (String) -> ByteArray?,
    onNew: () -> Unit,
    onOpen: (Recipe) -> Unit,
    onMakeRecipe: (Recipe) -> Unit,
    onImport: (String) -> Unit,
    modifier: Modifier,
) {
    val saved = recipes.filter { it.isRecipe }
    val earlier = recipes.filterNot { it.isRecipe }
    ListSurface(modifier) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.menu_recipes), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                Button(onClick = onNew) {
                    Icon(HearthIcons.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.menu_new_recipe))
                }
            }
            Spacer(Modifier.size(12.dp))
            // A link from a cooking site: name, ingredients, steps and photo come along.
            AddRow(stringResource(Res.string.recipe_link_hint), onImport, muted = true, compact = true)
            Spacer(Modifier.size(12.dp))
            if (saved.isEmpty()) EmptyHint(stringResource(Res.string.menu_recipes_empty), Modifier.padding(horizontal = 4.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(saved, key = { it.key }) { recipe -> RecipeCard(recipe, photoVersion, loadPhoto) { onOpen(recipe) } }
            }
            if (earlier.isNotEmpty()) {
                Spacer(Modifier.size(10.dp))
                Text(stringResource(Res.string.menu_earlier), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    earlier.take(12).forEach { dish -> ChoiceChip(dish.name, false, { onMakeRecipe(dish) }, icon = HearthIcons.Add) }
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, photoVersion: Int, loadPhoto: suspend (String) -> ByteArray?, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.background).clickable(onClick = onClick)) {
        RecipeThumb(recipe, recipe.name, photoVersion, loadPhoto, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(recipe.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (recipe.ingredients.isEmpty()) stringResource(Res.string.menu_no_ingredients)
                else pluralStringResource(Res.plurals.menu_x_ingredients, recipe.ingredients.size, recipe.ingredients.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The recipe's photo, or a warm placeholder with its first letter. Loaded lazily, small on purpose. */
@Composable
private fun RecipeThumb(recipe: Recipe?, name: String, photoVersion: Int, loadPhoto: suspend (String) -> ByteArray?, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, recipe?.key, recipe?.hasPhoto, photoVersion) {
        value = if (recipe?.hasPhoto == true) runCatching { loadPhoto(recipe.key)?.decodeToImageBitmap() }.getOrNull() else null
    }
    val s = Topic.MENU.style()
    Box(modifier.background(s.tint), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, color = s.ink)
        }
    }
}

// ---------- choosing a meal for a day ----------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChooseMealSheet(
    day: LocalDate,
    today: LocalDate,
    lang: Lang,
    recipes: List<Recipe>,
    photoVersion: Int,
    loadPhoto: suspend (String) -> ByteArray?,
    onRecipe: (Recipe) -> Unit,
    onDish: (String, String, Boolean) -> Unit,
    onNewRecipe: () -> Unit,
    onDismiss: () -> Unit,
) {
    var dish by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf("") }
    var alsoRecipe by remember { mutableStateOf(true) }
    FormSheet(dirty = dish.isNotBlank() || ingredients.isNotBlank(), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().widthIn(max = 900.dp).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 28.dp).navigationBarsPadding()) {
            Text(stringResource(Res.string.menu_choose_title, menuDayName(day, today, lang)), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.size(14.dp))
            if (recipes.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    recipes.take(24).forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.background)
                                .clickable { onRecipe(r); onDismiss() }.padding(end = 16.dp),
                        ) {
                            RecipeThumb(r, r.name, photoVersion, loadPhoto, Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text(r.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Spacer(Modifier.size(18.dp))
            }
            Text(stringResource(Res.string.menu_other_dish), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(dish, { dish = it }, placeholder = { Text(stringResource(Res.string.meal_dish_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(ingredients, { ingredients = it }, placeholder = { Text(stringResource(Res.string.meal_ingredients_hint)) }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { alsoRecipe = !alsoRecipe }) {
                Checkbox(alsoRecipe, { alsoRecipe = it })
                Text(stringResource(Res.string.menu_also_recipe), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onDish(dish.trim(), ingredients.trim(), alsoRecipe); onDismiss() }, enabled = dish.isNotBlank()) { Text(stringResource(Res.string.save)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onDismiss(); onNewRecipe() }) {
                    Icon(HearthIcons.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.menu_new_recipe))
                }
            }
        }
    }
}

// ---------- one recipe ----------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecipeSheet(
    recipe: Recipe,
    today: LocalDate,
    lang: Lang,
    photoVersion: Int,
    loadPhoto: suspend (String) -> ByteArray?,
    onPlan: (LocalDate) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().widthIn(max = 900.dp).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 28.dp).navigationBarsPadding()) {
            if (recipe.hasPhoto) {
                RecipeThumb(recipe, recipe.name, photoVersion, loadPhoto, Modifier.fillMaxWidth().heightIn(max = 320.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp)))
                Spacer(Modifier.size(16.dp))
            }
            Text(recipe.name, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.size(10.dp))
            Text(stringResource(Res.string.menu_ingredients), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            if (recipe.ingredients.isEmpty()) EmptyHint(stringResource(Res.string.menu_no_ingredients))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recipe.ingredients.forEach { Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) }
            }
            recipe.notes?.let {
                Spacer(Modifier.size(14.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.size(18.dp))
            Text(stringResource(Res.string.menu_on_menu), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (0 until 7).map { today.plusDays(it) }.forEach { d -> ChoiceChip(menuDayName(d, today, lang), false, { onPlan(d); onDismiss() }) }
            }
            Spacer(Modifier.size(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { onDismiss(); onEdit() }) { Text(stringResource(Res.string.edit)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(HearthIcons.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.delete))
                }
            }
        }
    }
}

// ---------- making a recipe ----------

/**
 * Tell what you cooked (type or speak), add a photo. The name and ingredients fill in from the story; people
 * can still change them. [existing] is edited (or a remembered dish made into a recipe).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecipeEditor(
    existing: Recipe?,
    speechLanguage: String,
    photoVersion: Int,
    loadPhoto: suspend (String) -> ByteArray?,
    onSave: (String, List<String>, String?, String?, ByteArray?) -> Unit,
    onDismiss: () -> Unit,
) {
    var story by remember { mutableStateOf(existing?.notes ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var nameTouched by remember { mutableStateOf(existing != null) }
    var ingredients by remember { mutableStateOf(existing?.ingredients ?: emptyList()) }
    var ingredientsTouched by remember { mutableStateOf(existing?.ingredients?.isNotEmpty() == true) }
    var newPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var listening by remember { mutableStateOf(false) }
    val picker = rememberPhotoPicker { newPhoto = it }

    fun onStory(text: String) {
        story = text
        val hint = RecipeWords.parse(text)
        if (!nameTouched) hint.name?.let { name = it }
        if (!ingredientsTouched && hint.ingredients.isNotEmpty()) ingredients = hint.ingredients
    }

    // Typed, spoken, picked or changed: anything that would be lost if the sheet closed now.
    val dirty = story != (existing?.notes ?: "") || name != (existing?.name ?: "") ||
        ingredients != (existing?.ingredients ?: emptyList<String>()) || newPhoto != null
    FormSheet(dirty = dirty, onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().widthIn(max = 900.dp).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 28.dp).navigationBarsPadding()) {
            Text(stringResource(Res.string.menu_recipe_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(Res.string.menu_recipe_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(16.dp))

            // Photo
            val shownPhoto by produceState<ImageBitmap?>(null, newPhoto, existing?.key, photoVersion) {
                value = newPhoto?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
                    ?: if (newPhoto == null && existing?.hasPhoto == true) runCatching { loadPhoto(existing.key)?.decodeToImageBitmap() }.getOrNull() else null
            }
            val removed = newPhoto?.isEmpty() == true
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 160.dp, height = 120.dp).clip(RoundedCornerShape(20.dp)).background(Topic.MENU.style().tint), contentAlignment = Alignment.Center) {
                    val image = shownPhoto
                    if (image != null && !removed) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(HearthIcons.Camera, null, tint = Topic.MENU.style().ink, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    picker.takePhoto?.let { take ->
                        Button(onClick = take) {
                            Icon(HearthIcons.Camera, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (shownPhoto != null && !removed) Res.string.menu_photo_other else Res.string.menu_photo_take))
                        }
                    }
                    OutlinedButton(onClick = picker.choosePhoto) { Text(stringResource(Res.string.menu_photo_choose)) }
                    if (shownPhoto != null && !removed) TextButton(onClick = { newPhoto = ByteArray(0) }) { Text(stringResource(Res.string.menu_photo_remove)) }
                }
            }
            Spacer(Modifier.size(18.dp))

            // Story (typed or spoken)
            Text(stringResource(Res.string.menu_story_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(6.dp))
            Row(verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    story, ::onStory,
                    placeholder = { Text(stringResource(Res.string.menu_story_hint)) },
                    minLines = 3,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                VoiceButton(
                    language = speechLanguage,
                    listening = listening,
                    onListening = { listening = it },
                    onPartial = {},
                    onFinal = { heard -> onStory(listOf(story.trim(), heard).filter { it.isNotEmpty() }.joinToString(". ")) },
                )
            }
            Spacer(Modifier.size(14.dp))

            // Name and ingredients
            OutlinedTextField(name, { name = it; nameTouched = true }, label = { Text(stringResource(Res.string.menu_recipe_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(12.dp))
            Text(stringResource(Res.string.menu_ingredients), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ingredients.forEach { ingredient ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)).padding(start = 14.dp),
                    ) {
                        Text(ingredient, style = MaterialTheme.typography.labelLarge)
                        DeleteButton({ ingredients = ingredients - ingredient; ingredientsTouched = true })
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            AddRow(stringResource(Res.string.menu_ingredient_add), { added ->
                ingredients = (ingredients + app.hearth.core.tools.week.splitIngredients(added)).distinctBy { it.lowercase() }
                ingredientsTouched = true
            }, muted = true, compact = true)
            Spacer(Modifier.size(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onSave(name.trim(), ingredients, story.trim().ifEmpty { null }, existing?.key, newPhoto); onDismiss() }, enabled = name.isNotBlank()) {
                    Icon(HearthIcons.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.save))
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        }
    }
}
