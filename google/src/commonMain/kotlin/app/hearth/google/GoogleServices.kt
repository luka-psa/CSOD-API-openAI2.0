package app.hearth.google

import app.hearth.core.tools.system.BackupFile
import app.hearth.core.tools.system.BackupStore
import app.hearth.core.tools.system.CalendarApi
import app.hearth.core.tools.system.CalendarEvent
import app.hearth.core.tools.system.GoogleSignInNeeded
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The two narrow scopes Hearth ever asks for, each only when its feature is switched on. */
object GoogleScopes {
    /** Only Hearth's own hidden folder in Drive; Hearth can't see any other file. */
    const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    /** Only calendars Hearth made itself, and their events; the family's other calendars stay untouched. */
    const val CALENDAR_APP_CREATED = "https://www.googleapis.com/auth/calendar.app.created"
}

/** Gets an access token for [scope]. With [interactive] false it never shows anything (null: sign-in needed). */
interface GoogleAuth {
    /** False on platforms or builds without Google sign-in. */
    val available: Boolean
    suspend fun token(scope: String, interactive: Boolean): String?
}

object NoGoogleAuth : GoogleAuth {
    override val available = false
    override suspend fun token(scope: String, interactive: Boolean): String? = null
}

private val JSON = Json { ignoreUnknownKeys = true }

/** A call with a fresh token; one retry with a new token when Google says the old one expired. */
private suspend fun GoogleAuth.call(scope: String, block: suspend (HttpRequestBuilder.() -> Unit) -> HttpResponse): HttpResponse {
    repeat(2) { attempt ->
        val token = token(scope, interactive = false) ?: throw GoogleSignInNeeded()
        val res = block { header("Authorization", "Bearer $token") }
        if (res.status != HttpStatusCode.Unauthorized || attempt == 1) {
            if (res.status == HttpStatusCode.Unauthorized || res.status == HttpStatusCode.Forbidden && "insufficient" in res.bodyAsText().lowercase()) throw GoogleSignInNeeded()
            return res
        }
    }
    error("unreachable")
}

private suspend fun HttpResponse.ok(): HttpResponse {
    if (!status.isSuccess()) throw IllegalStateException("Google ${status.value}: ${bodyAsText().take(200)}")
    return this
}

@Serializable
private data class DriveFile(val id: String, val name: String = "", val createdTime: String? = null, val size: String? = null)

@Serializable
private data class DriveList(val files: List<DriveFile> = emptyList())

/** Backups in Drive's appDataFolder: hidden from the Drive app, removed when the family revokes Hearth. */
class DriveBackupStore(private val http: HttpClient, private val auth: GoogleAuth) : BackupStore {
    private val scope = GoogleScopes.DRIVE_APPDATA

    override suspend fun list(): List<BackupFile> {
        val res = auth.call(scope) { a ->
            http.get("$DRIVE/files") {
                a()
                parameter("spaces", "appDataFolder")
                parameter("q", "name contains 'hearth-backup' and trashed = false")
                parameter("fields", "files(id,name,createdTime,size)")
                parameter("orderBy", "createdTime desc")
                parameter("pageSize", 50)
            }
        }.ok()
        return JSON.decodeFromString(DriveList.serializer(), res.bodyAsText()).files.map { it.toBackup() }
    }

    override suspend fun upload(name: String, json: String): BackupFile {
        val boundary = "hearth-${name.hashCode().toUInt()}"
        val meta = """{"name":${Json.encodeToString(String.serializer(), name)},"parents":["appDataFolder"],"mimeType":"application/json"}"""
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: application/json\r\n\r\n$json\r\n--$boundary--\r\n"
        val res = auth.call(scope) { a ->
            http.post("$UPLOAD/files") {
                a()
                parameter("uploadType", "multipart")
                parameter("fields", "id,name,createdTime,size")
                contentType(ContentType.parse("multipart/related; boundary=$boundary"))
                setBody(body)
            }
        }.ok()
        return JSON.decodeFromString(DriveFile.serializer(), res.bodyAsText()).toBackup()
    }

    override suspend fun download(id: String): String =
        auth.call(scope) { a -> http.get("$DRIVE/files/$id") { a(); parameter("alt", "media") } }.ok().bodyAsText()

    override suspend fun delete(id: String) {
        val res = auth.call(scope) { a -> http.delete("$DRIVE/files/$id") { a() } }
        if (res.status != HttpStatusCode.NotFound) res.ok()
    }

    @OptIn(ExperimentalTime::class)
    private fun DriveFile.toBackup() = BackupFile(
        id = id,
        name = name,
        createdAt = createdTime?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L,
        sizeBytes = size?.toLongOrNull(),
    )

    private companion object {
        const val DRIVE = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }
}

@Serializable
private data class CalendarRef(val id: String)

/** Hearth's own calendar in Google Calendar (scope calendar.app.created: nothing else is reachable). */
class GoogleCalendarApi(
    private val http: HttpClient,
    private val auth: GoogleAuth,
    private val timeZone: () -> String = { TimeZone.currentSystemDefault().id },
) : CalendarApi {
    private val scope = GoogleScopes.CALENDAR_APP_CREATED

    override suspend fun ensureCalendar(existingId: String?, name: String): String {
        if (existingId != null) {
            val res = auth.call(scope) { a -> http.get("$API/calendars/${enc(existingId)}") { a() } }
            if (res.status.isSuccess()) return existingId
            if (res.status != HttpStatusCode.NotFound && res.status != HttpStatusCode.Gone) res.ok()
        }
        val res = auth.call(scope) { a ->
            http.post("$API/calendars") {
                a()
                contentType(ContentType.Application.Json)
                setBody("""{"summary":${Json.encodeToString(String.serializer(), name)},"timeZone":"${timeZone()}"}""")
            }
        }.ok()
        return JSON.decodeFromString(CalendarRef.serializer(), res.bodyAsText()).id
    }

    /** Update in place; if it isn't there yet, insert it with our own id. */
    override suspend fun put(calendarId: String, event: CalendarEvent) {
        val body = eventJson(event)
        val update = auth.call(scope) { a ->
            http.put("$API/calendars/${enc(calendarId)}/events/${event.id}") { a(); contentType(ContentType.Application.Json); setBody(body) }
        }
        if (update.status.isSuccess()) return
        if (update.status != HttpStatusCode.NotFound) update.ok()
        auth.call(scope) { a ->
            http.post("$API/calendars/${enc(calendarId)}/events") { a(); contentType(ContentType.Application.Json); setBody(body) }
        }.ok()
    }

    override suspend fun delete(calendarId: String, eventId: String) {
        val res = auth.call(scope) { a -> http.delete("$API/calendars/${enc(calendarId)}/events/$eventId") { a() } }
        if (res.status != HttpStatusCode.NotFound && res.status != HttpStatusCode.Gone) res.ok()
    }

    private fun eventJson(e: CalendarEvent): String {
        val end = LocalDate.parse(e.date).plus(DatePeriod(days = 1))
        val s = { v: String -> Json.encodeToString(String.serializer(), v) }
        return """{"id":"${e.id}","summary":${s(e.title)},"description":${s(e.description)},""" +
            """"start":{"date":"${e.date}"},"end":{"date":"$end"},"transparency":"transparent","reminders":{"useDefault":false}}"""
    }

    private fun enc(id: String) = id.replace("@", "%40").replace("#", "%23")

    private companion object {
        const val API = "https://www.googleapis.com/calendar/v3"
    }
}
