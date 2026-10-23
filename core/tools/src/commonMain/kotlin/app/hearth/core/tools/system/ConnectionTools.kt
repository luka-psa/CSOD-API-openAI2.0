package app.hearth.core.tools.system

import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolResult
import kotlinx.serialization.Serializable

// Tablet-only: how Hearth connects to the family's own ChatGPT or Claude. Never offered over MCP.

@Serializable
data class SaveConnectionArgs(
    @Describe("Turn the connection to ChatGPT/Claude on or off.")
    val enabled: Boolean? = null,
    @Describe("Another relay's address (advanced); empty goes back to the built-in one.")
    val relayUrl: String? = null,
)

@Serializable
data class ConnectionResult(val enabled: Boolean, val connectorUrl: String?, override val summary: String) : ToolResult

class SaveConnectionTool(private val settings: SettingsRepository) : Tool<SaveConnectionArgs, ConnectionResult> {
    override val name = NAME
    override val description = "Change how Hearth connects to the family's AI (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = SaveConnectionArgs.serializer()
    override val resultSerializer = ConnectionResult.serializer()

    override suspend fun run(args: SaveConnectionArgs, ctx: ToolContext): ConnectionResult {
        val url = args.relayUrl?.trim()?.let { if (it.isEmpty() || it.startsWith("http")) it else "https://$it" }
        if (!url.isNullOrEmpty() && !Regex("^https?://[^/\\s]+[^\\s]*$").matches(url)) {
            throw ToolException(ctx.t("Dat adres lijkt niet te kloppen.", "That address doesn't look right."))
        }
        val c = settings.saveConnection(args.enabled, url)
        val summary = when {
            c.relayUrl == null -> ctx.t("Deze versie heeft nog geen relay. Vul een adres in onder Geavanceerd.", "This build has no relay yet. Enter an address under Advanced.")
            c.enabled -> ctx.t("Verbinding met jullie AI staat aan.", "The connection to your AI is on.")
            else -> ctx.t("Verbinding met jullie AI staat uit.", "The connection to your AI is off.")
        }
        return ConnectionResult(c.enabled, c.connectorUrl, summary)
    }

    companion object {
        const val NAME = "save_connection"
    }
}
