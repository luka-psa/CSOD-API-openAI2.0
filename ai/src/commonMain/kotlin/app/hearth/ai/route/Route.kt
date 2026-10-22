package app.hearth.ai.route

import app.hearth.core.model.Lang
import app.hearth.core.model.Section
import app.hearth.core.tools.Describe
import kotlinx.serialization.Serializable

/** What the person wants to do with the thing they mentioned. */
enum class Intent {
    /** Something new to remember, buy, book, do or agree on. */
    ADD,
    /** A question about what's open, on the list, planned. */
    ASK,
    /** Something got done, bought, booked or is no longer needed. */
    DONE,
    /** Greetings, thanks, small talk: nothing to store. */
    CHAT,
}

/** Stage 1 output: where a message belongs. Every field is required (strict JSON schema). */
@Serializable
data class RouteDecision(
    @Describe("The section the message belongs to.")
    val section: Section,
    @Describe("What the person wants.")
    val intent: Intent,
    @Describe("The message rewritten as a short, clear item in the person's language, e.g. 'Tandarts bellen voor Mia'. Keep names and details.")
    val text: String,
    @Describe("'nl' or 'en': the language the person wrote in.")
    val language: String,
    @Describe("Only for intent CHAT: a short, warm reply in the person's language. Otherwise an empty string.")
    val reply: String,
) {
    val lang: Lang get() = Lang.fromTag(language)
}
