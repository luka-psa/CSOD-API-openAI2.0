package app.hearth.core.data.week

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.withWriteTransaction
import app.hearth.core.data.HearthDatabase
import app.hearth.core.model.Huddle
import app.hearth.core.model.Meal
import app.hearth.core.model.Recipe
import app.hearth.core.model.newId
import app.hearth.core.model.normalizeName
import app.hearth.core.model.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "meal", indices = [Index("date", unique = true), Index("normalizedDish")])
data class MealEntity(
    @PrimaryKey val id: String,
    val date: String,
    val dish: String,
    val normalizedDish: String,
    /** One ingredient per line. */
    val ingredients: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toModel() = Meal(id, date, dish, ingredients.lines().filter { it.isNotBlank() })
}

/** Every dish we know: recipes saved on purpose, and dishes remembered from the meal plan. */
@Entity(tableName = "dish")
data class DishEntity(
    @PrimaryKey val normalizedName: String,
    val name: String,
    val ingredients: String,
    /** ISO date it was last on the menu; empty when never planned. */
    val lastUsedOn: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "NULL") val notes: String? = null,
    @ColumnInfo(defaultValue = "0") val hasPhoto: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isRecipe: Boolean = false,
) {
    fun toModel() = Meal(normalizedName, lastUsedOn, name, ingredients.lines().filter { it.isNotBlank() })
    fun toRecipe() = Recipe(normalizedName, name, ingredients.lines().filter { it.isNotBlank() }, notes, hasPhoto, isRecipe, lastUsedOn.ifEmpty { null })
}

/** A recipe's photo (JPEG, at most 800 px), kept apart so lists stay light. */
@Entity(tableName = "recipe_photo")
data class RecipePhotoEntity(
    @PrimaryKey val recipeKey: String,
    val jpeg: ByteArray,
    val updatedAt: Long,
)

@Entity(tableName = "huddle", indices = [Index("heldOn")])
data class HuddleEntity(
    @PrimaryKey val id: String,
    val heldOn: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toModel() = Huddle(id, heldOn, createdAt)
}

@Dao
interface WeekDao {
    @Query("SELECT * FROM meal WHERE date >= :from AND date < :until ORDER BY date")
    fun observeMeals(from: String, until: String): Flow<List<MealEntity>>

    @Query("SELECT * FROM meal WHERE date >= :from AND date < :until ORDER BY date")
    suspend fun meals(from: String, until: String): List<MealEntity>

    @Query("SELECT * FROM dish ORDER BY lastUsedOn DESC LIMIT 60")
    fun observeDishes(): Flow<List<DishEntity>>

    @Query("SELECT * FROM dish WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun dish(normalizedName: String): DishEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDish(dish: DishEntity)

    @Query("SELECT * FROM dish ORDER BY isRecipe DESC, name COLLATE NOCASE")
    fun observeRecipes(): Flow<List<DishEntity>>

    @Query("SELECT * FROM dish ORDER BY isRecipe DESC, name COLLATE NOCASE")
    suspend fun allDishes(): List<DishEntity>

    @Query("DELETE FROM dish WHERE normalizedName = :key")
    suspend fun deleteDish(key: String)

    @Query("SELECT jpeg FROM recipe_photo WHERE recipeKey = :key")
    suspend fun photo(key: String): ByteArray?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPhoto(photo: RecipePhotoEntity)

    @Query("DELETE FROM recipe_photo WHERE recipeKey = :key")
    suspend fun deletePhoto(key: String)

    @Query("SELECT * FROM meal WHERE date = :date LIMIT 1")
    suspend fun mealOn(date: String): MealEntity?

    @Insert
    suspend fun insertMeal(meal: MealEntity)

    @Query("DELETE FROM meal WHERE id IN (:ids)")
    suspend fun deleteMeals(ids: List<String>)

    @Query("SELECT * FROM huddle ORDER BY heldOn DESC, createdAt DESC LIMIT 1")
    fun observeLastHuddle(): Flow<HuddleEntity?>

    @Query("SELECT * FROM huddle ORDER BY heldOn DESC, createdAt DESC LIMIT 1")
    suspend fun lastHuddle(): HuddleEntity?

    @Insert
    suspend fun insertHuddle(huddle: HuddleEntity)
}

/** Meal plan and weekly meetings. Writes come from tools only. */
class WeekRepository(
    private val db: HearthDatabase,
    private val clock: () -> Long = ::nowMillis,
) {
    private val dao = db.weekDao()

    fun meals(from: String, until: String): Flow<List<Meal>> = dao.observeMeals(from, until).map { list -> list.map { it.toModel() } }
    suspend fun mealsBetween(from: String, until: String): List<Meal> = dao.meals(from, until).map { it.toModel() }
    suspend fun mealOn(date: String): Meal? = dao.mealOn(date)?.toModel()

    /** Dishes we had before (most recent first), for one-tap reuse. */
    val dishes: Flow<List<Meal>> = dao.observeDishes().map { list -> list.map { it.toModel() } }

    suspend fun earlierDish(dish: String): Meal? = dao.dish(normalizeName(dish))?.toModel()

    /** Recipes first (alphabetical), then dishes remembered from the plan. */
    val recipes: Flow<List<Recipe>> = dao.observeRecipes().map { list -> list.map { it.toRecipe() } }
    suspend fun allRecipes(): List<Recipe> = dao.allDishes().map { it.toRecipe() }
    suspend fun recipe(name: String): Recipe? = dao.dish(normalizeName(name))?.toRecipe()

    /**
     * Saves a recipe. [previousKey] is the recipe being edited: renaming it keeps its photo and history.
     * Returns the recipe and whether it was new.
     */
    suspend fun saveRecipe(name: String, ingredients: List<String>, notes: String?, previousKey: String? = null): Pair<Recipe, Boolean> =
        db.withWriteTransaction {
            val key = normalizeName(name)
            val now = clock()
            val old = previousKey?.let { dao.dish(it) } ?: dao.dish(key)
            if (old != null && old.normalizedName != key) {
                dao.deleteDish(old.normalizedName)
                dao.photo(old.normalizedName)?.let { dao.putPhoto(RecipePhotoEntity(key, it, now)) }
                dao.deletePhoto(old.normalizedName)
            }
            val entity = DishEntity(
                key, name.trim(), ingredients.joinToString("\n") { it.trim() }, old?.lastUsedOn ?: "",
                old?.createdAt ?: now, now, notes?.trim()?.ifEmpty { null }, old?.hasPhoto ?: false, isRecipe = true,
            )
            dao.upsertDish(entity)
            entity.toRecipe() to (old == null || !old.isRecipe)
        }

    suspend fun setPhoto(key: String, jpeg: ByteArray?) = db.withWriteTransaction {
        val dish = dao.dish(key) ?: return@withWriteTransaction
        val now = clock()
        if (jpeg == null) dao.deletePhoto(key) else dao.putPhoto(RecipePhotoEntity(key, jpeg, now))
        dao.upsertDish(dish.copy(hasPhoto = jpeg != null, updatedAt = now))
    }

    suspend fun photo(key: String): ByteArray? = dao.photo(key)

    /** The recipe goes; meals already planned with it stay on the menu. */
    suspend fun deleteRecipe(key: String) = db.withWriteTransaction {
        dao.deletePhoto(key)
        dao.deleteDish(key)
    }

    /** One meal per day: planning a day again replaces what was there. */
    suspend fun setMeal(date: String, dish: String, ingredients: List<String>): Meal = db.withWriteTransaction {
        dao.mealOn(date)?.let { dao.deleteMeals(listOf(it.id)) }
        val now = clock()
        val lines = ingredients.joinToString("\n") { it.trim() }
        val entity = MealEntity(newId(), date, dish.trim(), normalizeName(dish), lines, now, now)
        dao.insertMeal(entity)
        val known = dao.dish(normalizeName(dish))
        dao.upsertDish(
            DishEntity(
                normalizeName(dish), known?.name ?: dish.trim(), lines.ifEmpty { known?.ingredients ?: "" }, date, known?.createdAt ?: now, now,
                known?.notes, known?.hasPhoto ?: false, known?.isRecipe ?: false,
            ),
        )
        entity.toModel()
    }

    suspend fun deleteMeals(ids: List<String>) {
        if (ids.isNotEmpty()) dao.deleteMeals(ids)
    }

    val lastHuddle: Flow<Huddle?> = dao.observeLastHuddle().map { it?.toModel() }
    suspend fun last(): Huddle? = dao.lastHuddle()?.toModel()

    suspend fun recordHuddle(date: String): Huddle {
        val now = clock()
        val entity = HuddleEntity(newId(), date, now, now)
        dao.insertHuddle(entity)
        return entity.toModel()
    }
}
