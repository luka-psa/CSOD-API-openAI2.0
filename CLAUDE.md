# Hearth — household assistant for a shared family tablet

Hearth is a local-first Android tablet app. It lives on one shared, always-on
tablet at home. The home screen is a chat "orchestrator"; the rest is a visual,
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

- **Local-first.** All data lives in on-device SQLite via Room. The app must be
  fully usable offline. Cloud is an optional enhancer, never a dependency.
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

## Code conventions

- Kotlin, Jetpack Compose (Material 3), Room, Coroutines/Flow,
  kotlinx.serialization. Manual DI via an `AppContainer` (no Hilt) to keep the
  build and runtime light.
- IDs are UUID strings; every entity has `createdAt`/`updatedAt` (epoch millis)
  to make backup/restore and future sync safe.
- Tool names are `snake_case` verb_noun (`add_grocery_item`). Tool results are
  small, serializable data classes plus a short human `summary` string that the
  chat can show verbatim.
- Every tool gets a unit test (in-memory Room). Parser rules get table-driven
  tests.
- Build in small vertical slices. Each slice ends with: tests passing, the
  feature reachable from both chat and UI, and `docs/PROPOSAL.md` updated if the
  plan changed.

## Copy & tone checklist (for any user-facing string)

- Short, warm, specific. Credit the person ("Sam added milk").
- Never "overdue", "failed", "you forgot". Use "still open", "skipped today".
- Celebrate done work and protected time as earned, not as a guilty extra.
