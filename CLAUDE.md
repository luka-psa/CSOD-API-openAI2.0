# Hearth — household assistant for a shared family tablet

Hearth is a local-first Android tablet app. It lives on one shared, always-on
tablet at home. The same code also runs as a web app (Kotlin/Wasm) for previews. The home screen is a chat "orchestrator"; the rest is a visual,
today-focused dashboard. The tablet *is* the server: there is no backend.

Read `docs/PROPOSAL.md` for the approved tool list, schema, layout and folder
structure, `docs/JOURNEYS.md` for the topics, where each one lives and the user journeys, and
`docs/ARCHITECTURE.md` for the diagrams IT staff review (layers, screens to tables, data flows leaving the
device). Keep all three in sync when any of those change.

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
  what kiosk mode needs. Local model is optional and downloaded on demand: *Slimmer luisteren* (Instellingen,
  `set_smart_listening`, a device-only setting) downloads multilingual-e5-small (ONNX, ≈ 112 MB + its 16 MB
  tokenizer) once and runs it on the device (ONNX Runtime on Android, ONNX Runtime Web in the browser, loaded only
  then). Code in `ai/sense`: our own `UnigramTokenizer` (same ids as the model's), `SenseModel` (mean-pooled meaning +
  a small classifier trained on `SENSE_EXAMPLES` when it loads; add sentences there to teach it), `SmartListening`
  (state, cache; the platforms give a `SenseRuntime`). In `MeetingWords` the rules keep what they are precise at (a
  yes, an option, a doubt, a question; free time, meals, shopping; what went well) and the model decides the rest
  when it is sure (≥ 0.4). Measured on 183 held-out sentences: rules 55 %, rules + model ≈ 83 %.
  *Gesprekken begrijpen* (`set_interpreter`, device-only, on by itself on a tablet, hidden while the build has no
  `hearth.interpreterUrl`; fetched in the background from the household's own download site, where CI copies it from
  the `interpreter-model` branch; repository variable `HEARTH_SITE_URL`) goes further: FunctionGemma 270M fine-tuned on weekoverleg talk (int4 ONNX, ≈ 270 MB,
  downloaded once) reads the conversation after the meeting, topic by topic (`ai/interpret`: `GemmaTokenizer`,
  `Interpreter`, `Interpreting` = state and one reading). Its actions become suggestions through `Orchestrator.proposalOf`.
  The meeting is read in two moments (`ai/meeting`): live only `MeetingIntake` (names, the household's own things and
  topic words, no model, so the CPU stays with the speech recognizer); after it `MeetingProcessor` reads the whole
  conversation (the household's own words, by topic, interpreter or rules, made-up and off-topic conclusions dropped,
  the last word on a plan counts) and `Matcher` holds each conclusion against what exists (update it, or "staat er al").
  The household's own words (`WordFix`, `WordFixes` in `core/model/Listening.kt`, setting `listen.words`) are learned
  from fixed lines and retyped names; the conversation export (`hearth-conversation/2`) is material to retrain it on
  real talk. Hearth needs no cloud model for any of this; MCP is an optional extra.
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
  het?") with a *score* from 0 ("komt er niet van") to 5 ("loopt vanzelf") (`BacklogItem.score`, `scoreWord`); its
  *Ease* (the weather) follows from it: EASY "loopt vanzelf" (green, 4–5) / EFFORT "vraagt moeite" (amber, 2–3) /
  HARD "te zwaar" (orange, 0–1, never red), with an optional *small step* ("Begin klein").
  Wherever a rated routine or house rule shows, it carries a *score badge* (`ScoreBadge`: weather and five pips).
  A chore's ease comes from the `routine_log`; a task's from how often it was
  *postponed* (2× amber, 4× orange). One *weather* shows ease everywhere: zon (loopt vanzelf), wolk (vraagt
  moeite), bliksem (te zwaar); what the family rates is the 0–5 score. A routine can hang on an *Anchor*, a moment of the
  day (ochtend, na school, rond het eten, avond, slapengaan): the
  morning and evening routines are laid out per moment; a chore with a moment belongs
  to the rhythm, one without to the household. What really happens on a day gets *fixed days* (`Schedule`,
  `set_routine_schedule`): weekdays, a date or the nth weekday of the month, a time only when it is fixed ("zwemles
  elke woensdag om 14:00"); on its day it is aan de beurt, after it it stays open until done or its next day (never
  "late"), and it repeats in its own Google calendar *Hearth · Ritmes*. Most routines need only a moment. The *habit
  coach* (`habitReview` in `core/model/Habit.kt`, `review_habit`) applies Atomic Habits' four laws to a habit:
  *Zichtbaar* (moment, days), *Aantrekkelijk* (*iets leuks erbij*, `pairing`, `set_habit_pairing`; or its goal),
  *Makkelijk* (small step, fewer steps, less often), *Voldoening* (Wat ging goed; never streaks). It reads the score per
  meeting (HABIT_CHECK rows, one per meeting) and the frictions about it (`lawOf` picks the law from their words) and
  suggests one thing: smaller when too much, one law when it takes effort or a friction is open, a step bigger after
  three sunny meetings (the last a 5). It lives in the habit's sheet (*Hoe loopt het?*: its score, trend and bars, the
  0–5 scale, and only when it needs one *Wat kan beter?*; moment, how often and days fold under *Ritme instellen*) and
  shows its tip in the Terugblik. A routine can have *steps* (`parentId`,
  `add_routine`): a sequence ("avondroutine: boodschappen, Mia ophalen, koken, pyjama")
  or a checklist ("paklijst opvang: luiers, flesjes"); steps are ticked per day and come
  back tomorrow; all ticked = the routine is done today; a second tap takes a tick back (`reopen_backlog_item`). Steps show small and in order (a bar, the next step,
  "daarna: …", *Alle stappen* numbered); their order (`position`, `move_step`) is set with arrows in the
  routine's sheet. With a moment it is daily; without
  one it is a *checklist* (*Lijstjes*: no rhythm, not on Vandaag, used when needed)),
  *Admin & afspraken* (ADMIN/APPOINTMENT: *urgent*, an end date ("klaar tegen"), *waiting for a reply* with a
  follow-up day), *Grote taken* (PROJECT: *steps* via `parentId`, a day or "dit
  weekend"), *Te sorteren* (everything else).
- **Dashboard terms:** *Niet vergeten*, *Vandaag*, *In het ritme*,
  *Binnenkort*, *Vrije tijd* (generated from how full a day is, never from
  durations), *Grote taken*. The selectors live in
  `core/model/Overview.kt` so the screen, tools and MCP agree.
- **Werkbord** (`board_item`, not the backlog): *Huisregel* (RULE), *Frictie*
  (FRICTION, talked through in two moves: first the meeting chooses what it needs in one tap,
  its *direction* (`FrictionDirection`, `mark_friction`): *huisregel te maken*, *nieuwe routine*,
  *routine aanpassen* (along the four laws: another moment, iets leuks erbij, kleiner beginnen or a step), *taak met focus* (admin or a grote
  taak, as next up), or *loslaten*; then someone writes it in their own words, now or later
  (`resolve_friction`; the friction is a reminder, never the wording); every outcome is good),
  *Doel* (GOAL, scope gezin / samen / persoonlijk; gets *a place in the week* (`set_goal_step` how): a *ritme*
  (a habit at a moment, e.g. elke dag yoga, in Ritmes & routines), *vrije tijd* on a day (a hobby, in Vrije tijd),
  or one small *next step*; the goal shows that link in its colour). Huisregels are rated like routines,
  0 to 5 ("Hoe loopt het?" on the Werkbord or in the Terugblik); the last score shows as its badge, and they get the
  same four laws as habits (`ruleReview`, `set_house_rule_help`: a moment, iets leuks erbij, a small version).
  Every Werkbord item opens its *sheet* with a tap (other words: `edit_board_item`); a house rule's and a routine's
  sheet read in three parts (`SheetTabs`): *Nu* (what it is, change it), *Hoe loopt het* (rate, trend) and
  *Verbeteren* (the four laws as four cards, `LawCards`, one suggestion each). A goal reached is celebrated
  (`GoalCelebration`) and stays on the *Bereikt* shelf (`achieve_goal reached = false` takes it back). *Idee* (IDEA: a way to
  make it easier; decided like a friction).
  *Member*: a person with a colour.
- **Voorraad:** *Storage spot* (a room or topic) with *Stock items*; tap = OUT
  and onto the list, bought = IN_STOCK again. Voice never adds on its own: the
  words show live, then "Klopt dit?". Every list row ends in an X that asks first.
- **Import:** a list pasted in the capture bar (one item per line, bullets fine): a heading says where it
  goes (a checklist, Boodschappen, Recept …), without one each line is sorted on its own (`ListWords`).
  A recipe link (`import_recipe`) reads the page's schema.org JSON-LD (name, ingredients, steps, photo);
  the tablet fetches pages itself, the web preview through the relay's `/page` and `/image`.
- **Weekmenu & Recept:** a *Recipe* is a dish we know (in the `dish` table):
  saved on purpose (`isRecipe`, notes = how we made it, an optional photo) or
  remembered from the plan. Planning one puts its ingredients on the list.
  Stock spots are rooms *or categories* (Koelkast, Droge voeding, Snacks…).
- **Topics** (`Topic`): one name, icon and colour per topic on every screen (`Topic.title`, `Topic.icon()`,
  `Topic.style()`), always in one order and two groups (`TopicGroup`): *Doen & eten* (Admin & afspraken, Grote taken,
  Huishouden, Vrije tijd, Weekmenu, Boodschappen) and *Ritme & afspraken* (Ritmes & routines, Huisregels, Frictie &
  ideeën, Doelen: what is rated and what shapes it); side by side, the four sit on a *shelf* (`Shelf`) next to the six. The rail follows it:
  Vandaag, Taken, Weekmenu, Boodschappen, Werkbord, Weekoverleg. A card on Vandaag (below the day, every topic, also with nothing for today), a panel in **Taken** (a
  dashboard of panels: Admin & afspraken, Grote taken, Huishouden, Vrije tijd, Ritmes & routines; *Te sorteren* on top;
  panels fold), a column on the
  Werkbord (Huisregels, Frictie & ideeën, Doelen), a tile in the weekoverleg. Ember is for time cards and
  Hearth's own voice, never a topic. A form with typed text never closes without asking (`FormSheet`,
  `DiscardDialog`: *Verder typen* / *Weggooien*).
- **Weekoverleg:** one page by *Topic* (*Admin & afspraken* (admin and appointments), *Grote taken*, *Huishouden*
  (chores without a moment), *Vrije tijd*, *Weekmenu*, *Boodschappen*, three by two; on the shelf beside them, two by
  two, *Ritmes & routines* (laid out per moment of the day), *Huisregels*, *Frictie & ideeën*, *Doelen* (next small
  step)): each a calm tile with a *glance*, all the same size, tap to open its list. Terugblik (`Terugblik.kt`: what got done and last time in one line each; *Alle overleggen* shows every *Huddle*'s
  summary, editable afterwards (`edit_huddle_summary`); *de week* (`rate_week`, suggested by Hearth, agreed together,
  *Geen akkoord* is an answer too; can be switched off); then *Wat bespreken we?*, a short list built together, never
  everything at once: say what went well (a *win*, `add_win` `topic`), or tap a topic and pick an existing routine or
  house rule to rate on one scale from 0 to 5 (`ScoreScale`; last time a ring, the meetings before as bars; the coach's
  one tip right there) or a done thing as a win; Hearth only suggests the few that ask for attention; never who; a rule
  that is too much (0–1) two meetings in a row comes back as a friction),
  Deze week (7 days, plans with their colour, the meal small underneath),
  *Vooruitblik* (dates after this week: verlof, trips). No Oudjes. *Klaar* first shows
  *Samen akkoord?* (the summary to agree on), then records a *Huddle* with its *summary* (kept). *Luister mee* transcribes the meeting on a big stage that shows the last lines with
  what Hearth recognises in its topic's colour and *Waar het over gaat* (the topics so far); no conclusions while people
  talk. *Zet op zijn plek* then reads the whole conversation as *thoughts* (stretches joined by how they sound, never cut
  at a pause) with their *conclusions*, held against what exists (*Werkt bij*, *Staat er al*, *Toch nieuw*); only what is *decided* becomes something (`Move`, `Stage` in `ai/route/MeetingWords.kt`): a plan, an
  agreement ("we spreken af dat …"), or an option the other says yes to ("kan jij Mia ophalen?" "ja"); catching up,
  looking back at ourselves, questions, doubts and a brainstorm's options stay talk (the thought shows its stage:
  *Bijpraten*, *Aan het brainstormen, nog geen besluit* …); one idea said on its own goes to the Werkbord (the
  *transcript* lasts until the next meeting; a tap on a line in *Gesprek* fixes it and teaches the words); the *Voorstel*
  shows each conclusion → a *Place*, checked with one tap; a tap on one opens it to change its name, *Place* (taak,
  afspraak, ritme, …) and day, filled in from what was said. *Meal*: one per day; the *Dish* library remembers
  ingredients. Selectors in `core/model/Week.kt`.
- **Instellingen** (Settings; the rail's bottom item): *Taal* first (Dutch (Flemish) by default,
  English on request; `set_language`, setting `app.lang`; switching changes both the strings and
  what tools write), then *Samen op meer toestellen*, then Koppelingen.
- **Samen op meer toestellen** (sync, like git): one device *deelt het huishouden*, others *doen mee met een code*
  (a one-time *join code*, 8 letters, ten minutes); each *sync* pulls, merges three ways against the *base*
  (`sync_base`) and pushes to the household's *SyncSpace* on the relay, sealed on the device (the relay never sees
  the key). A device's own settings (language, Google, relay link, sync keys) never travel. Tools `start_sync`,
  `share_sync`, `join_sync` ⚠, `sync_now`, `stop_sync`; code in `core/data/sync`, `core/tools/system/SyncTools.kt`,
  `mcp/RelaySync.kt`.
- **Koppelingen** (ChatGPT/Claude and Google): *Relay* (`relay/`, one shared Cloudflare Worker; its address is built
  in, never typed in the app), *connector address* (`<relay>/mcp`, the same for everyone), linked in four steps (copy or
  share the address, *Open Claude* / *Open ChatGPT* on their connector page, *Toon koppelcode*, done), *koppelcode* /
  *pairing code* (6 digits on the tablet, typed on the relay's login page), *linked app* (*Gekoppeld*), *tablet key*. The MCP
  server lives in `mcp/`; tablet-only tools set `exposeOverMcp = false`. Google:
  *Back-up* (Drive app folder, *Terugzetten* asks first) and the *Hearth* calendar; setup in
  `docs/GOOGLE.md`.
- Never invent synonyms (no "grocery", "staple", "to-do" in new code or copy). Dutch copy has no English
  words: Taken (not backlog), einddatum (not deadline), koppelserver (not relay); *admin* is fine in Dutch.
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
