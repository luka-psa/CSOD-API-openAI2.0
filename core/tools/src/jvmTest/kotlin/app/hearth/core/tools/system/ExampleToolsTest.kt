package app.hearth.core.tools.system

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.board.BoardRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.food.StockRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.week.MeetingRepository
import app.hearth.core.data.week.WeekRepository
import app.hearth.core.model.Ease
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExampleToolsTest {
    private val db = inMemoryHearthDatabase()
    private val backlog = BacklogRepository(db)
    private val shopping = ShoppingRepository(db)
    private val board = BoardRepository(db)
    private val week = WeekRepository(db)
    private val settings = SettingsRepository(db)
    private val tools = HearthTools(shopping, backlog, board, StockRepository(db), week, settings, MeetingRepository(db))
    private val ctx = ToolContext(ToolSource.UI, Lang.NL, today = LocalDate(2026, 10, 22))

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `examples fill every section through the normal tools`() = runTest {
        shopping.ensureDefaultShops(Lang.NL)
        tools.loadExamples.run(NoArgs(), ctx)

        val open = backlog.openItems()
        val sections = open.map { it.section }.toSet()
        listOf(Section.CHORE, Section.HABIT, Section.ADMIN, Section.APPOINTMENT, Section.PROJECT, Section.PROTECTED_TIME, Section.OTHER)
            .forEach { assertTrue(it in sections, "$it missing") }
        val hard = open.single { it.text == "Elke avond de tassen klaarzetten" }
        assertEquals(Ease.HARD, hard.ease)
        assertEquals("Alleen de boekentassen bij de deur", hard.smallStep)
        assertEquals(2, open.single { it.text == "Fiets herstellen" }.postponed)
        assertTrue(open.single { it.text == "Verzekering bellen over de fiets" }.waiting)
        assertTrue(shopping.openItems().any { it.name == "Paracetamol" })
        assertTrue(shopping.openItems().any { it.name == "Eieren" })
        assertEquals("true", settings.currentValue(LoadExamplesTool.LOADED))
        assertFalse(tools.loadExamples.exposeOverMcp)
    }

    @Test
    fun `loading twice adds nothing new to the backlog`() = runTest {
        shopping.ensureDefaultShops(Lang.NL)
        tools.loadExamples.run(NoArgs(), ctx)
        val first = backlog.openItems().size
        tools.loadExamples.run(NoArgs(), ctx)
        assertEquals(first, backlog.openItems().size)
    }
}
