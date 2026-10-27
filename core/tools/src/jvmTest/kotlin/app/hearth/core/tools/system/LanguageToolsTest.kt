package app.hearth.core.tools.system

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.Lang
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LanguageToolsTest {
    private val db = inMemoryHearthDatabase()
    private val settings = SettingsRepository(db)
    private val tools = HearthTools(ShoppingRepository(db), BacklogRepository(db), BoardRepository(db), StockRepository(db), WeekRepository(db), settings, MeetingRepository(db))
    private val ctx = ToolContext(ToolSource.UI, Lang.NL)

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `the app is Dutch until it is switched, and says so in the new language`() = runTest {
        assertEquals(null, settings.currentValue(AppSettings.LANGUAGE))
        assertEquals("The app is now in English.", tools.registry.run(tools.setLanguage, SetLanguageArgs(Lang.EN), ctx).summary)
        assertEquals("en", settings.currentValue(AppSettings.LANGUAGE))
        assertEquals("De app is nu in het Nederlands.", tools.registry.run(tools.setLanguage, SetLanguageArgs(Lang.NL), ctx).summary)
        assertEquals("nl", settings.currentValue(AppSettings.LANGUAGE))
        assertFalse(tools.setLanguage.exposeOverMcp)
    }

    @Test
    fun `smarter listening is a setting of this device, never shared`() = runTest {
        assertEquals("Slimmer luisteren staat aan. Het model komt één keer binnen.", tools.registry.run(tools.setSmartListening, SmartListeningArgs(true), ctx).summary)
        assertEquals("true", settings.currentValue(AppSettings.SMART_LISTENING))
        // It stays on this device: each one downloads its own model.
        kotlin.test.assertTrue(AppSettings.SMART_LISTENING in SettingsRepository.DEVICE_ONLY)
        assertFalse(tools.registry.specs.first { it.name == SetSmartListeningTool.NAME }.exposeOverMcp)
        tools.registry.run(tools.setSmartListening, SmartListeningArgs(false), ctx)
        assertEquals("false", settings.currentValue(AppSettings.SMART_LISTENING))
    }
}
