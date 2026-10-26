package app.hearth.core.tools.system

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.backup.BackupRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.sync.SyncRepository
import app.hearth.core.data.week.MeetingRepository
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
import app.hearth.core.tools.food.AddStockItemArgs
import app.hearth.core.tools.food.AddStorageSpotArgs
import app.hearth.core.tools.food.MarkBoughtArgs
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Two devices of one household and a fake relay: pull, merge, push, like git. */
class SyncToolsTest {
    /** The relay, without the sealing (that is tested with the real client). */
    private class FakeCloud : SyncCloud {
        var version = 0L
        var snapshot: String? = null
        val offers = mutableMapOf<String, SyncKeys>()
        /** Runs once, just before the next push lands: another device pushing first. */
        var interject: (suspend () -> Unit)? = null
        var pushes = 0
        override suspend fun available() = true
        override fun newKeys() = SyncKeys("space-${version}", "token", "key")
        override suspend fun pull(keys: SyncKeys, since: Long?) = SyncPulled(version, if (since == version) null else snapshot)
        override suspend fun push(keys: SyncKeys, base: Long, snapshot: String): SyncPushed {
            interject?.let { interject = null; it() }
            if (base != version) return SyncPushed(version, false)
            pushes++
            version++
            this.snapshot = snapshot
            return SyncPushed(version, true)
        }
        override suspend fun offer(keys: SyncKeys) = JoinCode("ABCD234${offers.size}", 1L).also { offers[it.code] = keys }
        override suspend fun claim(code: String) = offers.remove(code.replace("-", "").uppercase()) ?: throw SyncCodeUnknown()
    }

    private val cloud = FakeCloud()
    private val ctx = ToolContext(ToolSource.UI, Lang.NL, today = LocalDate(2026, 10, 26))

    private inner class Device {
        val db = inMemoryHearthDatabase()
        val shopping = ShoppingRepository(db)
        val backlog = BacklogRepository(db)
        val stock = StockRepository(db)
        val settings = SettingsRepository(db)
        val tools = HearthTools(
            shopping, backlog, BoardRepository(db), stock, WeekRepository(db), settings, MeetingRepository(db), BackupRepository(db),
            sync = SyncRepository(db, BackupRepository(db)), syncCloud = cloud,
        )
        suspend fun sync() = tools.registry.run(tools.syncNow!!, NoArgs(), ctx)
        suspend fun shoppingList() = shopping.openItems().map { it.name }.sorted()
        suspend fun bought() = shopping.boughtItems().map { it.name }.sorted()
        suspend fun tasks() = backlog.openItems().map { it.text }.sorted()
    }

    private val tablet = Device()
    private val phone = Device()

    @AfterTest
    fun tearDown() {
        tablet.db.close()
        phone.db.close()
    }

    @Test
    fun `a household on two devices joins with a code, edits from both meet, and nothing anyone added is lost`() = runTest {
        tablet.shopping.ensureDefaultShops(Lang.NL)
        tablet.tools.registry.run(tablet.tools.addShoppingItem, AddShoppingItemArgs("melk"), ctx)
        tablet.tools.registry.run(tablet.tools.addToBacklog, AddToBacklogArgs("Tandarts bellen", Section.APPOINTMENT), ctx)
        tablet.tools.registry.run(tablet.tools.addToBacklog, AddToBacklogArgs("Garage opruimen", Section.PROJECT), ctx)

        // The tablet starts; the phone (with its own defaults and a test item) joins with the code.
        val started = tablet.tools.registry.run(tablet.tools.startSync!!, NoArgs(), ctx)
        assertEquals("ABCD-2340", started.shown)
        assertTrue(started.summary.contains("ABCD-2340"))
        phone.shopping.ensureDefaultShops(Lang.NL)
        phone.tools.registry.run(phone.tools.addToBacklog, AddToBacklogArgs("Iets van de gsm", Section.OTHER), ctx)
        // Joining replaces what is on the phone, so it asks first.
        val ask = phone.tools.registry.execute(ToolCall("join_sync", buildJsonObject { put("code", "abcd-2340") }), ctx.copy(source = ToolSource.CHAT))
        assertIs<ToolOutcome.NeedsConfirmation>(ask)
        assertEquals("Dit toestel neemt het huishouden over. Wat nu hier staat, verdwijnt. Doorgaan?", ask.summary)
        phone.tools.registry.run(phone.tools.joinSync!!, JoinSyncArgs("abcd-2340"), ctx, confirmed = true)
        assertEquals(listOf("Melk"), phone.shoppingList())
        assertEquals(listOf("Garage opruimen", "Tandarts bellen"), phone.tasks())
        // One shop list, not two sets of defaults.
        assertEquals(tablet.shopping.allShops().map { it.id }.toSet(), phone.shopping.allShops().map { it.id }.toSet())
        // A code works once.
        val again = assertFailsWith<ToolException> { Device().tools.let { it.registry.run(it.joinSync!!, JoinSyncArgs("ABCD2340"), ctx, confirmed = true) } }
        assertEquals("Die code klopt niet of is verlopen. Vraag op het andere toestel een nieuwe.", again.userMessage)

        // Both change something; the phone syncs first, then the tablet, then the phone again.
        tablet.tools.registry.run(tablet.tools.markBought, MarkBoughtArgs(names = listOf("melk")), ctx)
        phone.tools.registry.run(phone.tools.addShoppingItem, AddShoppingItemArgs("brood"), ctx)
        assertEquals("Gedeeld: 1 wijziging van dit toestel.", phone.sync().summary)
        assertEquals("Bijgewerkt: 1 wijziging binnen, 1 gedeeld.", tablet.sync().summary)
        phone.sync()
        assertEquals(listOf("Brood"), tablet.shoppingList())
        assertEquals(listOf("Brood"), phone.shoppingList())
        assertEquals(listOf("Melk"), phone.bought())
        assertEquals("Alles is bij.", phone.sync().summary)

        // Deleted on the tablet, untouched on the phone: gone on both.
        tablet.tools.registry.run(tablet.tools.removeBacklogItem, BacklogRefArgs(texts = listOf("garage")), ctx, confirmed = true)
        // Deleted on the tablet while the phone planned it: the plan wins, nothing is lost.
        tablet.tools.registry.run(tablet.tools.removeBacklogItem, BacklogRefArgs(texts = listOf("tandarts")), ctx, confirmed = true)
        phone.tools.registry.run(phone.tools.planBacklogItem, PlanBacklogArgs(texts = listOf("tandarts"), `when` = When.TOMORROW), ctx)
        tablet.sync()
        phone.sync()
        tablet.sync()
        assertEquals(listOf("Tandarts bellen"), tablet.tasks())
        assertEquals(listOf("Tandarts bellen"), phone.tasks())
        assertEquals("2026-10-27", tablet.backlog.openItems().single().date)
    }

    @Test
    fun `the same name on two devices becomes one, and each device keeps its own language`() = runTest {
        tablet.tools.registry.run(tablet.tools.startSync!!, NoArgs(), ctx)
        phone.tools.registry.run(phone.tools.joinSync!!, JoinSyncArgs("ABCD2340"), ctx, confirmed = true)

        // Both make a "Kelder" before they sync, each with something in it.
        tablet.tools.registry.run(tablet.tools.addStorageSpot, AddStorageSpotArgs("Kelder"), ctx)
        tablet.tools.registry.run(tablet.tools.addStockItem, AddStockItemArgs("Wijn", "Kelder"), ctx)
        phone.tools.registry.run(phone.tools.addStorageSpot, AddStorageSpotArgs("Kelder"), ctx)
        phone.tools.registry.run(phone.tools.addStockItem, AddStockItemArgs("Aardappelen", "Kelder"), ctx)
        // The phone reads English; the tablet stays Dutch.
        phone.tools.registry.run(phone.tools.setLanguage, SetLanguageArgs(Lang.EN), ctx)
        tablet.sync()
        phone.sync()
        tablet.sync()

        for (device in listOf(tablet, phone)) {
            val kelder = device.stock.allSpots().filter { it.name == "Kelder" }
            assertEquals(1, kelder.size)
            assertEquals(listOf("Aardappelen", "Wijn"), device.stock.allItems().filter { it.spotId == kelder.single().id }.map { it.name }.sorted())
        }
        assertEquals("EN", phone.settings.currentValue("app.lang")?.uppercase())
        assertEquals(null, tablet.settings.currentValue("app.lang"))
    }

    @Test
    fun `when another device pushes first, the sync pulls again and merges on top`() = runTest {
        tablet.tools.registry.run(tablet.tools.startSync!!, NoArgs(), ctx)
        phone.tools.registry.run(phone.tools.joinSync!!, JoinSyncArgs("ABCD2340"), ctx, confirmed = true)
        tablet.tools.registry.run(tablet.tools.addToBacklog, AddToBacklogArgs("Verzekering bellen", Section.ADMIN), ctx)
        phone.tools.registry.run(phone.tools.addToBacklog, AddToBacklogArgs("Lamp ophangen", Section.PROJECT), ctx)
        // Right before the phone's push lands, the tablet pushes.
        cloud.interject = { tablet.sync() }
        phone.sync()
        tablet.sync()
        assertEquals(listOf("Lamp ophangen", "Verzekering bellen"), phone.tasks())
        assertEquals(listOf("Lamp ophangen", "Verzekering bellen"), tablet.tasks())
        // Nothing changed since: nothing to push.
        val pushes = cloud.pushes
        assertFalse(tablet.tools.syncEngine!!.changed(tablet.tools.syncEngine!!.keys()!!))
        tablet.sync()
        assertEquals(pushes, cloud.pushes)
    }

    @Test
    fun `a relay that lost the household never empties a device, it gets everything back`() = runTest {
        tablet.tools.registry.run(tablet.tools.addToBacklog, AddToBacklogArgs("Tandarts bellen", Section.APPOINTMENT), ctx)
        tablet.tools.registry.run(tablet.tools.startSync!!, NoArgs(), ctx)
        tablet.sync()
        // The relay forgets everything.
        cloud.version = 0
        cloud.snapshot = null
        tablet.sync()
        assertEquals(listOf("Tandarts bellen"), tablet.tasks())
        assertEquals(1L, cloud.version)
        assertTrue(cloud.snapshot!!.contains("Tandarts bellen"))
    }

    @Test
    fun `stopping keeps everything here, and joining with what's here adds it to the household`() = runTest {
        tablet.tools.registry.run(tablet.tools.addToBacklog, AddToBacklogArgs("Tandarts bellen", Section.APPOINTMENT), ctx)
        tablet.tools.registry.run(tablet.tools.startSync!!, NoArgs(), ctx)
        val code = tablet.tools.registry.run(tablet.tools.shareSync!!, NoArgs(), ctx)
        phone.tools.registry.run(phone.tools.addToBacklog, AddToBacklogArgs("Fotoboek maken", Section.PROJECT), ctx)
        val joined = phone.tools.registry.run(phone.tools.joinSync!!, JoinSyncArgs(code.shown, keepThisDevice = true), ctx, confirmed = true)
        assertEquals("Welkom! Dit toestel hoort nu bij het huishouden; jullie dingen staan samen.", joined.summary)
        tablet.sync()
        assertEquals(listOf("Fotoboek maken", "Tandarts bellen"), tablet.tasks())

        assertEquals("Dit toestel synchroniseert niet meer. Alles blijft hier staan.", phone.tools.registry.run(phone.tools.stopSync!!, NoArgs(), ctx).summary)
        assertEquals(listOf("Fotoboek maken", "Tandarts bellen"), phone.tasks())
        val off = assertFailsWith<ToolException> { phone.sync() }
        assertEquals("Dit toestel synchroniseert nog niet. Zet het aan onder Instellingen → Samen op meer toestellen.", off.userMessage)
        // The sync keys never travel in a backup.
        assertFalse(BackupRepository(tablet.db).exportJson().contains("sync.keys"))
    }
}
