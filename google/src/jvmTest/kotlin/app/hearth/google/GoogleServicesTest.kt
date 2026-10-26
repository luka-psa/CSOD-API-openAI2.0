package app.hearth.google

import app.hearth.core.tools.system.CalendarEvent
import app.hearth.core.tools.system.GoogleSignInNeeded
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The REST calls Hearth makes, against a fake Google. */
class GoogleServicesTest {
    private val seen = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()
    private var tokens = mutableListOf("t1", "t2")

    private val auth = object : GoogleAuth {
        override val available = true
        val scopes = mutableListOf<String>()
        override suspend fun token(scope: String, interactive: Boolean): String? {
            scopes += scope
            return tokens.firstOrNull()
        }
    }

    private fun client(answer: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>) = HttpClient(MockEngine { req ->
        seen += req
        bodies += (req.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString() ?: ""
        val (status, body) = answer(req)
        respond(body, status, headersOf("Content-Type", "application/json"))
    })

    @Test
    fun `a backup goes to the hidden app folder as one multipart upload`() = runTest {
        val drive = DriveBackupStore(client { _ -> HttpStatusCode.OK to """{"id":"f1","name":"hearth-backup-1.json","createdTime":"2026-10-23T08:12:00.000Z","size":"42"}""" }, auth)
        val file = drive.upload("hearth-backup-1.json", """{"version":9}""")
        assertEquals("f1", file.id)
        assertEquals(42L, file.sizeBytes)
        val req = seen.single()
        assertEquals("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id%2Cname%2CcreatedTime%2Csize", req.url.toString())
        assertEquals("Bearer t1", req.headers["Authorization"])
        assertTrue("\"parents\":[\"appDataFolder\"]" in bodies.single())
        assertTrue("{\"version\":9}" in bodies.single())
        assertEquals(listOf(GoogleScopes.DRIVE_APPDATA), auth.scopes)
    }

    @Test
    fun `an expired token is renewed once, a revoked one asks for sign-in`() = runTest {
        var n = 0
        val drive = DriveBackupStore(client { _ -> if (n++ == 0) HttpStatusCode.Unauthorized to "{}" else HttpStatusCode.OK to """{"files":[{"id":"a","name":"hearth-backup-2.json"}]}""" }, auth)
        assertEquals(listOf("a"), drive.list().map { it.id })
        assertEquals(2, seen.size)
        assertTrue(seen.first().url.parameters["spaces"] == "appDataFolder")

        val gone = DriveBackupStore(client { _ -> HttpStatusCode.Unauthorized to "{}" }, auth)
        assertFailsWith<GoogleSignInNeeded> { gone.list() }
        tokens.clear()
        assertFailsWith<GoogleSignInNeeded> { drive.download("a") }
    }

    @Test
    fun `calendar events are all-day, quiet and inserted when missing`() = runTest {
        val cal = GoogleCalendarApi(client { req ->
            when {
                req.method == HttpMethod.Get -> HttpStatusCode.NotFound to "{}"
                req.method == HttpMethod.Post && req.url.encodedPath.endsWith("/calendars") -> HttpStatusCode.OK to """{"id":"abc@group.calendar.google.com"}"""
                req.method == HttpMethod.Put -> HttpStatusCode.NotFound to "{}"
                else -> HttpStatusCode.OK to "{}"
            }
        }, auth, timeZone = { "Europe/Brussels" })
        val id = cal.ensureCalendar("old@group.calendar.google.com", "Hearth")
        assertEquals("abc@group.calendar.google.com", id)
        assertTrue("\"timeZone\":\"Europe/Brussels\"" in bodies[1])

        cal.put(id, CalendarEvent("h1234", "Tandarts", "2026-10-31", "Uit Hearth"))
        val insert = bodies.last()
        assertTrue("\"start\":{\"date\":\"2026-10-31\"},\"end\":{\"date\":\"2026-11-01\"}" in insert, insert)
        assertTrue("\"reminders\":{\"useDefault\":false}" in insert, "visible beats notifications: no pings")
        assertEquals("/calendar/v3/calendars/abc%40group.calendar.google.com/events", seen.last().url.encodedPath)
        assertEquals(listOf(GoogleScopes.CALENDAR_APP_CREATED), auth.scopes.distinct())

        // A rhythm at a fixed time: an hour from its time, in this device's zone, repeating.
        cal.put(id, CalendarEvent("r1", "Zwemles", "2026-10-28", "Uit Hearth · elke woensdag om 14:00", time = "14:00", recurrence = "RRULE:FREQ=WEEKLY;BYDAY=WE"))
        val rhythm = bodies.last()
        assertTrue(
            "\"start\":{\"dateTime\":\"2026-10-28T14:00:00\",\"timeZone\":\"Europe/Brussels\"},\"end\":{\"dateTime\":\"2026-10-28T15:00:00\",\"timeZone\":\"Europe/Brussels\"}" in rhythm,
            rhythm,
        )
        assertTrue("\"recurrence\":[\"RRULE:FREQ=WEEKLY;BYDAY=WE\"]" in rhythm, rhythm)
    }
}
