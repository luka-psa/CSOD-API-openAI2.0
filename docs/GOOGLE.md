# Google Drive backup and Google Calendar: one-time setup

Families only tap **Aanmelden met Google** / **Koppel Google Agenda** under Koppelingen. To make those
buttons work, the maintainer sets up one Google Cloud project for every Hearth build, once. There are no
secrets in the app. On Android, Google recognises Hearth by its package name and signing key. In the
browser it uses a public web client id.

## What Hearth asks for (and only when that feature is switched on)

| Feature | Scope | What it allows |
|---|---|---|
| Back-up | `https://www.googleapis.com/auth/drive.appdata` | Only Hearth's own hidden folder in Drive. Google lists it as non-sensitive. |
| Agenda | `https://www.googleapis.com/auth/calendar.app.created` | Only calendars Hearth creates (“Hearth”) and their events. The family's other calendars stay unreachable. |

- **Backups:** `hearth-backup-<time>.json`. Once a day while switched on, plus "Nu back-up maken". The 7 newest are kept.
- **Restore:** replaces everything on the tablet (it asks first). The tablet's own AI link is never part of a backup.
- **Calendar:** each item with a day becomes an all-day event in the “Hearth” calendar.
  - Events have no reminders and show as "free": the shared screen stays the reminder.
  - Only changes are sent. Done or unplanned items disappear from the calendar.

## Steps (Google Cloud Console, about 10 minutes)

1. **Create a project** at <https://console.cloud.google.com> (e.g. "Hearth").
2. **APIs & Services → Library:** enable **Google Drive API** and **Google Calendar API**.
3. **Google Auth Platform → Branding / Audience:**
   - App name: Hearth. Add a support email.
   - User type: **External**.
   - While the app is in **Testing**, add each family's Google accounts as test users (up to 100). Testing-mode
     access is valid for 7 days per grant, so publish the app for real use.
   - **Publish:** the Drive scope is non-sensitive. If the Calendar scope shows as sensitive, Google asks for a
     short verification, and until it's done people see an "unverified app" notice they can click through.
4. **Data access:** add the two scopes above.
5. **Clients → Create client → Android:**
   - Package name: `app.hearth`
   - SHA-1 of the family signing key (`app/hearth-family.jks`): `86:27:30:C8:87:85:A8:71:AB:40:6B:D2:0B:CF:EE:48:08:C5:FB:02`
   
   That is all the tablet app needs. Nothing goes into the code.
6. **Optional, for the web preview:**
   - Clients → Create client → **Web application**. Authorized JavaScript origins: your Vercel address
     (and `http://localhost:8080` for development).
   - Put its client id in the repository variable `HEARTH_GOOGLE_WEB_CLIENT_ID` (Settings → Secrets and variables → Actions → Variables).
   - The web build bakes it in (`-Phearth.googleWebClientId`). Without it the web app says Google works in the tablet app.

If the signing key ever changes, add its SHA-1 to the Android client too (keytool:
`keytool -list -v -keystore app/hearth-family.jks -alias hearth -storepass hearth-family`).

## Code

- `google/` (Kotlin Multiplatform):
  - `DriveBackupStore` and `GoogleCalendarApi` call the plain REST APIs with Ktor; tested with a fake Google.
  - `AndroidGoogleAuth` uses Play services' `AuthorizationClient`. `MainActivity` shows its consent screen.
  - `WebGoogleAuth` uses the Google Identity Services token client.
- Tools in `core/tools/system/GoogleTools.kt` (all tablet-only):
  - `set_google_sync`, `backup_now`, `list_backups`, `sync_calendar`
  - `restore_backup`: destructive, asks first.
- `core/data/backup`: `BackupRepository` export/restore, one transaction.
- `AppContainer`:
  - Runs a daily backup check.
  - Syncs the calendar 20 s after the plan changes, only while each feature is on.
