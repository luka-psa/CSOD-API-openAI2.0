package app.hearth.ai

import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolOutcome
import app.hearth.core.tools.ToolRegistry

data class ChatReply(val text: String, val outcomes: List<ToolOutcome>)

/**
 * Routes a chat message to tools. Tier 1 (local rules) only for now; the on-device model and
 * cloud providers plug in behind it in later slices.
 */
class Orchestrator(
    private val registry: ToolRegistry,
    private val parser: LocalIntentParser = LocalIntentParser(),
) {
    suspend fun handle(text: String, ctx: ToolContext): ChatReply {
        val parsed = parser.parse(text)
            ?: return ChatReply(
                ctx.t(
                    "Dat lukt me nog niet. Probeer bijvoorbeeld \"melk\", \"medicijnen bij de apotheek\" of \"wat staat er op de lijst?\".",
                    "I can't do that one yet. Try \"milk\", \"medicine from the pharmacy\" or \"what's on the list?\".",
                ),
                emptyList(),
            )
        val replyCtx = parsed.lang?.let { ctx.copy(lang = it) } ?: ctx
        val outcomes = parsed.calls.map { registry.execute(it, replyCtx) }
        return ChatReply(outcomes.joinToString("\n") { it.summary }, outcomes)
    }
}
