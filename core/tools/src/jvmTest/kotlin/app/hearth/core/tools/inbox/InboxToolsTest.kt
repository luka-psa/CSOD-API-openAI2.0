package app.hearth.core.tools.inbox

import app.hearth.core.data.HearthDatabase
import app.hearth.core.data.food.ShoppingRepository
import app.hearth.core.data.inMemoryHearthDatabase
import app.hearth.core.data.inbox.InboxRepository
import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.tools.HearthTools
import app.hearth.core.tools.ToolCall
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InboxToolsTest {
    private val db: HearthDatabase = inMemoryHearthDatabase()
    private val inbox = InboxRepository(db)
    private val tools = HearthTools(ShoppingRepository(db), inbox)
    private val nl = ToolContext(ToolSource.CHAT, Lang.NL)
    private val en = ToolContext(ToolSource.CHAT, Lang.EN)

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun capture(text: String, section: Section = Section.OTHER, ctx: ToolContext = nl) =
        tools.registry.run(tools.captureToInbox, CaptureArgs(text, section), ctx)

    @Test
    fun `captures with a section label and never duplicates`() = runTest {
        val first = capture("Tandarts bellen voor Mia", Section.APPOINTMENT)
        assertTrue(first.added)
        assertEquals("In de inbox gezet: “Tandarts bellen voor Mia” (afspraak)", first.summary)

        val again = capture("  tandarts bellen voor mia ", Section.APPOINTMENT)
        assertFalse(again.added)
        assertEquals(1, inbox.openItems().size)
        assertEquals(listOf(Section.APPOINTMENT), inbox.open.first().map { it.section })
    }

    @Test
    fun `lists in both languages`() = runTest {
        assertEquals("The inbox is empty.", tools.registry.run(tools.listInbox, ListInboxArgs(), en).summary)
        capture("Lampen ophangen in het toilet", Section.PROJECT)
        capture("Belastingbrief", Section.ADMIN)
        assertEquals(
            "Lampen ophangen in het toilet (project)\nBelastingbrief (administratie)",
            tools.registry.run(tools.listInbox, ListInboxArgs(), nl).summary,
        )
    }

    @Test
    fun `archiving by text or id takes items out of the inbox`() = runTest {
        val dentist = capture("Tandarts bellen", Section.APPOINTMENT).item
        capture("Autokeuring", Section.APPOINTMENT)
        val byText = tools.registry.run(tools.archiveInboxItem, ArchiveInboxArgs(texts = listOf("autokeuring")), nl)
        assertEquals("Afgehandeld: “Autokeuring”", byText.summary)
        tools.registry.run(tools.archiveInboxItem, ArchiveInboxArgs(itemIds = listOf(dentist.id)), nl)
        assertTrue(inbox.openItems().isEmpty())

        val missing = tools.registry.execute(
            ToolCall.of(ArchiveInboxItemTool.NAME, ArchiveInboxArgs.serializer(), ArchiveInboxArgs(texts = listOf("x"))),
            en,
        )
        assertEquals(ToolOutcome.Failed(ArchiveInboxItemTool.NAME, "I can't find that in the inbox."), missing)
    }

    @Test
    fun `section is optional for callers`() = runTest {
        val outcome = tools.registry.execute(
            ToolCall("capture_to_inbox", kotlinx.serialization.json.buildJsonObject { put("text", kotlinx.serialization.json.JsonPrimitive("iets")) }),
            nl,
        )
        assertEquals("In de inbox gezet: “iets” (nog te sorteren)", outcome.summary)
    }
}
