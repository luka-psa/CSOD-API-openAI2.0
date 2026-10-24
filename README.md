# Hearth

A local-first household assistant for a shared, always-on family tablet.
Chat (voice or text, Flemish or English) on the left, today's dashboard on the right.
The same code runs on Android and in the browser.

- Product and architecture rules: [`CLAUDE.md`](CLAUDE.md)
- Terminology, tools, schema, layout, slices: [`docs/PROPOSAL.md`](docs/PROPOSAL.md)

## Build

Requires JDK 17+ and the Android SDK (platform 36).

```
./gradlew hearthCheck                                   # everything
./gradlew :app:assembleRelease                          # tablet APK (signed with the family key)
adb install -r app/build/outputs/apk/release/app-release.apk
./gradlew :web:wasmJsBrowserDevelopmentRun              # web app on localhost
```

## Web preview on Vercel

Every push to `hearth-household-assistant` runs `.github/workflows/web-preview.yml`:
tests, builds the web app, and publishes the finished site to the **`hearth-web`** branch.
Vercel (connected to this repo) deploys that branch as static files; it never builds Kotlin
itself. The source branch has a root `vercel.json` that switches Vercel deployments off for it.

For a stable public link, set **Vercel → Project → Settings → Git → Production Branch** to
`hearth-web`. Otherwise `hearth-web` deploys as a preview, which Vercel shows only to people
logged in to your Vercel team.

Each browser keeps its own data (stored in the browser's private file system). A first visit starts with
one or two examples in every section, to show how it works.
Works in current Chrome, Edge, Firefox and Safari.

## Get the app on the tablet

Every push also builds the Android app and puts it on the same site: open
`<preview link>/download.html` on the tablet and tap **Download Hearth** (the APK itself is
`/hearth.apk`; it is also attached to each workflow run as `hearth-android`).

All builds are signed with `app/hearth-family.jks`, so a newer APK installs over the old one and the
household's data stays. (Uninstalling Hearth deletes its data.) The key only tells Android "this is
the same app"; anyone with this repository can sign with it, so keep the repository private.

## Tablet setup (once)

1. Install the APK: open it, allow "install unknown apps" for the browser or Files app when Android
   asks (Settings → Apps & notifications → Special app access → Install unknown apps), then Install → Open.
2. Press the home button and choose **Hearth → Always** so it is the home app (it then also returns after a reboot).
3. Optional: set a PIN on the tablet. Hearth keeps showing over the lock screen; everything else needs the PIN.
4. Leave it on the charger: the screen stays on and dims to a clock when nobody uses it.
