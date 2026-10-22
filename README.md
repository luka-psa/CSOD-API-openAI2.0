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
adb install app/build/outputs/apk/debug/app-debug.apk   # tablet
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

Each browser keeps its own data (stored in the browser's private file system).
Works in current Chrome, Edge, Firefox and Safari.

## Tablet setup (once)

1. Install the APK and open Hearth.
2. Press the home button and choose **Hearth → Always** so it is the home app (it then also returns after a reboot).
3. Optional: set a PIN on the tablet. Hearth keeps showing over the lock screen; everything else needs the PIN.
4. Leave it on the charger: the screen stays on and dims to a clock when nobody uses it.
