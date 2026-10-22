# Hearth — household assistant for a shared family tablet

Hearth is a local-first Android tablet app. It lives on one shared, always-on
tablet at home. The same code also runs as a web app (Kotlin/Wasm) for previews. The home screen is a chat "orchestrator"; the rest is a visual,
today-focused dashboard. The tablet *is* the server: there is no backend.

Read `docs/PROPOSAL.md` for the approved tool list, schema, layout and folder
structure. Keep it in sync when any of those change.

## Problems we solve (keep every feature tied to one)

- Household work feels endless and invisible.
- Partners have different styles (doers vs. planners).
- Only one person knows what's needed (e.g. groceries).
- Daily chores don't fit to-do lists; reminders get ignored.
- Rest and fun time get skipped or feel guilty.
- Small friction stops tasks from happening.

## Product principles

1. **Zero-thinking.** Every interaction takes seconds. Voice first, big touch
   targets, no forms where a sentence will do. If a flow needs more than two
   taps or one sentence, redesign it.
2. **Visible beats notifications.** The shared screen is the reminder. No phone
   pings, no badges, no nagging sounds.
3. **Rhythm over regime.** Habits hang off daily anchors ("after dinner"), not
   clock times. Tone is supportive and never guilt-tripping: no "overdue", no
   red counters, no streak-loss shaming. Prefer "still open", "when you're
   ready", "nice — that's 5 things done today".
4. **Ownership.** Every area has an owner who carries it from noticing to doing.
   Helping is built in via linked sub-tasks assigned to someone else; the owner
   stays the owner.

## Architecture rules (non-negotiable)

- **Local-first.** All data lives in on-device SQLite via Room (Room 3, Kotlin
  Multiplatform). In the browser, the same Room database runs in a SQLite web
  worker stored in OPFS. The app must be fully usable offline. Cloud is an
  optional enhancer, never a dependency.
- **Shared code, thin platforms.** Everything except platform glue (kiosk, voice,
  database opening) lives in `commonMain`. No `java.*`/`android.*` imports in
  common code; use Kotlin stdlib, kotlinx-datetime, expect/actual.
- **One tool layer.** Every action is a typed tool with a schema, defined once
  in `core/tools`. The UI, the chat AI, the local fallback parser and the MCP
  server all call the *same* tool functions. Never write to a DAO from a
  ViewModel or screen; go through a tool. Reads for display may use repository
  `Flow`s directly.
- **Schemas are generated, not hand-written twice.** A tool's argument class is
  the source of truth; its JSON Schema (for LLMs and MCP) is derived from it.
- **Destructive actions need confirmation.** Tools flagged `destructive = true`
  (delete, clear, bulk-complete, restore-from-backup) never execute directly
  from the AI or MCP. They return a pending confirmation that the user approves
  in chat (or on screen). UI-initiated destructive actions show a confirm too.
- **Model-agnostic AI.** All LLM access goes through the `LlmProvider`
  interface (Claude, OpenAI, Grok). No provider SDK types leak outside `ai/`.
  Users bring their own API key; keys are stored encrypted with Android
  Keystore and never logged, exported, or backed up.
- **Tiered intent routing.** 1) deterministic local parser for simple commands
  ("add milk", "what's open?"); 2) optional small quantized on-device model for
  simple commands it can't parse; 3) cloud model for anything complex. Offline
  always degrades gracefully to tiers 1–2 with a friendly message.
- **Low-spec tablets.** Target 2–3 GB RAM devices. Avoid heavy libraries,
  keep Compose recompositions cheap, no always-running background work beyond
  what kiosk mode needs. Local model is optional and downloaded on demand.
- **Kiosk mode.** Full-screen immersive, screen stays on while charging, starts
  on boot, optional lock-task when provisioned as device owner.
- **MCP server.** Tools are also exposed over MCP (LAN, streamable HTTP),
  off by default, protected by a pairing token. Destructive-tool rules apply.
- **Google integration is opt-in and off by default.** Google sign-in is used
  only to (a) push scheduled items to Google Calendar from the device and
  (b) back up / restore to the user's Drive `appDataFolder`. Request the
  narrowest scopes, only when the feature is turned on.
- **Privacy.** No analytics, no telemetry. Only the content needed for a cloud
  request leaves the device, and only when a cloud provider is configured.

## Terminology (use these words everywhere: UI, chat, code, tool names)

Full glossary and linking rules: `docs/PROPOSAL.md` §1–2. In short:

- **Food & Stock:** *Stock item* (IN_STOCK/LOW/OUT, lives in a *Storage spot*),
  *Stock check*, *Shopping item* (restock = linked to a stock item, or
  *one-off buy*), *Shop*, *Meal plan*.
- **Home & Rhythm:** *Rhythm* (fixed daily sequence), *Chore* (recurring
  upkeep with a flexible interval + season, never "late"), *Anchor habit*,
  *Protected time*.
- **Planning:** *Appointment* (TO_BOOK → BOOKED → DONE), *Admin*, *Project*
  with *Steps*, *Errand* (a trip to one shop).
- **Shared:** *Area* with an *Owner*, *Helper*, *Batch*, *Friction*, *Note*,
  *Done log*.
- Never invent synonyms (no "grocery", "staple", "to-do" in new code or copy).
  UI ships in Dutch (Flemish) and English; the local parser understands both.

## Code conventions

- Kotlin Multiplatform (Android + JVM for tests + WasmJs), Compose Multiplatform
  (Material 3), Room 3, Coroutines/Flow, kotlinx.serialization. Manual DI via an
  `AppContainer` (no Hilt) to keep the build and runtime light.
- UI strings live in `ui/src/commonMain/composeResources/values{,-nl}/strings.xml`
  (no Android-style escaping: use “ ” and ’). No emoji in UI or tool summaries:
  the web build has no emoji font; use the vectors in `HearthIcons`.
- IDs are UUID strings; every entity has `createdAt`/`updatedAt` (epoch millis)
  to make backup/restore and future sync safe.
- Tool names are `snake_case` verb_noun (`add_shopping_item`). Tool results are
  small, serializable data classes plus a short human `summary` string that the
  chat can show verbatim.
- Every tool gets a unit test (in-memory Room). Parser rules get table-driven
  tests.
- Build in small vertical slices. Each slice ends with: tests passing, the
  feature reachable from both chat and UI, and `docs/PROPOSAL.md` updated if the
  plan changed.

## Build & test

```
./gradlew hearthCheck          # all tests (JVM + Wasm), Android build + lint, web bundle
./gradlew :web:wasmJsBrowserDevelopmentRun   # web app on localhost with live reload
```

Modules (all Kotlin Multiplatform except `app` and `web`):
`core:model` → `core:data` (Room 3; `worker/` = SQLite web worker) → `core:tools`
(all tools, `HearthTools` wires them) → `ai` (parser + orchestrator) → `ui` (Compose
screens, ViewModel, strings, `AppContainer`) → `app` (Android: activity, kiosk) and
`web` (browser entry point, `index.html`, `vercel.json`).

Tests: tool and orchestrator tests in `jvmTest` (in-memory Room, bundled SQLite);
parser and registry tests in `commonTest` (run on JVM and WebAssembly).
New tools: add args/result classes + tool class in `core/tools/.../<pillar>/`, register in
`HearthTools`, add parser rules (NL + EN) and tests.

## Copy & tone checklist (for any user-facing string)

- Short, warm, specific. Credit the person ("Sam added milk").
- Never "overdue", "failed", "you forgot". Use "still open", "skipped today".
- Celebrate done work and protected time as earned, not as a guilty extra.
