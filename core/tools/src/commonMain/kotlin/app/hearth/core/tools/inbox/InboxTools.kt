package app.hearth.core.tools.inbox

import app.hearth.core.data.inbox.InboxRepository
import app.hearth.core.model.InboxItem
import app.hearth.core.model.Section
import app.hearth.core.model.normalizeName
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolResult
import kotlinx.serialization.Serializable

// ---------- capture_to_inbox ----------

@Serializable
data class CaptureArgs(
    @Describe("What the person said, kept close to their own words, e.g. 'Book the dentist for Mia'.")
    val text: String,
    @Describe("The section it most likely belongs to. Use OTHER when unsure.")
    val section: Section = Section.OTHER,
)

@Serializable
data class CaptureResult(val item: InboxItem, val added: Boolean, override val summary: String) : ToolResult

class CaptureToInboxTool(private val repo: InboxRepository) : Tool<CaptureArgs, CaptureResult> {
    override val name = NAME
    override val description =
        "Keep something in the shared inbox so it is not forgotten, labelled with its likely section. " +
            "Use it for anything that has no better tool yet (appointments, admin, projects, chores, house rules, " +
            "frustrations) or that is too vague to act on."
    override val argsSerializer = CaptureArgs.serializer()
    override val resultSerializer = CaptureResult.serializer()

    override suspend fun run(args: CaptureArgs, ctx: ToolContext): CaptureResult {
        val text = args.text.trim()
        if (text.isEmpty()) throw ToolException(ctx.t("Wat moet ik onthouden?", "What should I keep?"))
        val (item, added) = repo.capture(text, args.section, ctx.actorMemberId)
        val label = item.section.label(ctx.lang)
        val summary = if (added) {
            ctx.t("In de inbox gezet: “${item.text}” ($label)", "Kept in the inbox: “${item.text}” ($label)")
        } else {
            ctx.t("Staat al in de inbox: “${item.text}”", "Already in the inbox: “${item.text}”")
        }
        return CaptureResult(item, added, summary)
    }

    companion object {
        const val NAME = "capture_to_inbox"
    }
}

// ---------- list_inbox ----------

@Serializable
class ListInboxArgs

@Serializable
data class ListInboxResult(val items: List<InboxItem>, override val summary: String) : ToolResult

class ListInboxTool(private val repo: InboxRepository) : Tool<ListInboxArgs, ListInboxResult> {
    override val name = NAME
    override val description = "Show what is waiting in the shared inbox, with the section each item belongs to."
    override val argsSerializer = ListInboxArgs.serializer()
    override val resultSerializer = ListInboxResult.serializer()

    override suspend fun run(args: ListInboxArgs, ctx: ToolContext): ListInboxResult {
        val items = repo.openItems()
        val summary = if (items.isEmpty()) {
            ctx.t("De inbox is leeg.", "The inbox is empty.")
        } else {
            items.joinToString("\n") { "${it.text} (${it.section.label(ctx.lang)})" }
        }
        return ListInboxResult(items, summary)
    }

    companion object {
        const val NAME = "list_inbox"
    }
}

// ---------- archive_inbox_item ----------

@Serializable
data class ArchiveInboxArgs(
    @Describe("Ids of inbox items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of inbox items, when ids are not known.")
    val texts: List<String> = emptyList(),
)

@Serializable
data class ArchiveInboxResult(val items: List<InboxItem>, override val summary: String) : ToolResult

class ArchiveInboxItemTool(private val repo: InboxRepository) : Tool<ArchiveInboxArgs, ArchiveInboxResult> {
    override val name = NAME
    override val description = "Mark inbox items as handled: they leave the inbox (kept in history, not deleted)."
    override val argsSerializer = ArchiveInboxArgs.serializer()
    override val resultSerializer = ArchiveInboxResult.serializer()

    override suspend fun run(args: ArchiveInboxArgs, ctx: ToolContext): ArchiveInboxResult {
        val wanted = args.texts.map(::normalizeName).toSet()
        val byText = if (wanted.isEmpty()) emptyList() else repo.openItems().filter { normalizeName(it.text) in wanted }
        val items = (repo.itemsByIds(args.itemIds).filterNot { it.archived } + byText).distinctBy { it.id }
        if (items.isEmpty()) throw ToolException(ctx.t("Dat vind ik niet in de inbox.", "I can't find that in the inbox."))
        repo.archive(items.map { it.id })
        val texts = items.joinToString(", ") { "“${it.text}”" }
        return ArchiveInboxResult(items, ctx.t("Afgehandeld: $texts", "Handled: $texts"))
    }

    companion object {
        const val NAME = "archive_inbox_item"
    }
}
