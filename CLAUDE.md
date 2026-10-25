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
- **AI comes in over MCP, not API keys.** The family's own ChatGPT or Claude
  subscription is the "brain": it calls Hearth's tools through the MCP server.
  Hearth itself makes no paid model calls. The `ai/llm` provider layer
  (OpenAI, Claude, Grok) is kept but not wired into the app.
- **The tablet's capture bar is local and instant.** 1) deterministic parser
  for clear commands ("voeg melk toe", "wat staat er op de lijst?"); 2) keyword
  sorter (NL/EN) puts everything else on the Backlog with a section label and,
  if a time word ends the sentence ("morgen", "tegen vrijdag"), a plan; a
  rhythm ("om de 2 weken") makes it a routine, "dringend" flags it.
  Nothing anyone says is ever lost. (The orchestrator can still route through a
  router + section-helper model pair if a provider is ever configured.)
- **Low-spec tablets.** Target 2–3 GB RAM devices. Avoid heavy libraries,
  keep Compose recompositions cheap, no always-running background work beyond
  what kiosk mode needs. Local model is optional and downloaded on demand.
- **Kiosk mode.** Full-screen immersive, screen stays on while charging, starts
  on boot, optional lock-task when provisioned as device owner.
- **MCP server.** Tools are exposed over MCP (streamable HTTP), off by default,
  reached through one shared relay with an OAuth login (a pairing code from the
  tablet; families never handle API keys). Destructive tools return a pending
  action that the person confirms (in their AI chat or on the tablet). See
  `docs/PROPOSAL.md` §7.
- **Google integration is opt-in and off by default.** Google sign-in is used
  only to (a) push scheduled items to Google Calendar from the device (Hearth's
  own calendar, scope `calendar.app.created`) and
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
- **Backlog:** everything still open from any section. Plan: *To plan*
  (unplanned), *Next up*, a *Day* (do on / *finish by*), *Later*. Earlier days
  read "sinds dinsdag", never "late".
- **Task kinds** (`TaskKind`, derived from the section): *Ritme & gewoontes*
  (CHORE/RHYTHM: interval + lastDone; done = "logged today", it comes
  back; states NEW/FRESH/SOON/DUE, shown as "aan de beurt", never "late".
  HABIT/RHYTHM are *habits*: never ticked off, rated together instead ("Hoe loopt
  het?"): *Ease* EASY "loopt vanzelf" (green) / EFFORT "vraagt moeite" (amber) /
  HARD "te zwaar" (orange, never red) with an optional *small step* ("Begin klein").
  A chore's ease comes from the `routine_log`; a task's from how often it was
  *postponed* (2× amber, 4× orange). A routine can hang on an *Anchor*, a moment of the
  day (ochtend, na school, rond het eten, avond, slapengaan; never a clock time): the
  morning and evening routines are laid out per moment; a chore with a moment belongs
  to the rhythm, one without to the household. A routine can have *steps* (`parentId`,
  `add_routine`): a sequence ("avondroutine: boodschappen, Mia ophalen, koken, pyjama")
  or a checklist ("paklijst opvang: luiers, flesjes"); steps are ticked per day and come
  back tomorrow; all ticked = the routine is done today),
  *Admin* (ADMIN/APPOINTMENT: *urgent*, deadline, *waiting for a reply* with a
  follow-up day), *Projecten* (PROJECT: *steps* via `parentId`, a day or "dit
  weekend"), *Te sorteren* (everything else).
- **Dashboard terms:** *Niet vergeten*, *Vandaag*, *In het ritme*,
  *Binnenkort*, *Vrije tijd* (generated from how full a day is, never from
  durations), *Lopende projecten*. The selectors live in
  `core/model/Overview.kt` so the screen, tools and MCP agree.
- **Werkbord** (`board_item`, not the backlog): *Huisregel* (RULE), *Frictie*
  (FRICTION → wordt huisregel / veranderen / loslaten; all three are good
  outcomes), *Doel* (GOAL, scope gezin / samen / persoonlijk, one small *next
  step*), *Idee* (IDEA: a way to make it easier; decided like a friction).
  *Member*: a person with a colour.
- **Voorraad:** *Storage spot* (a room or topic) with *Stock items*; tap = OUT
  and onto the list, bought = IN_STOCK again. Voice never adds on its own: the
  words show live, then "Klopt dit?". Every list row ends in an X that asks first.
- **Weekmenu & Recept:** a *Recipe* is a dish we know (in the `dish` table):
  saved on purpose (`isRecipe`, notes = how we made it, an optional photo) or
  remembered from the plan. Planning one puts its ingredients on the list.
  Stock spots are rooms *or categories* (Koelkast, Droge voeding, Snacks…).
- **Weekoverleg:** one page by *Topic* (*Vrije tijd*, *Admin & afspraken* (appointments), *Grote taken*,
  *Huishouden* (chores without a moment), *Ritmes & routines* (laid out per moment of the day), *Boodschappen*,
  *Weekmenu*, *Frictie & ideeën*, *Huisregels*): each a calm tile with a *glance*,
  tap to open its list. Terugblik (*Wat ging goed* = wins; two household
  *meters*, *Hoe liep het* (FLOW) and *Wat schuurde* (STRAIN), suggested by Hearth and
  agreed together, *Geen akkoord* = neutral; never per person, can be switched off),
  *Hoe liepen de huisregels?* (Liep goed / Liep nog niet, never who) under Huisregels,
  Deze week (7 days, plans with their colour, the meal small underneath),
  *Vooruitblik* (dates after this week: verlof, trips). No Oudjes. *Klaar* first shows
  *Samen akkoord?* (the summary to agree on), then records a *Huddle* with its *summary* (kept). *Luister mee* transcribes the meeting on a big stage that shows each sentence
  with its topic and lights up that tile (the *transcript* lasts until the next meeting); *Zet op zijn plek* opens the
  *Voorstel* (each sentence → a *Place*, checked with one tap). *Meal*: one per day; the *Dish* library remembers
  ingredients. Selectors in `core/model/Week.kt`.
- **Instellingen** (Settings; the rail's bottom item): *Taal* first (Dutch (Flemish) by default,
  English on request; `set_language`, setting `app.lang`; switching changes both the strings and
  what tools write), then Koppelingen.
- **Koppelingen** (ChatGPT/Claude and Google): *Relay* (`relay/`, one shared Cloudflare Worker), *connector address*
  (`<relay>/mcp`, the same for everyone), *koppelcode* / *pairing code* (6 digits on the tablet, typed on
  the relay's login page), *linked app* (*Gekoppeld*), *tablet key*. The MCP
  server lives in `mcp/`; tablet-only tools set `exposeOverMcp = false`. Google:
  *Back-up* (Drive app folder, *Terugzetten* asks first) and the *Hearth* calendar; setup in
  `docs/GOOGLE.md`.
- Never invent synonyms (no "grocery", "staple", "to-do" in new code or copy).
  UI ships in Dutch (Flemish), the default whatever the device says, and English (a setting);
  the local parser understands both.

## Code conventions

- Kotlin Multiplatform (Android + JVM for tests + WasmJs), Compose Multiplatform
  (Material 3), Room 3, Coroutines/Flow, kotlinx.serialization. Manual DI via an
  `AppContainer` (no Hilt) to keep the build and runtime light.
- UI strings live in `ui/src/commonMain/composeResources/values{,-nl}/strings.xml`
  (no Android-style escaping: use “ ” and ’). No emoji in UI or tool summaries:
  the web build has no emoji font; use the vectors in `HearthIcons`.
- Design system: `ui/.../theme` (Plus Jakarta Sans, warm canvas, one ember
  accent, 28dp cards, section colours in `SectionStyle`). Reuse `HearthCard`,
  `BacklogRow` (with `statusText`), `ChoiceChip`, `RoundCheck`, `AddRow` from
  `Components.kt`; `PlanSheet` adapts to the task kind. Navigation:
  rail on wide screens (≥ 840dp), bottom bar on phones; the capture bar sits
  under every screen.
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
(all tools, `HearthTools` wires them) → `ai` (parser + orchestrator) and `mcp` (MCP server,
relay client; `relay/` is the shared Worker) and `google` (Drive backup, Calendar push) → `ui` (Compose
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
