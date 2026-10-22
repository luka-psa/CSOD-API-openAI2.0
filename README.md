# Hearth 🔥

A local-first household assistant for a shared, always-on family tablet.
Chat (voice or text, Flemish or English) on the left, today's dashboard on the right.

- Product and architecture rules: [`CLAUDE.md`](CLAUDE.md)
- Terminology, tools, schema, layout, slices: [`docs/PROPOSAL.md`](docs/PROPOSAL.md)

## Build

Requires JDK 17+ and the Android SDK (platform 36).

```
./gradlew testDebugUnitTest :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Tablet setup (once)

1. Install the APK and open Hearth.
2. Press the home button and choose **Hearth → Always** so it is the home app (it then also returns after a reboot).
3. Optional: set a PIN on the tablet. Hearth keeps showing over the lock screen; everything else needs the PIN.
4. Leave it on the charger: the screen stays on and dims to a clock when nobody uses it.
