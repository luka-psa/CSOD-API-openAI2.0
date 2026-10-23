package app.hearth.core.data.week

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

/** Every dish we ever planned, with its ingredients, so it comes back in one tap even after the plan moved on. */
@Entity(tableName = "dish")
data class DishEntity(
    @PrimaryKey val normalizedName: String,
    val name: String,
    val ingredients: String,
    val lastUsedOn: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toModel() = Meal(normalizedName, lastUsedOn, name, ingredients.lines().filter { it.isNotBlank() })
}

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

    /** One meal per day: planning a day again replaces what was there. */
    suspend fun setMeal(date: String, dish: String, ingredients: List<String>): Meal = db.withWriteTransaction {
        dao.mealOn(date)?.let { dao.deleteMeals(listOf(it.id)) }
        val now = clock()
        val lines = ingredients.joinToString("\n") { it.trim() }
        val entity = MealEntity(newId(), date, dish.trim(), normalizeName(dish), lines, now, now)
        dao.insertMeal(entity)
        val known = dao.dish(normalizeName(dish))
        dao.upsertDish(DishEntity(normalizeName(dish), dish.trim(), lines.ifEmpty { known?.ingredients ?: "" }, date, known?.createdAt ?: now, now))
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
