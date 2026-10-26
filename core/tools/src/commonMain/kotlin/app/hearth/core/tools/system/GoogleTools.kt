package app.hearth.core.tools.system

import app.hearth.core.data.backlog.BacklogRepository
import app.hearth.core.data.backup.BackupRepository
import app.hearth.core.data.settings.SettingsRepository
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Plan
import app.hearth.core.model.Schedule
import app.hearth.core.model.dayOf
import app.hearth.core.model.isRoutine
import app.hearth.core.model.nowMillis
import app.hearth.core.tools.Describe
import app.hearth.core.tools.Tool
import app.hearth.core.tools.ToolContext
import app.hearth.core.tools.ToolException
import app.hearth.core.tools.ToolResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// Tablet-only: the family's own Google Drive (backup) and Google Calendar (push). Off by default; each
// asks for its own narrow scope only when it is switched on. Never offered over MCP.

/** A backup file in the app's hidden Drive folder. */
@Serializable
data class BackupFile(val id: String, val name: String, val createdAt: Long, val sizeBytes: Long? = null)

/** Google Drive's appDataFolder, or anything else that can keep a few files. */
interface BackupStore {
    suspend fun list(): List<BackupFile>
    suspend fun upload(name: String, json: String): BackupFile
    suspend fun download(id: String): String
    suspend fun delete(id: String)
}

/**
 * One event in a calendar Hearth made: all day, or at [time] ("20:00") for a rhythm at a fixed time. [recurrence] is an
 * RRULE for a rhythm on fixed days, starting on [date]. [id] is stable, so pushing again updates it.
 */
@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val date: String,
    val description: String,
    val time: String? = null,
    val recurrence: String? = null,
)

/** Google Calendar, limited to the one calendar Hearth made itself. */
interface CalendarApi {
    /** Returns [existingId] if that calendar still exists, else makes a new one called [name]. */
    suspend fun ensureCalendar(existingId: String?, name: String): String
    suspend fun put(calendarId: String, event: CalendarEvent)
    suspend fun delete(calendarId: String, eventId: String)
}

/** Not signed in to Google (or the permission was taken back). The person can sign in again. */
class GoogleSignInNeeded : Exception("Google sign-in needed")

object GoogleSettings {
    const val BACKUP_ON = "google.backup"
    const val LAST_BACKUP = "google.lastBackup"
    const val CALENDAR_ON = "google.calendar"
    const val CALENDAR_ID = "google.calendarId"
    const val CALENDAR_PUSHED = "google.calendarPushed"
    /** The second calendar, "Hearth · Ritmes": rhythms on fixed days as repeating events. */
    const val RHYTHM_CALENDAR_ID = "google.rhythmCalendarId"
    const val RHYTHM_PUSHED = "google.rhythmCalendarPushed"
    const val LAST_SYNC = "google.lastCalendarSync"
}

/** The Google features are optional: a build or platform without them gets this. */
object NoGoogle : BackupStore, CalendarApi {
    override suspend fun list(): List<BackupFile> = throw GoogleSignInNeeded()
    override suspend fun upload(name: String, json: String): BackupFile = throw GoogleSignInNeeded()
    override suspend fun download(id: String): String = throw GoogleSignInNeeded()
    override suspend fun delete(id: String) = throw GoogleSignInNeeded()
    override suspend fun ensureCalendar(existingId: String?, name: String): String = throw GoogleSignInNeeded()
    override suspend fun put(calendarId: String, event: CalendarEvent) = throw GoogleSignInNeeded()
    override suspend fun delete(calendarId: String, eventId: String) = throw GoogleSignInNeeded()
}

/** Runs a Google call; turns "not signed in" and network trouble into a friendly line. */
private suspend fun <T> google(ctx: ToolContext, block: suspend () -> T): T = try {
    block()
} catch (e: ToolException) {
    throw e
} catch (_: GoogleSignInNeeded) {
    throw ToolException(ctx.t("Meld je eerst aan bij Google.", "Sign in to Google first."))
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (_: Exception) {
    throw ToolException(ctx.t("Google is nu niet bereikbaar. Hearth probeert het later opnieuw.", "Google can't be reached right now. Hearth will try again later."))
}

// ---- switches ----

@Serializable
data class SetGoogleSyncArgs(
    @Describe("Daily backup to Google Drive on or off.")
    val backup: Boolean? = null,
    @Describe("Show planned days in Google Calendar on or off.")
    val calendar: Boolean? = null,
)

@Serializable
data class GoogleSyncResult(val backup: Boolean, val calendar: Boolean, override val summary: String) : ToolResult

class SetGoogleSyncTool(private val settings: SettingsRepository) : Tool<SetGoogleSyncArgs, GoogleSyncResult> {
    override val name = "set_google_sync"
    override val description = "Turn the Google Drive backup or Google Calendar on or off (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = SetGoogleSyncArgs.serializer()
    override val resultSerializer = GoogleSyncResult.serializer()

    override suspend fun run(args: SetGoogleSyncArgs, ctx: ToolContext): GoogleSyncResult {
        args.backup?.let { settings.put(GoogleSettings.BACKUP_ON, it.toString()) }
        args.calendar?.let { settings.put(GoogleSettings.CALENDAR_ON, it.toString()) }
        val backup = settings.currentValue(GoogleSettings.BACKUP_ON) == "true"
        val calendar = settings.currentValue(GoogleSettings.CALENDAR_ON) == "true"
        val summary = when {
            args.backup == true -> ctx.t("Elke dag een back-up in Google Drive.", "A backup to Google Drive every day.")
            args.backup == false -> ctx.t("Back-up naar Google Drive staat uit.", "Backup to Google Drive is off.")
            args.calendar == true -> ctx.t("Geplande dagen komen in Google Agenda.", "Planned days go to Google Calendar.")
            else -> ctx.t("Google Agenda staat uit.", "Google Calendar is off.")
        }
        return GoogleSyncResult(backup, calendar, summary)
    }
}

// ---- backup ----

@Serializable
class NoArgs

@Serializable
data class BackupResult(val file: BackupFile, val items: Int, override val summary: String) : ToolResult

class BackupNowTool(
    private val backups: BackupRepository,
    private val store: BackupStore,
    private val settings: SettingsRepository,
    private val clock: () -> Long = ::nowMillis,
) : Tool<NoArgs, BackupResult> {
    override val name = "backup_now"
    override val description = "Save a copy of everything in Hearth to the family's Google Drive (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = NoArgs.serializer()
    override val resultSerializer = BackupResult.serializer()

    override suspend fun run(args: NoArgs, ctx: ToolContext): BackupResult = google(ctx) {
        val backup = backups.export()
        val json = BackupRepository.JSON.encodeToString(app.hearth.core.data.backup.HearthBackup.serializer(), backup)
        val file = store.upload("hearth-backup-${clock()}.json", json)
        // Keep the newest few; older ones go.
        store.list().sortedByDescending { it.createdAt }.drop(KEEP).forEach { runCatching { store.delete(it.id) } }
        settings.put(GoogleSettings.LAST_BACKUP, clock().toString())
        BackupResult(file, backup.itemCount, ctx.t("Back-up staat veilig in Google Drive.", "Backup is safe in Google Drive."))
    }

    companion object {
        const val KEEP = 7
    }
}

@Serializable
data class BackupListResult(val backups: List<BackupFile>, override val summary: String) : ToolResult

class ListBackupsTool(private val store: BackupStore) : Tool<NoArgs, BackupListResult> {
    override val name = "list_backups"
    override val description = "The backups in Google Drive, newest first (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = NoArgs.serializer()
    override val resultSerializer = BackupListResult.serializer()

    override suspend fun run(args: NoArgs, ctx: ToolContext): BackupListResult = google(ctx) {
        val files = store.list().sortedByDescending { it.createdAt }
        BackupListResult(files, if (files.isEmpty()) ctx.t("Nog geen back-ups.", "No backups yet.") else ctx.t("${files.size} back-ups.", "${files.size} backups."))
    }
}

@Serializable
data class RestoreBackupArgs(@Describe("Id of the backup to put back.") val id: String)

@Serializable
data class RestoreResult(val items: Int, override val summary: String) : ToolResult

class RestoreBackupTool(
    private val backups: BackupRepository,
    private val store: BackupStore,
) : Tool<RestoreBackupArgs, RestoreResult> {
    override val name = "restore_backup"
    override val description = "Replace everything on this tablet with a backup from Google Drive (tablet only)."
    override val destructive = true
    override val exposeOverMcp = false
    override val argsSerializer = RestoreBackupArgs.serializer()
    override val resultSerializer = RestoreResult.serializer()

    override fun confirmQuestion(args: RestoreBackupArgs, ctx: ToolContext) =
        ctx.t("Alles op deze tablet vervangen door deze back-up?", "Replace everything on this tablet with this backup?")

    override suspend fun run(args: RestoreBackupArgs, ctx: ToolContext): RestoreResult {
        val json = google(ctx) { store.download(args.id) }
        val backup = try {
            backups.parse(json)
        } catch (_: IllegalArgumentException) {
            throw ToolException(ctx.t("Die back-up kan deze versie van Hearth niet lezen.", "This version of Hearth can't read that backup."))
        }
        backups.restore(backup)
        return RestoreResult(backup.itemCount, ctx.t("Back-up teruggezet. Alles staat er weer.", "Backup restored. Everything is back."))
    }
}

// ---- calendar ----

@Serializable
data class CalendarSyncResult(val added: Int, val changed: Int, val removed: Int, override val summary: String) : ToolResult

/**
 * Pushes every open item with a day to Hearth's own Google calendar as an all-day event, and removes
 * events whose item got done, unplanned or deleted. Only what changed since last time is sent.
 */
class SyncCalendarTool(
    private val backlog: BacklogRepository,
    private val calendar: CalendarApi,
    private val settings: SettingsRepository,
    private val clock: () -> Long = ::nowMillis,
) : Tool<NoArgs, CalendarSyncResult> {
    override val name = "sync_calendar"
    override val description = "Bring Hearth's Google calendar up to date with the planned days (tablet only)."
    override val exposeOverMcp = false
    override val argsSerializer = NoArgs.serializer()
    override val resultSerializer = CalendarSyncResult.serializer()

    override suspend fun run(args: NoArgs, ctx: ToolContext): CalendarSyncResult = google(ctx) {
        val open = backlog.openItems()
        val days = push("Hearth", GoogleSettings.CALENDAR_ID, GoogleSettings.CALENDAR_PUSHED, open.mapNotNull { eventFor(it, ctx) }, always = true)
        // Rhythms on fixed days go in their own calendar, so it can be shown or hidden on its own.
        val rhythms = push(
            ctx.t("Hearth · Ritmes", "Hearth · Rhythms"), GoogleSettings.RHYTHM_CALENDAR_ID, GoogleSettings.RHYTHM_PUSHED,
            open.mapNotNull { rhythmEventFor(it, ctx) }, always = false,
        )
        settings.put(GoogleSettings.LAST_SYNC, clock().toString())
        val (added, changed, removed) = Triple(days.first + rhythms.first, days.second + rhythms.second, days.third + rhythms.third)
        val summary = if (added + changed + removed == 0) {
            ctx.t("Google Agenda is bij.", "Google Calendar is up to date.")
        } else {
            ctx.t("Google Agenda bijgewerkt.", "Google Calendar updated.")
        }
        CalendarSyncResult(added, changed, removed, summary)
    }

    /**
     * Brings one calendar in line with [events]: only what changed since last time is sent. With [always] false the
     * calendar is only made once there is something to put in it. Returns added, changed, removed.
     */
    private suspend fun push(name: String, idKey: String, pushedKey: String, events: List<CalendarEvent>, always: Boolean): Triple<Int, Int, Int> {
        val wanted = events.associateBy { it.id }
        val pushed = settings.currentValue(pushedKey)?.let { runCatching { JSON.decodeFromString(PUSHED, it) }.getOrNull() }.orEmpty()
        val existing = settings.currentValue(idKey)
        if (!always && wanted.isEmpty() && pushed.isEmpty()) return Triple(0, 0, 0)
        val calendarId = calendar.ensureCalendar(existing, name)
        val fresh = calendarId != existing
        if (fresh) settings.put(idKey, calendarId)
        val known = if (fresh) emptyMap() else pushed

        val now = known.toMutableMap()
        var added = 0
        var changed = 0
        var removed = 0
        try {
            for ((id, event) in wanted) {
                val hash = fingerprint(event)
                if (known[id] == hash) continue
                calendar.put(calendarId, event)
                if (id in known) changed++ else added++
                now[id] = hash
            }
            for (id in known.keys - wanted.keys) {
                calendar.delete(calendarId, id)
                now.remove(id)
                removed++
            }
        } finally {
            // Remember what did go through, so a dropped connection never pushes twice.
            settings.put(pushedKey, JSON.encodeToString(PUSHED, now))
        }
        return Triple(added, changed, removed)
    }

    /** A rhythm on fixed days, repeating from its first day after it got its schedule (so the event stays the same). */
    private fun rhythmEventFor(item: BacklogItem, ctx: ToolContext): CalendarEvent? {
        val schedule = item.schedule?.takeIf { !item.done && item.isRoutine() } ?: return null
        val from = dayOf(item.createdAt)
        val first = if (schedule.occursOn(from)) from else schedule.nextAfter(from) ?: return null
        return CalendarEvent(
            "r" + item.id.lowercase().filter { it in '0'..'9' || it in 'a'..'v' },
            item.text, first.toString(), ctx.t("Uit Hearth · ", "From Hearth · ") + schedule.label(ctx.lang),
            time = schedule.time, recurrence = rrule(schedule),
        )
    }

    private fun eventFor(item: BacklogItem, ctx: ToolContext): CalendarEvent? {
        if (item.done || item.plan != Plan.DAY) return null
        val date = item.localDate ?: return null
        val title = when {
            item.waiting -> ctx.t("Opvolgen: ${item.text}", "Follow up: ${item.text}")
            item.finishBy -> ctx.t("Klaar tegen vandaag: ${item.text}", "Done by today: ${item.text}")
            else -> item.text
        }
        // Google event ids allow 0-9 and a-v; a UUID without dashes fits.
        val id = "h" + item.id.lowercase().filter { it in '0'..'9' || it in 'a'..'v' }
        return CalendarEvent(id, title, date.toString(), ctx.t("Uit Hearth", "From Hearth"))
    }

    private fun fingerprint(e: CalendarEvent) =
        (if (e.recurrence == null && e.time == null) "${e.title}|${e.date}" else "${e.title}|${e.date}|${e.time}|${e.recurrence}|${e.description}").hashCode().toString(16)

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val PUSHED = MapSerializer(String.serializer(), String.serializer())
    }
}

private val RRULE_DAYS = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

/** The RRULE for a rhythm's fixed days: weekly on its days, monthly on a date or on the nth weekday. */
internal fun rrule(s: Schedule): String {
    val day = s.monthDay
    val week = s.monthWeek
    return when {
        day != null -> "RRULE:FREQ=MONTHLY;BYMONTHDAY=${if (day >= 31) -1 else day}"
        week != null -> "RRULE:FREQ=MONTHLY;BYDAY=$week${RRULE_DAYS[s.weekdays.first().ordinal]}"
        s.weekdays.size >= 7 -> "RRULE:FREQ=DAILY"
        else -> "RRULE:FREQ=WEEKLY;BYDAY=" + s.weekdays.sorted().joinToString(",") { RRULE_DAYS[it.ordinal] }
    }
}
