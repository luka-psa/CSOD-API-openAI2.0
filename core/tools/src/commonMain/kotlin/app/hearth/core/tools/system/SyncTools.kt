package app.hearth.core.tools.system

import app.hearth.core.data.backup.BackupRepository
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.data.sync.SyncNeedsUpdate
import app.hearth.core.data.sync.SyncRepository
import app.hearth.core.model.nowMillis
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// Tablet-only: the household on more devices (the tablet, phones, an iPad in the browser). Like git: one
// remote holds the household's snapshot, every device pulls, merges and pushes. The remote is the relay,
// and it only ever sees a sealed box: the key stays on the household's devices.

/** This device's way into the household's sync: where ([space]), the relay's password ([token]) and the key. */
@Serializable
data class SyncKeys(val space: String, val token: String, val key: String)

/** The household as the remote has it. [snapshot] is null when this device already has [version]. */
class SyncPulled(val version: Long, val snapshot: String?)

/** A push either became [version], or someone pushed first and this device is behind [version]. */
class SyncPushed(val version: Long, val accepted: Boolean)

/** A code a new device types to join, valid until [expiresAt] (epoch ms). */
@Serializable
data class JoinCode(val code: String, val expiresAt: Long) {
    /** "K7QM-3XPD": two groups of four, easy to read across the room. */
    val shown: String get() = code.chunked(4).joinToString("-")
}

/** Where the household's devices meet. The real one is the relay; it seals everything before it leaves. */
interface SyncCloud {
    /** False when this build has no relay (and none was typed in). */
    suspend fun available(): Boolean
    fun newKeys(): SyncKeys
    suspend fun pull(keys: SyncKeys, since: Long?): SyncPulled
    suspend fun push(keys: SyncKeys, base: Long, snapshot: String): SyncPushed
    /** Leaves [keys] for a new device under a one-time code. */
    suspend fun offer(keys: SyncKeys): JoinCode
    /** The keys behind a code, once; [SyncCodeUnknown] when it's wrong, used or expired. */
    suspend fun claim(code: String): SyncKeys
}

class SyncCodeUnknown : Exception("Unknown or expired join code")
class SyncNoRelay : Exception("No relay")

/** A build without a relay. */
object NoSyncCloud : SyncCloud {
    override suspend fun available() = false
    override fun newKeys(): SyncKeys = throw SyncNoRelay()
    override suspend fun pull(keys: SyncKeys, since: Long?): SyncPulled = throw SyncNoRelay()
    override suspend fun push(keys: SyncKeys, base: Long, snapshot: String): SyncPushed = throw SyncNoRelay()
    override suspend fun offer(keys: SyncKeys): JoinCode = throw SyncNoRelay()
    override suspend fun claim(code: String): SyncKeys = throw SyncNoRelay()
}

/** What a sync did: changes taken in from the other devices, and changes sent from this one. */
@Serializable
data class SyncReport(val pulled: Int, val pushed: Int)

/**
 * One round, like `git pull` then `git push`: pull what the others pushed since this device's base, merge it in
 * (row by row, in one transaction), and push the result on top of the version it was built on. When another
 * device pushed in between, the remote says so and the round starts again from there.
 */
class SyncEngine(
    private val repo: SyncRepository,
    private val settings: SettingsRepository,
    private val cloud: SyncCloud,
    private val clock: () -> Long = ::nowMillis,
) {
    suspend fun keys(): SyncKeys? = settings.currentValue(SettingsRepository.SYNC_KEYS)?.let { runCatching { JSON.decodeFromString(SyncKeys.serializer(), it) }.getOrNull() }

    suspend fun saveKeys(keys: SyncKeys?) {
        settings.put(SettingsRepository.SYNC_KEYS, keys?.let { JSON.encodeToString(SyncKeys.serializer(), it) })
        if (keys == null) {
            settings.put(SettingsRepository.SYNC_LAST, null)
            repo.clearBase()
        }
    }

    /** Has this device changed anything since it last agreed with the others? */
    suspend fun changed(keys: SyncKeys): Boolean = repo.changed(keys.space)

    suspend fun round(keys: SyncKeys): SyncReport {
        var pulled = 0
        repeat(ATTEMPTS) {
            val base = repo.base(keys.space)
            val remote = cloud.pull(keys, base?.version)
            // Nobody pushed and nothing changed here: up to date, nothing to merge.
            if (remote.snapshot == null && base != null && remote.version == base.version && !repo.changed(keys.space)) {
                settings.put(SettingsRepository.SYNC_LAST, clock().toString())
                return SyncReport(pulled, 0)
            }
            val theirs: JsonObject? = when {
                remote.snapshot != null -> parse(remote.snapshot)
                remote.version == 0L -> null
                else -> base?.snapshot
            }
            // The remote lost its history (empty, or older than what this device agreed on: a reset relay, a household
            // forgotten after a year): nothing there may read as "deleted". Both are added together instead.
            val agreed = base?.takeIf { theirs != null && remote.version >= it.version }
            val result = repo.mergeIn(agreed?.snapshot, theirs)
            pulled += result.pulled
            if (theirs != null && result.pushed == 0) {
                repo.setBase(keys.space, remote.version, result.merged)
                settings.put(SettingsRepository.SYNC_LAST, clock().toString())
                return SyncReport(pulled, 0)
            }
            val pushed = cloud.push(keys, remote.version, result.merged.toString())
            if (pushed.accepted) {
                repo.setBase(keys.space, pushed.version, result.merged)
                settings.put(SettingsRepository.SYNC_LAST, clock().toString())
                return SyncReport(pulled, result.pushed)
            }
            // Someone pushed first: what was pulled is in, so that is the new base; go again.
            if (theirs != null) repo.setBase(keys.space, remote.version, theirs)
        }
        throw SyncBusy()
    }

    /** Joins with [keys]: takes the household as it is, or adds this device's things to it. */
    suspend fun join(keys: SyncKeys, keepThisDevice: Boolean): SyncReport {
        repo.clearBase()
        if (keepThisDevice) return round(keys).also { saveKeys(keys) }
        val remote = cloud.pull(keys, null)
        val snapshot = remote.snapshot?.let(::parse) ?: return round(keys).also { saveKeys(keys) }
        repo.replaceWith(snapshot)
        repo.setBase(keys.space, remote.version, snapshot)
        saveKeys(keys)
        settings.put(SettingsRepository.SYNC_LAST, clock().toString())
        return SyncReport(rowCount(snapshot), 0)
    }

    private fun parse(text: String): JsonObject = BackupRepository.JSON.parseToJsonElement(text).jsonObject

    private fun rowCount(snapshot: JsonObject): Int = listOf("backlog", "shoppingItems", "board", "stockItems", "meals", "dishes")
        .sumOf { (snapshot[it] as? kotlinx.serialization.json.JsonArray)?.size ?: 0 }

    class SyncBusy : Exception("Others keep syncing; try again")

    companion object {
        private const val ATTEMPTS = 4
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}

/** Runs a sync call; turns no relay, no network and an older Hearth into a friendly line. */
private suspend fun <T> syncing(ctx: ToolContext, block: suspend () -> T): T = try {
    block()
} catch (e: ToolException) {
    throw e
} catch (_: SyncNoRelay) {
    throw ToolException(ctx.t("Deze versie heeft nog geen koppelserver. Vul een adres in onder Instellingen → Geavanceerd.", "This build has no relay yet. Fill in an address under Settings → Advanced."))
} catch (_: SyncCodeUnknown) {
    throw ToolException(ctx.t("Die code klopt niet of is verlopen. Vraag op het andere toestel een nieuwe.", "That code isn't right or has expired. Ask for a new one on the other device."))
} catch (_: SyncNeedsUpdate) {
    throw ToolException(ctx.t("Een ander toestel heeft een nieuwere Hearth. Werk Hearth hier eerst bij.", "Another device has a newer Hearth. Update Hearth here first."))
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (_: Exception) {
    throw ToolException(ctx.t("De koppelserver is nu niet bereikbaar. Hearth probeert het straks opnieuw.", "The relay can't be reached right now. Hearth will try again soon."))
}

private fun summaryOf(report: SyncReport, ctx: ToolContext): String {
    fun nl(n: Int) = if (n == 1) "1 wijziging" else "$n wijzigingen"
    fun en(n: Int) = if (n == 1) "1 change" else "$n changes"
    return when {
        report.pulled == 0 && report.pushed == 0 -> ctx.t("Alles is bij.", "Everything is up to date.")
        report.pushed == 0 -> ctx.t("Bijgewerkt: ${nl(report.pulled)} van de anderen.", "Updated: ${en(report.pulled)} from the others.")
        report.pulled == 0 -> ctx.t("Gedeeld: ${nl(report.pushed)} van dit toestel.", "Shared: ${en(report.pushed)} from this device.")
        else -> ctx.t("Bijgewerkt: ${nl(report.pulled)} binnen, ${report.pushed} gedeeld.", "Updated: ${en(report.pulled)} in, ${report.pushed} shared.")
    }
}

private fun noSync(ctx: ToolContext) = ToolException(
    ctx.t("Dit toestel synchroniseert nog niet. Zet het aan onder Instellingen → Samen op meer toestellen.", "This device doesn't sync yet. Turn it on under Settings → On more devices."),
)

@Serializable
data class SyncResult(val pulled: Int, val pushed: Int, override val summary: String) : ToolResult

class SyncNowTool(private val engine: SyncEngine) : Tool<NoArgs, SyncResult> {
    override val name = NAME
    override val description = "Bring this device and the household's other devices in step now: pull, merge, push (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = NoArgs.serializer()
    override val resultSerializer = SyncResult.serializer()

    override suspend fun run(args: NoArgs, ctx: ToolContext): SyncResult {
        val keys = engine.keys() ?: throw noSync(ctx)
        val report = syncing(ctx) { engine.round(keys) }
        return SyncResult(report.pulled, report.pushed, summaryOf(report, ctx))
    }

    companion object {
        const val NAME = "sync_now"
    }
}

@Serializable
data class JoinCodeResult(val code: String, val shown: String, val expiresAt: Long, override val summary: String) : ToolResult

private fun JoinCode.result(summary: String) = JoinCodeResult(code, shown, expiresAt, summary)

/** Starts the household's sync on this device (it becomes the first one) and gives a code for the next. */
class StartSyncTool(private val engine: SyncEngine, private val cloud: SyncCloud) : Tool<NoArgs, JoinCodeResult> {
    override val name = "start_sync"
    override val description = "Share this device's household so other devices can join it with a code (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = NoArgs.serializer()
    override val resultSerializer = JoinCodeResult.serializer()

    override suspend fun run(args: NoArgs, ctx: ToolContext): JoinCodeResult = syncing(ctx) {
        if (engine.keys() != null) throw ToolException(ctx.t("Dit toestel synchroniseert al.", "This device already syncs."))
        if (!cloud.available()) throw SyncNoRelay()
        val keys = cloud.newKeys()
        engine.round(keys)
        engine.saveKeys(keys)
        cloud.offer(keys).let { code ->
            code.result(ctx.t("Het huishouden staat klaar. Op het andere toestel: Instellingen → Samen op meer toestellen → Doe mee met een code: ${code.shown}.", "The household is ready. On the other device: Settings → On more devices → Join with a code: ${code.shown}."))
        }
    }
}

/** A fresh one-time code for one more device. */
class ShareSyncTool(private val engine: SyncEngine, private val cloud: SyncCloud) : Tool<NoArgs, JoinCodeResult> {
    override val name = "share_sync"
    override val description = "A one-time code (valid ten minutes) so one more device can join the household (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = NoArgs.serializer()
    override val resultSerializer = JoinCodeResult.serializer()

    override suspend fun run(args: NoArgs, ctx: ToolContext): JoinCodeResult {
        val keys = engine.keys() ?: throw noSync(ctx)
        val code = syncing(ctx) { cloud.offer(keys) }
        return code.result(ctx.t("Typ ${code.shown} op het nieuwe toestel. De code werkt tien minuten, voor één toestel.", "Type ${code.shown} on the new device. It works for ten minutes, for one device."))
    }
}

@Serializable
data class JoinSyncArgs(
    @Describe("The code the other device shows, e.g. K7QM-3XPD.") val code: String,
    @Describe("True: add what is on this device to the household. False (default): this device takes the household as it is.")
    val keepThisDevice: Boolean = false,
)

/** Joins the household with a code. Asks first: by default what is on this device makes way for the household. */
class JoinSyncTool(private val engine: SyncEngine, private val cloud: SyncCloud) : Tool<JoinSyncArgs, SyncResult> {
    override val name = "join_sync"
    override val description = "Join the household's other devices with the code one of them shows (tablet only)."
    override val destructive = true
    override val exposeOverMcp = false
    override val argsSerializer = JoinSyncArgs.serializer()
    override val resultSerializer = SyncResult.serializer()

    override fun confirmQuestion(args: JoinSyncArgs, ctx: ToolContext) =
        if (args.keepThisDevice) ctx.t("Wat op dit toestel staat bij het huishouden voegen?", "Add what is on this device to the household?")
        else ctx.t("Dit toestel neemt het huishouden over. Wat nu hier staat, verdwijnt. Doorgaan?", "This device takes over the household. What is on it now goes. Continue?")

    override suspend fun run(args: JoinSyncArgs, ctx: ToolContext): SyncResult = syncing(ctx) {
        if (engine.keys() != null) throw ToolException(ctx.t("Dit toestel synchroniseert al. Stop eerst om bij een ander huishouden te komen.", "This device already syncs. Stop first to join another household."))
        val keys = cloud.claim(args.code)
        val report = engine.join(keys, args.keepThisDevice)
        SyncResult(
            report.pulled, report.pushed,
            if (args.keepThisDevice) ctx.t("Welkom! Dit toestel hoort nu bij het huishouden; jullie dingen staan samen.", "Welcome! This device is part of the household now; your things are together.")
            else ctx.t("Welkom! Dit toestel hoort nu bij het huishouden.", "Welcome! This device is part of the household now."),
        )
    }
}

@Serializable
data class StopSyncResult(override val summary: String) : ToolResult

/** This device leaves; everything stays on it, and the other devices go on without it. */
class StopSyncTool(private val engine: SyncEngine) : Tool<NoArgs, StopSyncResult> {
    override val name = "stop_sync"
    override val description = "Stop syncing on this device. Everything stays here and on the other devices (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = NoArgs.serializer()
    override val resultSerializer = StopSyncResult.serializer()

    override suspend fun run(args: NoArgs, ctx: ToolContext): StopSyncResult {
        engine.saveKeys(null)
        return StopSyncResult(ctx.t("Dit toestel synchroniseert niet meer. Alles blijft hier staan.", "This device no longer syncs. Everything stays here."))
    }
}
