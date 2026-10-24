package app.hearth.core.tools.system

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.backup.BackupRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolSource
import app.hearth.core.tools.backlog.AddToBacklogArgs
import app.hearth.core.tools.backlog.BacklogRefArgs
import app.hearth.core.tools.backlog.PlanBacklogArgs
import app.hearth.core.tools.backlog.When
import app.hearth.core.tools.food.AddShoppingItemArgs
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Drive backup and Calendar push, with Google faked: the tools' own logic, no network. */
class GoogleToolsTest {
    private class FakeDrive : BackupStore {
        val files = linkedMapOf<String, Pair<BackupFile, String>>()
        var n = 0L
        override suspend fun list() = files.values.map { it.first }
        override suspend fun upload(name: String, json: String): BackupFile {
            val f = BackupFile("f${++n}", name, n, json.length.toLong())
            files[f.id] = f to json
            return f
        }
        override suspend fun download(id: String) = files.getValue(id).second
        override suspend fun delete(id: String) { files.remove(id) }
    }

    private class FakeCalendar : CalendarApi {
        val calendars = mutableMapOf<String, MutableMap<String, CalendarEvent>>()
        var calls = 0
        var made = 0
        override suspend fun ensureCalendar(existingId: String?, name: String): String =
            existingId?.takeIf { it in calendars } ?: "cal${++made}".also { calendars[it] = mutableMapOf() }
        override suspend fun put(calendarId: String, event: CalendarEvent) { calls++; calendars.getValue(calendarId)[event.id] = event }
        override suspend fun delete(calendarId: String, eventId: String) { calls++; calendars.getValue(calendarId).remove(eventId) }
    }

    private val db = inMemoryHearthDatabase()
    private val backlog = BacklogRepository(db)
    private val shopping = ShoppingRepository(db)
    private val settings = SettingsRepository(db)
    private val drive = FakeDrive()
    private val calendar = FakeCalendar()
    private val tools = HearthTools(shopping, backlog, BoardRepository(db), StockRepository(db), WeekRepository(db), settings, app.hearth.core.data.week.MeetingRepository(db), BackupRepository(db), drive, calendar)
    private val ctx = ToolContext(ToolSource.UI, Lang.NL, today = LocalDate(2026, 10, 22))

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `a backup holds everything and a restore puts it back, keeping this tablet's connection`() = runTest {
        shopping.ensureDefaultShops(Lang.NL)
        tools.registry.run(tools.addShoppingItem, AddShoppingItemArgs("melk"), ctx)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Tandarts bellen", Section.APPOINTMENT), ctx)
        val conn = settings.saveConnection(true, "https://relay.example")
        val made = tools.registry.run(tools.backupNow!!, NoArgs(), ctx)
        assertEquals("Back-up staat veilig in Google Drive.", made.summary)
        assertTrue("mcp.householdId" !in drive.files.values.single().second, "the tablet's ids never leave it")

        // Things change after the backup…
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Fiets maken", Section.PROJECT), ctx)
        tools.registry.run(tools.removeBacklogItem, BacklogRefArgs(texts = listOf("tandarts")), ctx, confirmed = true)

        // …and a restore needs a yes first.
        val ask = tools.registry.execute(ToolCall("restore_backup", buildJsonObject { put("id", made.file.id) }), ctx.copy(source = ToolSource.CHAT))
        assertIs<ToolOutcome.NeedsConfirmation>(ask)
        val restored = tools.registry.run(tools.restoreBackup!!, RestoreBackupArgs(made.file.id), ctx, confirmed = true)
        assertEquals("Back-up teruggezet. Alles staat er weer.", restored.summary)
        assertEquals(listOf("Tandarts bellen"), backlog.openItems().map { it.text })
        assertEquals(listOf("Melk"), shopping.openItems().map { it.name })
        assertEquals(conn, settings.currentConnection())
    }

    @Test
    fun `restoring an older backup keeps filling the same calendar`() = runTest {
        val made = tools.registry.run(tools.backupNow!!, NoArgs(), ctx)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Tandarts", Section.APPOINTMENT, `when` = When.TOMORROW), ctx)
        tools.registry.run(tools.syncCalendar, NoArgs(), ctx)
        tools.registry.run(tools.restoreBackup!!, RestoreBackupArgs(made.file.id), ctx, confirmed = true)
        val after = tools.registry.run(tools.syncCalendar, NoArgs(), ctx)
        assertEquals(listOf("cal1"), calendar.calendars.keys.toList())
        assertEquals(1, after.removed)
        assertTrue(calendar.calendars.getValue("cal1").isEmpty())
    }

    @Test
    fun `only the newest backups are kept and a broken file is refused kindly`() = runTest {
        repeat(9) { tools.registry.run(tools.backupNow!!, NoArgs(), ctx) }
        assertEquals(BackupNowTool.KEEP, drive.files.size)
        assertEquals("f9", tools.registry.run(tools.listBackups, NoArgs(), ctx).backups.first().id)

        drive.files["bad"] = BackupFile("bad", "x", 99) to "{nope"
        val e = assertFailsWith<ToolException> { tools.registry.run(tools.restoreBackup!!, RestoreBackupArgs("bad"), ctx, confirmed = true) }
        assertEquals("Die back-up kan deze versie van Hearth niet lezen.", e.userMessage)
    }

    @Test
    fun `without Google sign-in the tools say so`() = runTest {
        val offline = HearthTools(shopping, backlog, BoardRepository(db), StockRepository(db), WeekRepository(db), settings, app.hearth.core.data.week.MeetingRepository(db), BackupRepository(db))
        val e = assertFailsWith<ToolException> { offline.registry.run(offline.backupNow!!, NoArgs(), ctx) }
        assertEquals("Meld je eerst aan bij Google.", e.userMessage)
    }

    @Test
    fun `planned days go to Hearth's calendar, only changes are sent`() = runTest {
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Tandarts", Section.APPOINTMENT), ctx)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Belastingbrief", Section.ADMIN), ctx)
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs("Garage opruimen", Section.PROJECT), ctx)
        tools.registry.run(tools.planBacklogItem, PlanBacklogArgs(texts = listOf("tandarts"), `when` = When.DAY, date = "2026-10-23"), ctx)
        tools.registry.run(tools.planBacklogItem, PlanBacklogArgs(texts = listOf("belasting"), `when` = When.DAY, date = "2026-10-30", finishBy = true), ctx)

        val first = tools.registry.run(tools.syncCalendar, NoArgs(), ctx)
        assertEquals(2, first.added)
        val events = calendar.calendars.getValue("cal1").values.sortedBy { it.date }
        assertEquals(listOf("Tandarts" to "2026-10-23", "Klaar tegen vandaag: Belastingbrief" to "2026-10-30"), events.map { it.title to it.date })
        assertTrue(events.all { e -> e.id.all { it in '0'..'9' || it in 'a'..'v' } })

        val calls = calendar.calls
        assertEquals("Google Agenda is bij.", tools.registry.run(tools.syncCalendar, NoArgs(), ctx).summary)
        assertEquals(calls, calendar.calls)

        tools.registry.run(tools.planBacklogItem, PlanBacklogArgs(texts = listOf("tandarts"), `when` = When.DAY, date = "2026-10-27"), ctx)
        tools.registry.run(tools.completeBacklogItem, BacklogRefArgs(texts = listOf("belasting")), ctx)
        val next = tools.registry.run(tools.syncCalendar, NoArgs(), ctx)
        assertEquals(Triple(0, 1, 1), Triple(next.added, next.changed, next.removed))
        assertEquals(listOf("2026-10-27"), calendar.calendars.getValue("cal1").values.map { it.date })

        // The calendar was deleted in Google: a new one is made and everything pushed again.
        calendar.calendars.clear()
        assertEquals(1, tools.registry.run(tools.syncCalendar, NoArgs(), ctx).added)
    }
}
