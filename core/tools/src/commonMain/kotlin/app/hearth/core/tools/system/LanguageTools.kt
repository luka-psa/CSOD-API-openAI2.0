package app.hearth.core.tools.system

import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.model.Lang
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolResult
import kotlinx.serialization.Serializable

// Tablet-only: the app's language. Dutch (Flemish) unless the family switches to English.

object AppSettings {
    /** "nl" or "en"; missing means Dutch. */
    const val LANGUAGE = "app.lang"
}

@Serializable
data class SetLanguageArgs(@Describe("NL (Dutch, Flemish) or EN (English).") val language: Lang)

@Serializable
data class LanguageResult(val language: Lang, override val summary: String) : ToolResult

class SetLanguageTool(private val settings: SettingsRepository) : Tool<SetLanguageArgs, LanguageResult> {
    override val name = NAME
    override val description = "Switch the app between Dutch (Flemish) and English (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = SetLanguageArgs.serializer()
    override val resultSerializer = LanguageResult.serializer()

    override suspend fun run(args: SetLanguageArgs, ctx: ToolContext): LanguageResult {
        settings.put(AppSettings.LANGUAGE, if (args.language == Lang.EN) "en" else "nl")
        // Said in the new language: that's the one the family will read from now on.
        val summary = if (args.language == Lang.EN) "The app is now in English." else "De app is nu in het Nederlands."
        return LanguageResult(args.language, summary)
    }

    companion object {
        const val NAME = "set_language"
    }
}
