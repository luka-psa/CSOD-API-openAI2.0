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
    /** "true" when this device reads the weekoverleg with the sentence model. */
    const val SMART_LISTENING = SettingsRepository.SMART_LISTENING
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

@Serializable
data class SmartListeningArgs(@Describe("True: read the weekoverleg with the sentence model (downloads it once, about 130 MB).") val enabled: Boolean)

@Serializable
data class SmartListeningResult(val enabled: Boolean, override val summary: String) : ToolResult

/**
 * Smarter listening on this device: Luister mee reads what is said with a small sentence model on the device itself,
 * next to the rules. Switching it on downloads the model once; switching it off removes it again.
 */
class SetSmartListeningTool(private val settings: SettingsRepository) : Tool<SmartListeningArgs, SmartListeningResult> {
    override val name = NAME
    override val description = "Switch smarter listening on or off on this device (a sentence model for Luister mee; tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = SmartListeningArgs.serializer()
    override val resultSerializer = SmartListeningResult.serializer()

    override suspend fun run(args: SmartListeningArgs, ctx: ToolContext): SmartListeningResult {
        settings.put(AppSettings.SMART_LISTENING, args.enabled.toString())
        val summary = if (args.enabled) ctx.t("Slimmer luisteren staat aan. Het model komt één keer binnen.", "Smarter listening is on. The model downloads once.")
        else ctx.t("Slimmer luisteren staat uit. De regels luisteren verder.", "Smarter listening is off. The rules keep listening.")
        return SmartListeningResult(args.enabled, summary)
    }

    companion object {
        const val NAME = "set_smart_listening"
    }
}
