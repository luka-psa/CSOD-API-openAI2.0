package app.hearth.core.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.model.BoardKind
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

    @Test
    fun houseRulesAndFrictionsMoveToTheBoard() = runTest {
        val file = File(createTempDirectory("hearth").toFile(), "hearth.db")
        create(file, 4) { connection ->
            fun backlog(id: String, text: String, section: String) = connection.execSQL(
                "INSERT INTO backlog_item (id, text, normalizedText, section, plan, plannedDate, finishBy, done, doneAt, addedById, " +
                    "createdAt, updatedAt, urgent, waiting, intervalDays, lastDone, parentId) " +
                    "VALUES ('$id', '$text', '${text.lowercase()}', '$section', 'UNPLANNED', NULL, 0, 0, NULL, NULL, 1, 1, 0, 0, NULL, NULL, NULL)",
            )
            backlog("1", "Geen schermen na 20u", "NOTE")
            backlog("2", "Rommel in de gang", "FRICTION")
            backlog("3", "Tandarts", "APPOINTMENT")
        }

        val db = Room.databaseBuilder<HearthDatabase>(file.absolutePath).setDriver(BundledSQLiteDriver()).build()
        val board = BoardRepository(db).allItems()
        val backlog = BacklogRepository(db).openItems()
        db.close()

        assertEquals(listOf("Tandarts"), backlog.map { it.text })
        assertEquals(listOf(BoardKind.RULE to "Geen schermen na 20u", BoardKind.FRICTION to "Rommel in de gang"), board.map { it.kind to it.text })
    }

    /** Builds a v2 database exactly as the v2 schema export describes it, with one inbox item. */
    private fun createV2(file: File) = create(file, 2) { connection ->
        connection.execSQL(
            "INSERT INTO inbox_item (id, text, normalizedText, section, archived, archivedAt, addedById, createdAt, updatedAt) " +
                "VALUES ('1', 'Tandarts bellen', 'tandarts bellen', 'APPOINTMENT', 0, NULL, NULL, 1, 1)",
        )
    }

    /** Builds a database exactly as schema export [version] describes it, then fills it with [rows]. */
    private fun create(file: File, version: Int, rows: (androidx.sqlite.SQLiteConnection) -> Unit) {
        val schema = Json.parseToJsonElement(File("schemas/app.hearth.core.data.HearthDatabase/$version.json").readText())
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
        rows(connection)
        connection.execSQL("PRAGMA user_version = $version")
        connection.close()
    }
}
