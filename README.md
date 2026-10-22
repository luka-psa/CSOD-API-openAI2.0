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

Every push to `hearth-household-assistant` builds the web app in GitHub Actions
(`.github/workflows/web-preview.yml`). To publish it on Vercel (once):

1. Create a token at <https://vercel.com/account/tokens>.
2. In GitHub: **Settings → Secrets and variables → Actions → New repository secret**,
   name `VERCEL_TOKEN`, paste the token.
3. Run the **Web preview** workflow (Actions tab → Web preview → Run workflow), or push.
   The first run creates a Vercel project called `hearth`; the URL is in the workflow log
   and on your Vercel dashboard.

Without the secret the workflow still builds the site and attaches it as the `hearth-web`
artifact. Each browser keeps its own data (stored in the browser's private file system).
Works in current Chrome, Edge, Firefox and Safari.

## Tablet setup (once)

1. Install the APK and open Hearth.
2. Press the home button and choose **Hearth → Always** so it is the home app (it then also returns after a reboot).
3. Optional: set a PIN on the tablet. Hearth keeps showing over the lock screen; everything else needs the PIN.
4. Leave it on the charger: the screen stays on and dims to a clock when nobody uses it.
