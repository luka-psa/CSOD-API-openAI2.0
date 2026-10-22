package app.hearth.core.tools.backlog

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BacklogToolsTest {
    private val db = inMemoryHearthDatabase()
    private val backlog = BacklogRepository(db)
    private val tools = HearthTools(ShoppingRepository(db), backlog)

    // Thursday 22 October 2026.
    private val thursday = LocalDate(2026, 10, 22)
    private val nl = ToolContext(ToolSource.CHAT, Lang.NL, today = thursday)
    private val en = nl.copy(lang = Lang.EN)

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun add(text: String, section: Section = Section.OTHER, w: When = When.UNPLANNED, date: String? = null, finishBy: Boolean = false) =
        tools.registry.run(tools.addToBacklog, AddToBacklogArgs(text, section, w, date, finishBy), nl)

    @Test
    fun `adds with a section label and never duplicates`() = runTest {
        val first = add("Tandarts bellen voor Mia", Section.APPOINTMENT)
        assertTrue(first.added)
        assertEquals("Op de backlog: “Tandarts bellen voor Mia” (afspraak)", first.summary)
        assertFalse(add("  tandarts bellen voor mia ", Section.APPOINTMENT).added)
        assertEquals(1, backlog.openItems().size)
    }

    @Test
    fun `adding with a plan says when`() = runTest {
        assertEquals("Op de backlog: “Ramen kuisen” (huishoudtaak) · morgen", add("Ramen kuisen", Section.CHORE, When.TOMORROW).summary)
        assertEquals(
            "Op de backlog: “Belastingbrief” (administratie) · klaar tegen zaterdag",
            add("Belastingbrief", Section.ADMIN, When.DAY, "2026-10-24", finishBy = true).summary,
        )
    }

    @Test
    fun `planning by text moves items between next, days and later`() = runTest {
        add("Lampen ophangen in het toilet", Section.PROJECT)
        val next = tools.registry.run(tools.planBacklogItem, PlanBacklogArgs(texts = listOf("lampen"), `when` = When.NEXT), nl)
        assertEquals("“Lampen ophangen in het toilet” · volgende", next.summary)

        val day = tools.registry.run(tools.planBacklogItem, PlanBacklogArgs(texts = listOf("lampen ophangen in het toilet"), `when` = When.DAY, date = "2026-10-27"), en)
        assertEquals("“Lampen ophangen in het toilet” · Tuesday", day.summary)
        val item = backlog.openItems().single()
        assertEquals(Plan.DAY, item.plan)
        assertEquals("2026-10-27", item.date)

        tools.registry.run(tools.planBacklogItem, PlanBacklogArgs(itemIds = listOf(item.id), `when` = When.LATER), nl)
        assertEquals(Plan.LATER, backlog.openItems().single().plan)
    }

    @Test
    fun `today view includes earlier days without calling them late`() = runTest {
        add("Autokeuring boeken", Section.APPOINTMENT, When.DAY, "2026-10-20")
        add("Gras maaien", Section.CHORE, When.TODAY)
        add("Kast kopen", Section.PROJECT, When.DAY, "2026-10-30")
        add("Lakens", Section.CHORE, When.NEXT)
        val today = tools.registry.run(tools.listBacklog, ListBacklogArgs(BacklogView.TODAY), nl)
        assertEquals("Autokeuring boeken (afspraak, sinds dinsdag)\nGras maaien (huishoudtaak, vandaag)", today.summary)
        assertEquals(listOf("Autokeuring boeken", "Gras maaien"), tools.registry.run(tools.listBacklog, ListBacklogArgs(BacklogView.WEEK), nl).items.map { it.text })
    }

    @Test
    fun `done items leave the backlog, count for today, and can be reopened`() = runTest {
        val item = add("Autokeuring", Section.APPOINTMENT).item
        assertEquals("Gedaan: “Autokeuring”. Mooi zo!", tools.registry.run(tools.completeBacklogItem, BacklogRefArgs(texts = listOf("autokeuring")), nl).summary)
        assertTrue(backlog.openItems().isEmpty())
        assertEquals(1, backlog.doneSince(0).first())
        tools.registry.run(tools.reopenBacklogItem, BacklogRefArgs(itemIds = listOf(item.id)), nl)
        assertEquals(1, backlog.openItems().size)
    }

    @Test
    fun `deleting asks first`() = runTest {
        add("Iets verkeerds")
        val call = ToolCall.of(RemoveBacklogItemTool.NAME, BacklogRefArgs.serializer(), BacklogRefArgs(texts = listOf("Iets verkeerds")))
        val pending = tools.registry.execute(call, nl)
        assertEquals("“Iets verkeerds” van de backlog verwijderen?", pending.summary)
        assertTrue(pending is ToolOutcome.NeedsConfirmation)
        assertEquals(1, backlog.openItems().size)
        tools.registry.execute(call, nl, confirmed = true)
        assertTrue(backlog.openItems().isEmpty())
    }

    @Test
    fun `day needs a date`() = runTest {
        add("x")
        val out = tools.registry.execute(
            ToolCall.of(PlanBacklogItemTool.NAME, PlanBacklogArgs.serializer(), PlanBacklogArgs(texts = listOf("x"), `when` = When.DAY)),
            en,
        )
        assertEquals(ToolOutcome.Failed(PlanBacklogItemTool.NAME, "Which day? (e.g. 2026-10-24)"), out)
    }
}
