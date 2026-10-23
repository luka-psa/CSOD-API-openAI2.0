package app.hearth.core.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/** Households already have v2 databases (tablet and browser); their inbox must survive as backlog. */
class MigrationTest {

    @Test
    fun inboxItemsBecomeUnplannedBacklogItems() = runTest {
        val file = File(createTempDirectory("hearth").toFile(), "hearth.db")
        createV2(file)

        val db = Room.databaseBuilder<HearthDatabase>(file.absolutePath).setDriver(BundledSQLiteDriver()).build()
        val items = BacklogRepository(db).openItems()
        db.close()

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("Tandarts bellen", item.text)
        assertEquals(Section.APPOINTMENT, item.section)
        assertEquals(Plan.UNPLANNED, item.plan)
        assertEquals(null, item.date)
        assertEquals(false, item.urgent)
        assertEquals(null, item.parentId)
    }

    /** Builds a v2 database exactly as the v2 schema export describes it, with one inbox item. */
    private fun createV2(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/app.hearth.core.data.HearthDatabase/2.json").readText())
            .jsonObject["database"]!!.jsonObject
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        schema["entities"]!!.jsonArray.forEach { entity ->
            val e = entity.jsonObject
            val table = e["tableName"]!!.jsonPrimitive.content
            connection.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e["indices"]?.jsonArray?.forEach { index ->
                connection.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema["setupQueries"]!!.jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
        connection.execSQL(
            "INSERT INTO inbox_item (id, text, normalizedText, section, archived, archivedAt, addedById, createdAt, updatedAt) " +
                "VALUES ('1', 'Tandarts bellen', 'tandarts bellen', 'APPOINTMENT', 0, NULL, NULL, 1, 1)",
        )
        connection.execSQL("PRAGMA user_version = 2")
        connection.close()
    }
}
