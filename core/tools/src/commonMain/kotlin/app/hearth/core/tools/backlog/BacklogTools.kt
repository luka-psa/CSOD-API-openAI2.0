package app.hearth.core.tools.backlog

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.dayLabel
import app.hearth.core.model.normalizeName
import app.hearth.core.model.plusDays
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolResult
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** When something should happen, in the words people use. */
enum class When { UNPLANNED, NEXT, TODAY, TOMORROW, DAY, LATER }

private data class Resolved(val plan: Plan, val date: LocalDate?)

private fun resolve(whenValue: When, date: String?, ctx: ToolContext): Resolved = when (whenValue) {
    When.UNPLANNED -> Resolved(Plan.UNPLANNED, null)
    When.NEXT -> Resolved(Plan.NEXT, null)
    When.LATER -> Resolved(Plan.LATER, null)
    When.TODAY -> Resolved(Plan.DAY, ctx.today)
    When.TOMORROW -> Resolved(Plan.DAY, ctx.today.plusDays(1))
    When.DAY -> {
        val parsed = date?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
            ?: throw ToolException(ctx.t("Welke dag? (bv. 2026-10-24)", "Which day? (e.g. 2026-10-24)"))
        Resolved(Plan.DAY, parsed)
    }
}

/** "vandaag", "Volgende", "later", "klaar tegen vrijdag". */
fun planLabel(item: BacklogItem, ctx: ToolContext): String {
    val date = item.localDate
    return when (item.plan) {
        Plan.UNPLANNED -> ctx.t("in te plannen", "to plan")
        Plan.NEXT -> ctx.t("volgende", "next up")
        Plan.LATER -> ctx.t("later", "later")
        Plan.DAY -> {
            val day = date?.let { dayLabel(it, ctx.today, ctx.lang) } ?: "?"
            if (item.finishBy) ctx.t("klaar tegen $day", "done by $day") else day
        }
    }
}

/** Finds open items by id, or by text: exact first, then a looser "contains" match. */
private suspend fun BacklogRepository.find(ids: List<String>, texts: List<String>): List<BacklogItem> {
    val open = openItems()
    val byText = texts.flatMap { t ->
        val key = normalizeName(t)
        open.filter { normalizeName(it.text) == key }.ifEmpty {
            open.filter { normalizeName(it.text).contains(key) || key.contains(normalizeName(it.text)) }
        }
    }
    return (itemsByIds(ids) + byText).distinctBy { it.id }
}

private fun quoted(items: List<BacklogItem>) = items.joinToString(", ") { "“${it.text}”" }

@Serializable
data class BacklogItemsResult(val items: List<BacklogItem>, override val summary: String) : ToolResult

@Serializable
data class BacklogRefArgs(
    @Describe("Ids of backlog items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of backlog items, when ids are not known.")
    val texts: List<String> = emptyList(),
)

// ---------- add_to_backlog ----------

@Serializable
data class AddToBacklogArgs(
    @Describe("What needs doing, close to the person's words, e.g. 'Tandarts bellen voor Mia'.")
    val text: String,
    @Describe("The section it belongs to. Use OTHER when unsure.")
    val section: Section = Section.OTHER,
    @Describe("When: UNPLANNED (default), NEXT (soon, no day), TODAY, TOMORROW, DAY (with date) or LATER.")
    val `when`: When = When.UNPLANNED,
    @Describe("Only for DAY: the date as yyyy-MM-dd.")
    val date: String? = null,
    @Describe("True when it must be finished by that day rather than done on it.")
    val finishBy: Boolean = false,
)

@Serializable
data class AddToBacklogResult(val item: BacklogItem, val added: Boolean, override val summary: String) : ToolResult

class AddToBacklogTool(private val repo: BacklogRepository) : Tool<AddToBacklogArgs, AddToBacklogResult> {
    override val name = NAME
    override val description =
        "Put something that needs doing on the shared backlog, labelled with its section and optionally planned " +
            "(next up, a day, or later). Use it for appointments to book, admin, projects, chores, house rules, " +
            "frustrations: anything that is not a shopping item."
    override val argsSerializer = AddToBacklogArgs.serializer()
    override val resultSerializer = AddToBacklogResult.serializer()

    override suspend fun run(args: AddToBacklogArgs, ctx: ToolContext): AddToBacklogResult {
        val text = args.text.trim()
        if (text.isEmpty()) throw ToolException(ctx.t("Wat moet er gebeuren?", "What needs doing?"))
        val (plan, date) = resolve(args.`when`, args.date, ctx)
        val (item, added) = repo.add(text, args.section, plan, date?.toString(), args.finishBy && date != null, ctx.actorMemberId)
        val label = item.section.label(ctx.lang)
        val summary = if (added) {
            val whenText = if (item.plan == Plan.UNPLANNED) "" else " · ${planLabel(item, ctx)}"
            ctx.t("Op de backlog: “${item.text}” ($label)$whenText", "On the backlog: “${item.text}” ($label)$whenText")
        } else {
            ctx.t("Staat al op de backlog: “${item.text}”", "Already on the backlog: “${item.text}”")
        }
        return AddToBacklogResult(item, added, summary)
    }

    companion object {
        const val NAME = "add_to_backlog"
    }
}

// ---------- list_backlog ----------

enum class BacklogView { ALL, TODAY, NEXT, UNPLANNED, WEEK }

@Serializable
data class ListBacklogArgs(
    @Describe("ALL open items (default), TODAY (planned for today or earlier), NEXT, UNPLANNED, or WEEK (next 7 days).")
    val view: BacklogView = BacklogView.ALL,
)

class ListBacklogTool(private val repo: BacklogRepository) : Tool<ListBacklogArgs, BacklogItemsResult> {
    override val name = NAME
    override val description = "Show what is open on the backlog, with section and plan (day, next up, later)."
    override val argsSerializer = ListBacklogArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: ListBacklogArgs, ctx: ToolContext): BacklogItemsResult {
        val weekEnd = ctx.today.plusDays(7)
        val items = repo.openItems().filter { item ->
            val d = item.localDate
            when (args.view) {
                BacklogView.ALL -> true
                BacklogView.TODAY -> item.plan == Plan.DAY && d != null && d <= ctx.today
                BacklogView.NEXT -> item.plan == Plan.NEXT
                BacklogView.UNPLANNED -> item.plan == Plan.UNPLANNED
                BacklogView.WEEK -> item.plan == Plan.DAY && d != null && d < weekEnd
            }
        }.sortedWith(compareBy({ it.plan.ordinal }, { it.date ?: "" }))
        val summary = if (items.isEmpty()) {
            ctx.t("Niets open hier.", "Nothing open here.")
        } else {
            items.joinToString("\n") { "${it.text} (${it.section.label(ctx.lang)}, ${planLabel(it, ctx)})" }
        }
        return BacklogItemsResult(items, summary)
    }

    companion object {
        const val NAME = "list_backlog"
    }
}

// ---------- plan_backlog_item ----------

@Serializable
data class PlanBacklogArgs(
    @Describe("Ids of backlog items.")
    val itemIds: List<String> = emptyList(),
    @Describe("Texts of backlog items, when ids are not known.")
    val texts: List<String> = emptyList(),
    @Describe("NEXT (soon, no day), TODAY, TOMORROW, DAY (with date), LATER, or UNPLANNED.")
    val `when`: When,
    @Describe("Only for DAY: the date as yyyy-MM-dd.")
    val date: String? = null,
    @Describe("True when it must be finished by that day rather than done on it.")
    val finishBy: Boolean = false,
)

class PlanBacklogItemTool(private val repo: BacklogRepository) : Tool<PlanBacklogArgs, BacklogItemsResult> {
    override val name = NAME
    override val description = "Decide when backlog items happen: next up, a specific day (do on / finish by), later, or back to unplanned."
    override val argsSerializer = PlanBacklogArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: PlanBacklogArgs, ctx: ToolContext): BacklogItemsResult {
        val items = repo.find(args.itemIds, args.texts)
        if (items.isEmpty()) throw ToolException(ctx.t("Dat vind ik niet op de backlog.", "I can't find that on the backlog."))
        val (plan, date) = resolve(args.`when`, args.date, ctx)
        val finishBy = args.finishBy && date != null
        repo.plan(items.map { it.id }, plan, date?.toString(), finishBy)
        val updated = items.map { it.copy(plan = plan, date = date?.toString(), finishBy = finishBy) }
        val label = planLabel(updated.first(), ctx)
        return BacklogItemsResult(updated, ctx.t("${quoted(updated)} · $label", "${quoted(updated)} · $label"))
    }

    companion object {
        const val NAME = "plan_backlog_item"
    }
}

// ---------- complete / reopen ----------

class CompleteBacklogItemTool(private val repo: BacklogRepository) : Tool<BacklogRefArgs, BacklogItemsResult> {
    override val name = NAME
    override val description = "Mark backlog items as done (booked, handled, finished). They leave the backlog and count as done today."
    override val argsSerializer = BacklogRefArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: BacklogRefArgs, ctx: ToolContext): BacklogItemsResult {
        val items = repo.find(args.itemIds, args.texts).filterNot { it.done }
        if (items.isEmpty()) throw ToolException(ctx.t("Dat vind ik niet op de backlog.", "I can't find that on the backlog."))
        repo.setDone(items.map { it.id }, done = true)
        return BacklogItemsResult(items.map { it.copy(done = true) }, ctx.t("Gedaan: ${quoted(items)}. Mooi zo!", "Done: ${quoted(items)}. Nice!"))
    }

    companion object {
        const val NAME = "complete_backlog_item"
    }
}

class ReopenBacklogItemTool(private val repo: BacklogRepository) : Tool<BacklogRefArgs, BacklogItemsResult> {
    override val name = NAME
    override val description = "Undo 'done': put backlog items back as open."
    override val argsSerializer = BacklogRefArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override suspend fun run(args: BacklogRefArgs, ctx: ToolContext): BacklogItemsResult {
        val items = repo.itemsByIds(args.itemIds).filter { it.done }
        if (items.isEmpty()) throw ToolException(ctx.t("Niets om terug te zetten.", "Nothing to put back."))
        repo.setDone(items.map { it.id }, done = false)
        return BacklogItemsResult(items.map { it.copy(done = false) }, ctx.t("Terug open: ${quoted(items)}", "Open again: ${quoted(items)}"))
    }

    companion object {
        const val NAME = "reopen_backlog_item"
    }
}

// ---------- remove_backlog_item (destructive) ----------

class RemoveBacklogItemTool(private val repo: BacklogRepository) : Tool<BacklogRefArgs, BacklogItemsResult> {
    override val name = NAME
    override val description = "Delete backlog items that were added by mistake. For finished things use complete_backlog_item."
    override val destructive = true
    override val argsSerializer = BacklogRefArgs.serializer()
    override val resultSerializer = BacklogItemsResult.serializer()

    override fun confirmQuestion(args: BacklogRefArgs, ctx: ToolContext): String {
        val what = args.texts.joinToString(", ") { "“$it”" }.ifEmpty { ctx.t("dit", "this") }
        return ctx.t("$what van de backlog verwijderen?", "Delete $what from the backlog?")
    }

    override suspend fun run(args: BacklogRefArgs, ctx: ToolContext): BacklogItemsResult {
        val items = repo.find(args.itemIds, args.texts)
        if (items.isEmpty()) throw ToolException(ctx.t("Dat vind ik niet op de backlog.", "I can't find that on the backlog."))
        repo.delete(items.map { it.id })
        return BacklogItemsResult(items, ctx.t("Verwijderd: ${quoted(items)}", "Deleted: ${quoted(items)}"))
    }

    companion object {
        const val NAME = "remove_backlog_item"
    }
}
