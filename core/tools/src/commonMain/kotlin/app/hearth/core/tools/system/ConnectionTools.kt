package app.hearth.core.tools.system

import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolResult
import kotlinx.serialization.Serializable

// Tablet-only: how Hearth connects to the family's own ChatGPT or Claude. Never offered over MCP.

@Serializable
data class SaveConnectionArgs(
    @Describe("Turn the connection to ChatGPT/Claude on or off.")
    val enabled: Boolean? = null,
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
        val c = settings.saveConnection(args.enabled)
        val summary = when {
            c.relayUrl == null -> ctx.t(NO_RELAY_NL, NO_RELAY_EN)
            c.enabled -> ctx.t("Verbinding met jullie AI staat aan.", "The connection to your AI is on.")
            else -> ctx.t("Verbinding met jullie AI staat uit.", "The connection to your AI is off.")
        }
        return ConnectionResult(c.enabled, c.connectorUrl, summary)
    }

    companion object {
        const val NAME = "save_connection"
    }
}

/** The relay's address is built in (HEARTH_RELAY_URL); a build without one says so, and nobody types it. */
internal const val NO_RELAY_NL = "Deze versie van Hearth is gebouwd zonder koppelserver. Installeer een nieuwere versie."
internal const val NO_RELAY_EN = "This version of Hearth was built without a relay. Install a newer version."
