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
    const val INTERPRETER = SettingsRepository.INTERPRETER
    const val TRANSCRIBER = SettingsRepository.TRANSCRIBER
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

@Serializable
data class InterpreterArgs(@Describe("True: read the weekoverleg as a whole conversation with the interpreter (downloads it once, about 270 MB).") val enabled: Boolean)

@Serializable
data class InterpreterResult(val enabled: Boolean, override val summary: String) : ToolResult

/**
 * Understanding conversations on this device: a small language model reads the weekoverleg as a whole (what came back,
 * what changed) and names what was decided. Switching it on downloads it once; switching it off removes it again.
 */
class SetInterpreterTool(private val settings: SettingsRepository) : Tool<InterpreterArgs, InterpreterResult> {
    override val name = NAME
    override val description = "Switch understanding conversations on or off on this device (a language model for Luister mee; tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = InterpreterArgs.serializer()
    override val resultSerializer = InterpreterResult.serializer()

    override suspend fun run(args: InterpreterArgs, ctx: ToolContext): InterpreterResult {
        settings.put(AppSettings.INTERPRETER, args.enabled.toString())
        val summary = if (args.enabled) ctx.t("Gesprekken begrijpen staat aan. Het model komt één keer binnen.", "Understanding conversations is on. The model downloads once.")
        else ctx.t("Gesprekken begrijpen staat uit.", "Understanding conversations is off.")
        return InterpreterResult(args.enabled, summary)
    }

    companion object {
        const val NAME = "set_interpreter"
    }
}

@Serializable
data class TranscriberArgs(@Describe("True: Luister mee records the meeting and Whisper writes it down on this device (downloads it once, about 250 MB).") val enabled: Boolean)

@Serializable
data class TranscriberResult(val enabled: Boolean, override val summary: String) : ToolResult

/**
 * Beter uitschrijven: Luister mee records the weekoverleg itself and Whisper writes it down on this device, a few
 * seconds behind the talk, without gaps. Switching it on downloads the model once; switching it off removes it again
 * (Luister mee then uses the device's own speech recognition).
 */
class SetTranscriberTool(private val settings: SettingsRepository) : Tool<TranscriberArgs, TranscriberResult> {
    override val name = NAME
    override val description = "Switch better transcription (Whisper, on this device) for Luister mee on or off (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = TranscriberArgs.serializer()
    override val resultSerializer = TranscriberResult.serializer()

    override suspend fun run(args: TranscriberArgs, ctx: ToolContext): TranscriberResult {
        settings.put(AppSettings.TRANSCRIBER, args.enabled.toString())
        val summary = if (args.enabled) ctx.t("Beter uitschrijven staat aan. Het model komt één keer binnen.", "Better transcription is on. The model downloads once.")
        else ctx.t("Beter uitschrijven staat uit.", "Better transcription is off.")
        return TranscriberResult(args.enabled, summary)
    }

    companion object {
        const val NAME = "set_transcriber"
    }
}
