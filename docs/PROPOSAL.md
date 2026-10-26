# Hearth — design

Status: **Approved (v2 + decisions in §9). Slice 1 built.**
v2 adds: shared terminology, the Stock ↔ Shopping loop, shops & one-off buys,
recurring household chores, and a Planning area (appointments, admin, projects).

## 1. The three pillars and their terms

The app is organised around three pillars plus **Today**. Each term below means one
thing everywhere: in the UI, in chat, in code and in tool names. UI ships in
**Dutch (Flemish) and English**; the chat parser understands both.

### Pillar A: Food & Stock (*Keuken & Voorraad*)

| Term (EN / NL) | Meaning | Example |
|---|---|---|
| **Stock item** / *Voorraaditem* | Something we always want at home. It has a state: `IN_STOCK`, `LOW` or `OUT`, plus a storage spot. | Milk (fridge), dishwasher tabs (under sink), paracetamol (bathroom) |
| **Storage spot** / *Plek* | Where stock lives. Used to walk through stock room by room. | Fridge, freezer, pantry, bathroom, cleaning cupboard |
| **Stock check** / *Voorraadcheck* | A quick walk through one or all spots: tap **OK / Low / Out** per item. | "Check the bathroom" |
| **Shopping item** / *Boodschap* | One line on the shared shopping list. It is either a **restock** (linked to a stock item) or a **one-off buy** (*losse aankoop*). | "Milk" (restock), "Medicine from the pharmacy" (one-off) |
| **Shop** / *Winkel* | Where an item is bought. The shopping list is grouped by shop. | Supermarket, Pharmacy (*Apotheek*), Gamma, IKEA, Bakery |
| **Meal plan** / *Weekmenu* | Optional weekly meals. Ingredients that aren't in stock become shopping items. | |

### Pillar B: Home & Rhythm (*Huis & Ritme*)

| Term | Meaning | Example |
|---|---|---|
| **Rhythm** / *Ritme* | A fixed daily sequence tied to a moment of the day. Resets every day. | Evening routine: dishes → lunchboxes → table clear |
| **Chore** / *Huishoudtaak* | Recurring upkeep with a **flexible interval** ("about every 2 weeks") and an optional **season**. No due date. It slowly becomes "ready" again after it's done. | Weed the terrace (*onkruid wieden terras*), every ~2 wks, Apr–Oct · Mow the lawn (*gras maaien*), ~weekly, Apr–Oct · Change sheets (*lakens verversen*), ~2 wks · Clean windows (*ramen kuisen*), ~2 months |
| **Anchor habit** / *Gewoonte* | "After X, do Y". Shown while forming, then faded out. | After coffee → empty the dishwasher |
| **Protected time** / *Vrije tijd* | Recurring fun/rest block, shown as a reward. | Friday movie night |

### Pillar C: Planning (*Planning*)

| Term | Meaning | Example |
|---|---|---|
| **Appointment** / *Afspraak* | Something to **book** and then **attend**. Lifecycle: `TO_BOOK` → `BOOKED` (date, place) → `DONE`. It can repeat ("every year"). | Osteopath, car inspection (*autokeuring*), dentist |
| **Admin** / *Admin* | Paperwork with an optional deadline (end date). Topic: *Admin & afspraken*. | Taxes, insurance, school forms |
| **Project** / *Grote taak* | Bigger job made of **steps**. It can link to shopping items (materials), errands and appointments. | Hang lamps in the toilet · Clear out & clean the whole kitchen (*keuken uitkuisen*) · New cupboard |
| **Errand** / *Rit* | A trip to one shop. It automatically shows every open shopping item for that shop. | IKEA run for the cupboard, Gamma for paint |
| **Step** / *Stap* | A sub-task of a project (or any task). Can be assigned to a helper, but the owner stays the owner. | "Buy paint", "Remove old lamp" |

### Shared across pillars

| Term | Meaning |
|---|---|
| **Area** / *Domein* | A zone of responsibility with one **owner**, e.g. Kitchen, Garden, Admin, Car, Kids. Every chore, project, appointment and stock spot belongs to an area. |
| **Owner** / *Eigenaar* | The person who notices *and* follows through for an area. Default assignee. |
| **Helper** / *Helper* | Someone assigned a linked step; the owner keeps ownership. |
| **Batch** / *Bundel* | A set slot that collects similar items, e.g. "Calls, Tue 10:00". |
| **Friction** / *Ergernis* | An annoying task plus ideas to fix it: open / to try / to buy / solved. |
| **Note** / *Huisregel* | A household rule or agreement for reference. |
| **Done log** / *Gedaan* | Every completed thing, so work becomes visible. |

## 2. How the parts link

```
          check: Low/Out                     tick "bought"
 Stock item ───────────────▶ Shopping item ───────────────▶ Stock item = IN_STOCK
     ▲                           │  ▲   │
     │ meal plan only adds       │  │   └─ grouped by Shop ──▶ Errand ("Gamma run")
     │ what isn't in stock       │  │                             ▲
 Meal plan ──────────────────────┘  │                             │
                                    │ materials                   │
 Friction idea "to buy" ────────────┤                             │
                                    │                             │
 Project ── steps ──▶ Step / Helper step                          │
    │  └──────────── needs shop trip ─────────────────────────────┘
    └── needs appointment ──▶ Appointment TO_BOOK ──▶ Batch "Calls" ──▶ BOOKED ──▶ Google Calendar
                                                                         │
                                         (repeats yearly, e.g. keuring) └──▶ DONE ──▶ next TO_BOOK

 Chore done ─▶ resets its interval ─▶ Done log      Rhythm step ticked ─▶ Done log
```

The rules behind the diagram:

1. **Restock loop.** Marking a stock item `LOW` or `OUT` (or a counted item dropping to its low mark) adds one shopping item linked to it (never a duplicate). Ticking that shopping item as bought sets the stock item back to `IN_STOCK`. Saying "add milk" in chat when *Milk* is a stock item links to it and marks it `LOW`.
2. **One-off buys** are shopping items without a stock item. When ticked they just leave the list. Chat can tell them apart: "don't forget medicine from the pharmacy" becomes a one-off with shop = Pharmacy.
3. **Shops → errands.** Every shopping item has a shop (default: Supermarket). An errand for a shop shows all open items for that shop live. Ticking items in the errand is the same action as ticking them on the list.
4. **Projects pull things in.** A project can create shopping items (materials, with a shop), errands and appointments. These keep a link back, so the project shows its progress across all of them.
5. **Appointments to book land in the "Calls" batch** automatically. Once booked, they go to Google Calendar (if enabled). Repeating appointments create the next `TO_BOOK` when done.
6. **Chores never become "late".** Each has `lastDoneAt` and an interval with slack. The UI shows a soft "readiness" (fresh → ready → really ready). Outside its season a chore is hidden.
7. **Everything done goes to the Done log**, which drives "done this week" and the progress card.
8. **Area owner is the default** for anything created in that area; the AI may suggest a helper based on style (quick vs. project).

## 3. Tool list

`⚠` = destructive (needs confirmation). All tools are typed; schemas generated.

**Built so far** (one `backlog_item` table carries routines, admin, projects and steps until each pillar
gets its own tables): `add_shopping_item`, `list_shopping_items`, `mark_bought`, `unmark_bought`,
`remove_shopping_item` ⚠ · `add_to_backlog` (section, when, urgent, intervalDays), `list_backlog` (views:
ALL, TODAY, NEXT, UNPLANNED, WEEK, DONT_FORGET, ROUTINES_DUE, ROUTINES, ADMIN, PROJECTS, WAITING),
`plan_backlog_item`, `complete_backlog_item` (a routine logs "done today" and stays; a project takes its
steps along), `reopen_backlog_item`, `remove_backlog_item` ⚠ (a project's steps go too),
`set_routine_interval`, `mark_urgent`, `wait_for_reply` (follow-up day), `move_backlog_item`,
`add_project_step` (creates the project if needed) · Werkbord: `add_member`, `remove_member` ⚠,
`add_house_rule`, `log_friction`, `add_goal` (FAMILY/COUPLE/PERSONAL), `list_board`, `mark_friction` (what it
needs: RULE, NEW_ROUTINE, CHANGE_ROUTINE, TASK; it stays open, labelled), `resolve_friction` (in the family's own
words: RULE → a house rule, CHANGE → a task as next up, ROUTINE → a new routine with its moment, ROUTINE_CHANGE →
a step and/or another moment for an existing routine, LET_GO), `set_goal_step` (a goal's place in the week: STEP, optionally onto the backlog as next up; HABIT, a
routine at a moment; FREE_TIME, free time on a day), `achieve_goal`, `remove_board_item` ⚠. `add_to_backlog` / `move_backlog_item` with
NOTE, FRICTION or GOAL put the item on the Werkbord. · Stock: `list_stock`, `add_stock_item`, `add_storage_spot`,
`set_stock_state` (LOW/OUT → a linked shopping item, once; IN_STOCK takes it off), `remove_stock_item` ⚠,
`clear_bought_items` ⚠. `mark_bought` puts linked stock back IN_STOCK; `add_shopping_item` links to a stock item
with the same name. · Weekoverleg: `plan_meal` (one meal per day; ingredients onto the shopping list; a known
dish brings its ingredients), `list_meal_plan`, `remove_meal` ⚠, `weekoverleg_agenda` (look back since the last
meeting and wins, then every *topic* in a few words with its details, the meters' suggestions and the Luister mee
transcript, so the family's AI can sort it), `finish_weekoverleg` (writes the summary; `preview` only writes it,
for the family to agree on before Klaar), `add_win`,
`remove_win` ⚠ (tablet), `set_week_meter` (FLOW / STRAIN, agreed together for the household; empty = no
agreement = neutral 3), `set_week_meters` (on/off, tablet), `check_house_rule` (EASY / EFFORT / HARD like the routines, never who), `rate_habit` (EASY / EFFORT / HARD),
`import_recipe` (a link: the page's schema.org JSON-LD becomes a Recipe with ingredients, numbered steps and
"Bron: …" as notes, and its photo; the web preview fetches through the relay's `/page` (only JSON-LD and
og:image come back) and `/image` (images only, max 3 MB)), `set_small_step` (a habit's smallest first step), `add_routine` (a routine with its steps in order: the evening
sequence or a checklist like the bag for the opvang; saying it again adds the missing steps; steps are ticked per
day with `complete_backlog_item`, and all ticked logs the routine for today), `set_anchor` (a routine's moment of the day: MORNING, AFTERNOON,
DINNER, EVENING, BEDTIME; `add_to_backlog` takes it too),
`list_huddles`
(summaries), `add_transcript_line` (tablet), `read_conversation` (the meeting's lines with ids and times, for the
family's AI: it reads for meaning, adds with the regular tools, then calls `mark_transcript_sorted`),
`fix_transcript_line` (misheard words; only the open meeting), `clear_transcript` ⚠ (tablet),
`add_idea` (Werkbord; decided with `resolve_friction` like a friction). · Recipes:
`save_recipe` (name, ingredients, how we made it; rename keeps the photo), `list_recipes`, `remove_recipe` ⚠,
`set_recipe_photo` (tablet only). Stock: `move_stock_item`, `remove_storage_spot` ⚠.
Google (tablet only, opt-in, see `docs/GOOGLE.md`): `set_google_sync`, `backup_now`, `list_backups`,
`restore_backup` ⚠, `sync_calendar`.
Language (tablet only): `set_language` (NL / EN, stored as `app.lang`). The app is Dutch (Flemish) by default,
whatever the device or browser says; Instellingen → Taal switches it, strings and tool texts alike, and the
recognizer follows (nl-BE / en-US).
Examples (tablet only): `load_examples` puts one or two items in every section through the normal tools (Koppelingen →
Voorbeelden laden). The web preview loads them on a first visit, before the screen shows.

The full target list:

**Household:** `get_household`, `add_member`, `update_member`, `remove_member` ⚠, `add_area`, `update_area`, `remove_area` ⚠, `set_area_owner`

**Stock & shopping**
- `list_stock` (spot?, state?) · `add_stock_item` · `update_stock_item` · `remove_stock_item` ⚠
- `set_stock_state` (itemId, IN_STOCK|LOW|OUT): LOW/OUT auto-adds shopping item
- `set_stock_count` (itemId, count): for counted items; at or below `lowAt` it auto-adds a shopping item
- `start_stock_check` (spot?) → ordered list of items to walk through
- `add_shopping_item` (name, quantity?, shopName?, note?): links to stock if it matches, otherwise one-off
- `list_shopping_items` (shop?) · `mark_bought` (itemId[]): restores stock · `unmark_bought`
- `remove_shopping_item` ⚠ · `clear_bought_items` ⚠
- `add_shop` · `list_shops`
- `set_meal_plan` · `get_meal_plan` · `shop_for_meal_plan`: adds only what isn't in stock

**Home & rhythm**
- `list_rhythms` · `add_rhythm` · `update_rhythm` · `delete_rhythm` ⚠ · `tick_rhythm_step`
- `add_chore` (title, area, intervalDays, slackDays?, seasonFrom?, seasonTo?, size?) · `update_chore` · `delete_chore` ⚠
- `list_chores` (ready only?, member?) · `mark_chore_done` · `snooze_chore` (days)
- `add_anchor` · `update_anchor` · `fade_anchor` · `delete_anchor` ⚠ · `log_anchor_done`
- `add_protected_time` · `update_protected_time` · `delete_protected_time` ⚠

**Planning**
- `add_appointment` (title, area?, who?, contact?, repeatEveryMonths?) · `book_appointment` (id, dateTime, place?) · `mark_appointment_done` · `list_appointments` (status?) · `delete_appointment` ⚠
- `add_admin_task` (title, deadline?) · `list_admin`
- `add_project` (title, area?, owner?) · `add_project_steps` (projectId, steps[]) · `break_down` (AI writes the steps; tool stores them) · `ask_for_help` (stepId, helperId) · `link_to_project` (projectId, shoppingItem|appointment|errand)
- `add_errand` (shop, forProjectId?) · `list_errands`
- Generic for tasks/steps/admin: `complete_task`, `reopen_task`, `update_task`, `delete_task` ⚠

**Today & cross-cutting**
- `get_today` (member?) · `whats_open` (member?) · `suggest_quick_task` (member?, minutes?) · `get_progress` (TODAY|WEEK)
- Batch: `add_batch` · `add_batch_item` · `complete_batch_item` · `list_batch`
- Friction: `log_friction` · `add_friction_idea` · `set_friction_status` · `idea_to_shopping` · `delete_friction` ⚠
- Notes: `add_note` · `update_note` · `search_notes` · `delete_note` ⚠
- System (not over MCP): `confirm_pending_action`, `cancel_pending_action`, `set_calendar_sync`, `push_to_calendar`, `backup_now`, `restore_backup` ⚠
- Sync (v0.19, tablet only): `start_sync` (this device shares its household and gets a join code), `share_sync`
  (a one-time code for one more device), `join_sync` ⚠ (code; takes the household over, or `keepThisDevice` adds
  this device's things), `sync_now` (pull, merge, push; the capture bar understands "synchroniseer"), `stop_sync`

## 4. Data schema (Room)

All tables: `id TEXT PK (UUID)`, `createdAt`, `updatedAt`. Enums as TEXT.

| Entity | Key columns |
|---|---|
| **Household** | name, locale (nl-BE / en), timezone, dayStartsAt, dayMoments JSON |
| **Member** | name, color, avatar, style (DOER/PLANNER), prefersSize (QUICK/PROJECT/MIXED), strengths JSON, isKid |
| **Area** | name, icon, ownerId |
| **StorageSpot** | name, icon, areaId?, sortOrder |
| **Shop** | name, kind (SUPERMARKET/PHARMACY/DIY/FURNITURE/OTHER), sortOrder |
| **StockItem** | name, normalizedName, spotId, defaultShopId, tracking (STATE/COUNT), state (IN_STOCK/LOW/OUT), count?, lowAt?, restockCount?, lastCheckedAt |
| **ShoppingItem** | name, normalizedName, quantity?, note?, shopId, stockItemId? (null = one-off), projectId?, mealPlanId?, frictionIdeaId?, bought, boughtAt?, addedById? |
| **MealPlan / MealPlanDay** | weekStart · day, meal, ingredients JSON |
| **Rhythm / RhythmStep / RhythmTick** | name, dayMoment, memberIds, active · title, order · stepId, date, byId |
| **Chore** | title, areaId, ownerId?, intervalDays, slackDays, seasonFromMonth?, seasonToMonth?, size, lastDoneAt?, lastDoneById?, dayMoment? |
| **Task** | kind (TODO/ADMIN/PROJECT/STEP/ERRAND), title, notes, areaId?, ownerId?, assigneeId?, parentId? (project → steps), size, deadline?, shopId? (errands), status (OPEN/DONE/SKIPPED), completedAt?, completedById?, sortOrder |
| **Appointment** | title, areaId?, forMemberId?, contact?, status (TO_BOOK/BOOKED/DONE), scheduledAt?, place?, repeatEveryMonths?, projectId?, calendarEventId? |
| **Anchor / AnchorLog** | memberId?, afterWhat, doWhat, phase (FORMING/FADING/FORMED) · anchorId, date |
| **ProtectedTime** | title, kind (FUN/REST), memberIds, recurrence, startTime, durationMin, calendarEventId? |
| **Batch / BatchItem** | name, slotRecurrence, slotTime · batchId, title, appointmentId?, done |
| **FrictionEntry / FrictionIdea** | title, areaId?, status · entryId, text, tried |
| **Note** | title, body, tags, pinned |
| **DoneLog** | sourceType (CHORE/TASK/RHYTHM/APPOINTMENT/SHOPPING), sourceId, memberId?, doneAt |
| **ChatMessage / PendingAction** | as in v1 |

**Built so far:** `Shop`, `ShoppingItem`, and `BacklogItem` (v4): text, section, plan (UNPLANNED/NEXT/DAY/LATER),
plannedDate, finishBy, done/doneAt, urgent, waiting (date = follow-up day), intervalDays + lastDone (routines),
parentId (project steps). v5 adds `Member` (name, colour) and `BoardItem` (kind RULE/FRICTION/GOAL, status
OPEN/BECAME_RULE/BECAME_CHANGE/LET_GO/ACHIEVED, memberId, scope FAMILY/COUPLE/PERSONAL, nextStep, linkedId,
closedAt); the v4→v5 migration moves open house rules and frictions off the backlog. v6 adds `StorageSpot`
(name, sortOrder) and `StockItem` (name, spotId, state IN_STOCK/LOW/OUT, shopId?); first run seeds Keuken,
Badkamer, Wasplaats and Medicijnkast with everyday items (medicine from the Apotheek). v7 adds `Meal` (date
unique, dish, ingredients), `Dish` (the dish library: name, ingredients, lastUsedOn) and `Huddle` (heldOn).
v9: `Dish` becomes the *Recipe* (notes = how we made it, hasPhoto, isRecipe) with `recipe_photo` (JPEG ≤ 800 px,
apart so lists stay light). Stock layout 2: Koelkast, Vers, Droge voeding, Snacks, Diepvries, Drank, Badkamer,
Wasplaats, Medicijnkast; households with the first layout get these once and their kitchen items move along. v10
adds `reflection` (WIN with text / FEELING with value 1–5, memberId, huddleId null until the meeting ends),
`transcript_line` (text, sorted, huddleId), `Huddle.summary`, and `BoardKind.IDEA`. A meeting's transcript is
deleted when the next meeting ends; summaries stay. v11 adds `routine_log` (habit id, day) and
`reflection.suggested`; meters are FLOW / STRAIN rows, house-rule checks RULE_CHECK rows (v0.6's per-person FEELING
rows are no longer used). v12 adds `backlog_item.postponed` (moved to a later day, Later or To plan), `ease`
(the family's rating of a habit) and `smallStep`. v13 adds `backlog_item.anchor` (a routine's moment of the day). v16 adds
`sync_base` (per sync space: the version and snapshot this device last agreed on with the household, the merge
base). Auto-migrations keep every household's data.

Changes from v1: *GroceryItem* is now **ShoppingItem** (with shop + optional stock link). *Staple* is now **StockItem** (with state and storage spot). New: **StorageSpot**, **Shop**, **Chore**, **Appointment**, **DoneLog**. Task gains `kind`.

## 5. Navigation & dashboard

Rail on the tablet (≥ 840dp), bottom bar on phones, the capture bar under every screen.

```
 Vandaag (dashboard) | Taken | Weekmenu | Weekoverleg | Werkbord | Boodschappen      (Koppelingen at the bottom)
```

**Vandaag: the tablet dashboard** (three columns on the tablet, one on a phone):

```
┌ Niet vergeten ─────────┐ ┌ Vandaag ─────────────────┐ ┌ Binnenkort ──────────────┐
│ ● verzekering  dringend│ │ ▌Vrije tijd: lichte dag  │ │ Morgen  ● belastingbrief⚑│
│ ● belastingbrief       │ │ ○ osteopaat bellen       │ │ Zo 25   ● lampen toilet  │
│   klaar tegen morgen   │ │ In het ritme             │ │ Wo 28   (vrij)           │
├ Boodschappen ──────────┤ │ ○ vaatwasser · elke dag  │ │ "Zaterdag is nog vrij."  │
│ Melk  Brood            │ │ ○ lakens · om de 2 weken │ ├ Grote taken ─────────────┤
└────────────────────────┘ │ Volgende                 │ │ IKEA kast ▓▓░ 1/3 stappen│
                           └──────────────────────────┘ └──────────────────────────┘
```

- **Niet vergeten**: urgent items, deadlines ("klaar tegen") within 3 days, follow-ups that are due.
- **Vandaag**: what's planned today (earlier days read "sinds dinsdag"), the routines that are about time
  ("In het ritme"), and what's next up with a one-tap "Vandaag".
- **Vrije tijd is generated**, without durations: nothing left today means free ("Alles van vandaag is
  gedaan…"), one or two things means a light day; in *Binnenkort* empty days read "vrij" and the first free
  weekend day is called out.
- **Binnenkort**: the next six days with what's on them. **Grote taken**: progress and the next step.
- **One topic, one look** (v0.15): every topic (`Topic`) has one name, icon and colour on every screen: a card
  on Vandaag (Boodschappen, Huisregels, Grote taken), a panel in Taken, a column on the Werkbord, a tile in the
  weekoverleg. Time cards (Vandaag, Niet vergeten, Binnenkort, Deze week, Terugblik) and Hearth's own voice keep
  the ember accent. The map of topics, icons and the user journeys: `docs/JOURNEYS.md`.

**Taken** (v0.15), a dashboard of panels like Vandaag, one per work topic with the same name, icon, colour
and glance as its weekoverleg tile: *Admin & afspraken* (Dringend · Met een einddatum · Wacht op antwoord ·
Open), *Grote taken* (steps with progress, add a step inline), *Huishouden* (Aan de beurt · Loopt goed · Nog geen
ritme; the tick logs "done today", it returns after its interval), *Ritmes & routines* (per moment of the day,
then Lijstjes and Gewoontes) and *Vrije tijd*. Each panel has its own add field and folds with a tap on its
title (on a phone they start folded: the first look is the overview). *Te sorteren* sits on top, only when
something is unsorted. No filter chips and no separate Week tab: the days live on Vandaag (Binnenkort) and in the
weekoverleg (Deze week).
The plan sheet adapts to the kind: how often / give it a day (routine); urgent, waiting for a reply with a
follow-up day, do on / finish by (admin); next, this weekend, a day, later (project/step); and "Hoort bij"
to move between kinds.

**Werkbord**: who lives here (name chips with a colour), then three columns: *Huisregels* (numbered),
*Frictie & ideeën* (each one first gets what it needs, "Wat is er nodig?": Huisregel · Nieuwe routine · Routine
aanpassen · Taak met focus · Loslaten; a chosen one shows "Huisregel te maken" in the colour of where it goes, with
"Schrijf de huisregel" to word it now or later; sorted-out ones stay visible as wins) and
*Doelen* (Gezin · Samen · per person, each with one small next step that can go onto "volgende"; reached
goals stay as wins). The dashboard shows *Huisregels*: three house rules and how many frictions wait
for the weekoverleg.

**Boodschappen**: stock per room or category (hold an item to move it, hold a category to remove it); the list (grouped by shop, X to remove with a confirm, "Gekochte wissen" once things are
ticked) next to the **Voorraad** (chips per room/topic; tap an item = it's out and goes on the list; tap again
= we have it). Phone: two tabs.

**Capture bar & voice**: the mic shows the words live in the field while listening, then asks "Klopt dit?"
(Toevoegen / Wis): nothing is added by voice alone. An NL/EN pill picks the listening language; the parser
understands both. Android listens in-app (SpeechRecognizer with partial results, RECORD_AUDIO asked on first
use); the web uses the Web Speech API with interim results. "de koffie is op", "we zijn bijna door het
wc-papier", "we're out of milk" mark the stock (or put it on the list when it isn't a stock item).

Every list row has an X at the end; it always asks before deleting.

**Weekmenu**: the coming seven days next to the *Recepten* (cards with photo). Tap a day → pick a recipe
(one tap; its ingredients go onto the list) or type "iets anders" (dish + ingredients, optionally kept as a
recipe). *Nieuw recept*: take or choose a photo, tell what you made (typed or with the mic); the name and
ingredients fill in from the story (`RecipeWords`) and stay editable. Capture bar: "recept: pannenkoeken met
bloem, melk en eieren". Phone: tabs Deze week / Recepten.

**Weekoverleg** (v0.10): one page built around the household's *topics* (`Topic` in `core/model/Week.kt`):
*Vrije tijd*, *Doelen*, *Admin & afspraken*, *Grote taken*, *Huishouden*, *Ritmes & routines*, *Boodschappen*, *Weekmenu*, *Frictie & ideeën*, *Huisregels* (two rows of five on the tablet). On top
the **Luister mee** stage: a big mic with breathing rings and moving bars while it listens, the words as they come
in, the topics as a row of dots (the one the last thought went to glows), and the last *thoughts*, each with
its *conclusions* as pills in their topic's colour ("Naar de opvang · donderdag"). A pause never cuts a thought
(v0.18): stretches join by how they sound ("dus …", "die …", "voor de rest …", a half sentence, a fragment without
subject or verb, a day said after a pause); "oh ja", a new subject or two minutes of silence start the next one. Hearth
waits about four seconds after the last words ("Hearth luistert nog…") before it shows what it concludes, and a tap
on a thought fixes words the recognizer misheard. Below it every topic is a calm tile with a few words (`Topic.glance`: "3 open · 1 wacht op
antwoord", "3 van 7 avonden"); the tile Luister mee just heard about glows too. Tapping a tile opens its list under
the grid (free time and admin with an add field, projects with their next step and "dit weekend", chores aan de
beurt and the habit rating, the shopping list, the meals of the week, the house-rule check and frictions, the goals with their next small step). In Dutch *afspraken* always means appointments; house rules are *huisregels*.
Then *Terugblik* (last meeting's summary, what got done, *Wat ging goed*, the meters) and *Deze week* (7 days, what
has that day with its section colour, the meal small underneath) and *Vooruitblik* (dates after this week). There
are no Oudjes: what has no topic waits in Taken → Te sorteren. *Klaar* first opens *Samen akkoord?*: the summary
(by topic) to read together, with a nudge when sentences still wait in the Voorstel; *Akkoord, klaar* records it.
Terugblik also has *Wat ging goed* (wins, X asks first) and two household meters, *Hoe liep het?* and *Wat
schuurde?* (on by default, can be switched off). Hearth suggests (a ring and a faint fill): Hoe liep het starts at
"goed" because small things and good habits get forgotten, "heel goed" with plenty done and no habit asking for
attention, never below 3 (`flowSuggestion`); Wat schuurde rises with new frictions and agreements that didn't work
(`strainSuggestion`). The family agrees with a tap, picks another step together, or taps *Geen akkoord* (neutral).
The summary shows Hearth's guess when the family saw it differently. Terugblik (v0.16) has *Hoe lopen de ritmes en
huisregels?*: every routine (in the order of the day) and every house rule on one scale, loopt vanzelf / vraagt
moeite / te zwaar, never who, with a smaller first step right there; a rule that is too much two meetings in a row
comes back as a friction ("Huisregel loopt nog niet: …"). The Ritmes and Huisregels tiles show how they go and
point to the Terugblik. *Gewoontes* (Atomic Habits): habits are not ticked off;
the family rates each one together (*Ease*: loopt vanzelf green / vraagt moeite amber / te zwaar orange, never red) and
can give it a *small step* ("Begin klein: …"). Chores get their ease from the `routine_log`; tasks from how often
they were postponed (2× amber, 4× orange; saying a task again with a later day moves it and counts). *Vraagt aandacht*
lists both and offers *Minder vaak* (interval ×2) or *Makkelijker maken* (an idea on the Werkbord). In Taken habits
have their own group with the rhythm icon and a "Hoe loopt het?" chip; never "late". **Luister mee** (it keeps listening while the family opens the list, the
stock or the tasks, with a small "Terug naar het overleg" bar there): continuous transcription during the meeting (Web Speech;
Android restarts its recognizer after each pause and prefers on-device recognition; both ask for three guesses and keep
the one with the household's own words, names, stock and recipes, which they also get as hints where the platform
supports it: phrase hints in the browser, biasing strings on Android 13+). What Hearth reads is cleaned first
(`Spoken`: "euh", "allee", "gewoon" out; "da", "ge", "nie", "'k" as plain Dutch); the transcript keeps what was said.
*Gesprek* shows what was heard (tap a line to fix it); *Zet op zijn plek* opens the Voorstel: each conclusion,
grouped by topic, with what was said around it (tap the words to fix them and Hearth reads them again) and its
guessed place (Taken, Afspraak, Vrije tijd,
Boodschappen, Weekmenu, Huisregel, Frictie, Idee, Doel, Ging goed, Gewoon gepraat; *Agenda* takes dates further
ahead too: "verlof van 3 tot 7 november", "oma is jarig op 12/12", "volgende maand op vakantie"), all ticked, a tap on the
place moves it, *Toevoegen* runs the same tools. A thought that was just talk comes once, as *Gewoon gepraat*.
Sorting is local (`MeetingWords.thoughts` + the capture-bar rules, `Orchestrator.listen` / `propose`); the family's own
AI understands dialect and half sentences far better and reads the conversation through `read_conversation`.
The dashboard shows *Wat ging goed* until the meeting takes it along; "dat ging goed: …" in the capture bar adds one.
The dashboard shows "Vanavond: <dish>" and, Friday to Sunday when no meeting was held for 6 days, a calm
"Tijd voor het weekoverleg?" card with Start.

## 6. Folder structure

Kotlin Multiplatform: shared code in `commonMain`, platform glue in `androidMain` / `wasmJsMain`.

```
core/model     plain data + helpers (common only)
core/data      Room 3 entities, DAOs, repositories; openHearthDatabase() per platform
  worker/      SQLite web worker (npm package) used by the browser build
core/tools     Tool interface, schema generation, registry, all tools by pillar
ai             LocalIntentParser (NL + EN), Orchestrator; later LlmProvider + local model
ui             Compose screens, HomeViewModel, AppContainer, strings (NL/EN), icons
app            Android: MainActivity, kiosk, Application
web            Browser: main(), index.html, vercel.json
mcp            MCP server + the tablet's relay client (relay/ is the shared Worker)
google         Drive appDataFolder backups, Hearth's own Google calendar, sign-in per platform
```

Dependency direction: `app, web → ui → ai → core:tools → core:data → core:model`.

## 7. AI: your own subscription over MCP (no API keys)

**Decision:** Hearth makes no paid model calls. The family's ChatGPT or Claude (normal subscription) is the
brain; it reads and changes Hearth through Hearth's MCP server, using exactly the same tools as the UI.

```
Phone / laptop                      Tablet (the server)
ChatGPT or Claude app  ── MCP ──▶   Hearth MCP server ─▶ ToolRegistry ─▶ Room
  "zet de tandarts op vrijdag"       plan_backlog_item(texts=["tandarts"], when=DAY, date=…)
```

On the tablet itself the **capture bar** is local and instant:
1. Rules (NL/EN) for clear commands: "voeg melk toe", "wat staat er op de lijst?", "melk gekocht".
2. Keyword sorter: everything else goes on the **Backlog** with a section label; a trailing time word plans it
   ("tandarts morgen", "belastingbrief tegen vrijdag"); "de tandarts is geregeld" marks it done.

**The constraint to solve:** ChatGPT and claude.ai call MCP connectors *from their cloud*, so they need a public
HTTPS address; a tablet on home Wi-Fi has none. Options:

| Option | How | Trade-off |
|---|---|---|
| A. Tiny relay (recommended) | Tablet keeps one outbound connection to a small relay (e.g. a Cloudflare Worker); the relay exposes the MCP URL and forwards calls. | Works anywhere, no router setup; one small piece of hosted glue (stateless, no data stored). |
| B. Tunnel on the tablet | Cloudflare Tunnel / Tailscale Funnel exposes the tablet directly. | No relay, but extra app/setup on the tablet; support on Android varies. |
| C. Claude Desktop on a home laptop | Local MCP over the LAN. | No public URL at all, but only works from that laptop, not phones; ChatGPT can't do this. |

**Chosen: A, one shared relay for every family (v0.5).** The maintainer deploys `relay/` once (Cloudflare
Worker, "Deploy relay" workflow); its address is baked into the builds (`-Phearth.relayUrl`; CI finds it from the
Cloudflare account, or the variable `HEARTH_RELAY_URL`). Nobody types it in the app. Families never handle keys:

1. Each person adds the same connector address `<relay>/mcp` in Claude or ChatGPT.
2. The AI app opens the relay's Hearth page; they tap **Toon koppelcode** on the tablet and type the 6-digit
   code there. That is a standard MCP OAuth 2.1 login: RFC 9728 resource metadata (401 with
   `WWW-Authenticate`), RFC 8414 server metadata, dynamic client registration (stateless client ids) and
   Client ID Metadata Documents, PKCE S256, `resource` checked, 1 h access tokens, rotating refresh tokens.
3. The tablet lists linked apps (**Gekoppeld**) and unlinks each with a confirm.

**Built:** `relay/src/worker.js`: `Household` Durable Object per tablet (keyed by a hash of the tablet's
household id; trust-on-first-use tablet key; linked apps with token hashes; tokens start with the object's id,
so no global token table) and one `Directory` object (pairing codes: 10 min, single use; 10 wrong guesses per
IP per 15 min). Tested in workerd (`relay/test`) and end to end from Kotlin (`RelayEndToEndTest`). `mcp/`:
`McpServer` (JSON-RPC: initialize, tools/list with generated schemas and hints, tools/call through the
`ToolRegistry`, `confirm_pending_action` / `cancel_pending_action`) and `RelayClient` (one outgoing WebSocket,
heartbeat, backoff; pairing codes, linked apps, unlink). Settings (`setting` table) through the tablet-only
`save_connection` tool; household id and tablet key are made on the device and never leave it except to the
relay. Pending confirmations show on the tablet with Ja/Nee too.

**Sync between devices (v0.19): the relay as a git remote.** The household's devices (the tablet, phones, an iPad in
the browser) keep one household. Each sync is `pull`, `merge`, `push`:

- **Remote:** a `SyncSpace` Durable Object per household holds one snapshot (the backup's JSON) and its version. A push
  names the version it was built on; when another device pushed first it gets 409 and the device pulls, merges and
  tries again. The first push sets the household's token (a hash is stored); only that token reads or writes.
- **Sealed:** the snapshot is sealed on the device with AES-256-GCM under a household key the relay never sees
  (`SyncCrypto`: javax.crypto on Android, WebCrypto in the browser). The relay stores a locked box, a version and a
  token hash. Spaces untouched for a year are deleted.
- **Joining:** a device in the household makes a one-time code (8 letters, no 0/O/1/I, "K7QM-3XPD"); the keys are
  sealed with a key derived from it (PBKDF2, 100 000 rounds) and left under a lookup derived the same slow way, for
  ten minutes, taken once (10 wrong tries per IP per 15 minutes). The relay never sees the code.
- **Merge** (`SyncMerge`, three ways per row against the base): whoever changed a row since the base wins; when both
  did, the latest change wins; a change beats a delete. Names that must be unique (two devices both made "Kelder")
  fold into one row and what pointed at the other follows; a row whose parent was deleted elsewhere brings it back.
  Only rows that differ are written, in one transaction. A device's own settings (language, Google, its relay link,
  its sync keys) never travel; a restore from a backup clears the base, so the next sync adds both together.
- **When:** about 15 s after a change, every 5 minutes while Hearth is open, when it comes back on screen, and with
  *Nu synchroniseren*. The always-on tablet is always current; a phone catches up when opened (no background jobs).
- **Joining a household** asks first: *Neem het huishouden over* (recommended; what is on this device goes) or
  *Voeg samen* (what is here is added).
- Google Agenda should stay on one device: each device would make its own "Hearth" calendar.

The two-stage router/helper code (`ai/llm`, `ai/route`) stays in the repo, tested but not wired in.

## 8. Build slices (revised order)

1. ✅ Shopping list from chat and dashboard; web version; Vercel preview.
2. ✅ Orchestrator + inbox; offline sorter; Ja/Nee confirmations.
3. ✅ Redesign + Backlog planning: navigation, plan sheet, Shopping screen, capture bar.
4. ✅ **Task types + dashboard**: routines (interval, "about time", never late), admin (urgent, deadline,
   waiting for a reply + follow-up), projects with steps; Taken screen; dashboard with Niet vergeten,
   Vandaag + ritme, Binnenkort, generated free time, Lopende projecten. Capture bar reads "om de 2 weken",
   "elke week", "dringend".
5. ✅ **Werkbord**: house rules, frictions (→ rule / change / let go), personal, couple and family goals
   with a next step; household members (names, colours). Capture bar: "huisregel: …", "ik erger me aan …",
   "we willen samen …", "doel: …", and spoken commands that name the place ("voeg aan de huisregels toe dat
   …", "add to goals: …", "zet … bij de projecten"): only the rest becomes the item (`AreaWords`).
6. ✅ **Weekoverleg**: guided weekly meeting with meal planner (dishes → shopping items);
   capture bar: "we eten maandag spaghetti", "vanavond eten we frietjes", "dinner on monday: pizza".
   v0.6: wat ging goed, Luister mee with a Voorstel, ideas on the Werkbord, summaries. v0.7: one prepared page,
   Vooruitblik, dates further ahead. v0.8: two household meters agreed together, house-rule check, habit health.
   v0.9: habits rated (Ease colours, small step) instead of ticked; tasks coloured by postponement.
   v0.10: the meeting by topic (tiles), a visible Luister mee that lights up the topic, Samen akkoord? before
   Klaar, Oudjes removed, phone layout fixes. v0.11: moments of the day (*Anchor*): Ritmes & routines laid out
   per moment (capture bar: "’s avonds de tassen klaarzetten", "na het eten afwassen", "voor het slapen
   voorlezen"), apart from Huishouden; Frictie & ideeën and Huisregels as separate tiles. v0.12: Dutch by
   default with a language setting (Instellingen → Taal); Koppelingen lives under Instellingen. v0.13: routines
   with steps (sequence or checklist), ticked per day in Taken and Vandaag, edited in the routine's sheet;
   capture bar "avondroutine: …", "paklijst opvang: …", "slaaproutine: badje → pyjama → verhaaltje".
   v0.14: a routine without a moment is a checklist (Lijstjes; no rhythm, used when needed); paste a list in the
   capture bar (heading → checklist / Boodschappen / Recept, otherwise line by line); a recipe from a link
   (capture bar or Recepten → "Plak een link naar een recept"; the family's AI can call import_recipe too).
   v0.19: the household on more devices (Instellingen → Samen op meer toestellen): share it on one device, join with a
   one-time code, and it syncs like git (pull, three-way merge, push) through the relay, sealed end to end (§7);
   DB v16 (`sync_base`).
   v0.18: Luister mee hears thoughts, not breaths: stretches join by how they sound, each thought gives its
   conclusions (what, who, which day; "deze week" goes to Volgende), Hearth waits until a thought settles, words
   can be fixed on the stage, in Gesprek and in the Voorstel (`fix_transcript_line`), the recognizer keeps the guess
   with the household's words, Flemish speech is read as plain Dutch, and the family's AI reads the conversation
   (`read_conversation`, `mark_transcript_sorted` over MCP).
   v0.17: "Admin" is Dutch too; a routine's steps show small and in order (a bar, the next step, Alle stappen
   numbered) with arrows in its sheet (`move_step`, `backlog_item.position`, DB v15); house rules show how they go
   on the Werkbord and Vandaag ("Hoe loopt het?"); a goal gets a place in the week as a ritme, vrije tijd or a step.
   v0.16: Terugblik rates every routine and house rule on one scale; frictions first get what they need
   (huisregel, nieuwe routine, routine aanpassen, taak met focus, loslaten), then are written in the family's own
   words (`mark_friction`, `board_item.direction`, DB v14).
   v0.15: one topic, one look (name, icon, colour) on every screen; Taken as a dashboard of topic panels;
   Doelen as a weekoverleg tile; forms ask before typed text goes (Verder typen / Weggooien); Dutch copy
   without English words (Paperassen, Taken, einddatum, koppelserver); user journeys in `docs/JOURNEYS.md`.
7. ✅ **MCP relay** (§7, option A): one shared relay; "Koppelingen" (rail bottom; on a phone via the link
   icon on Vandaag): add `<relay>/mcp` once in ChatGPT/Claude and type the tablet's pairing code.
8. ✅ Stock per room/topic with the restock loop; X-to-delete everywhere; clear bought; live voice with a
   confirm and NL/EN. (Still to do: counted stock "nog 3", a walk-through stock check.)
9. Per-person Today, helpers, seasons for routines, anchor habits, protected time as its own kind.
10. ✅ **Google** (opt-in, narrow scopes): daily backup to Drive's hidden app folder with restore (asks
    first); planned days in Hearth's own Google calendar (all-day, no reminders). Setup: `docs/GOOGLE.md`.
11. On-device model for offline sorting.

## 9. Decisions

1. **Name:** Hearth, package `app.hearth`.
2. **Language:** Flemish Dutch and English. UI follows the device language; the local parser understands both and replies in the language that was used.
3. **Target device:** Lenovo Tab M10 FHD Plus (TB-X606F): Helio P22T, 2–4 GB RAM, 1920×1200, Android 9/10. `minSdk 26`, `compileSdk/targetSdk 36`.
4. **Kiosk = "always-on wall display", not a locked-down device.**
   - Screen stays on while charging (`FLAG_KEEP_SCREEN_ON` only when plugged in).
   - Hearth shows **over the lock screen** (`showWhenLocked` + `turnScreenOn`), so nobody unlocks to use it.
   - After 2 minutes without touch it **dims** to a clock; the first touch only wakes it.
   - Hearth registers as a **home app**: choose it as the default home once, and it also comes back after a reboot.
   - **Theft:** set a normal PIN on the tablet. Hearth still runs over the lock screen, but leaving Hearth, opening settings or other apps needs the PIN. API keys stay encrypted in the Android Keystore, and Android's own cloud backup is off (`allowBackup=false`).
5. **Stock tracking per item:** either **state** (OK / Low / Out, the default) or **count** ("3 left", with a low mark). Both feed the same restock loop.

6. **Web version:** the app also runs in the browser from the same code (Kotlin/Wasm + Compose Multiplatform + Room 3 with a SQLite web worker). Data is stored per browser (OPFS). It is a preview and demo surface; the tablet stays the "server". Kiosk and the Android recognizer are Android-only; the web uses the browser's speech recognition where available. Built by `.github/workflows/web-preview.yml`, published to the `hearth-web` branch, deployed by Vercel's Git integration.

7. **No API usage.** AI is the family's own ChatGPT/Claude subscription via MCP (§7).
8. **Design:** 2026 look: Plus Jakarta Sans, warm canvas, white 28dp cards, one ember accent, calm section
   colours, rail navigation on the tablet and bottom bar on phones, capture bar under every screen.

## 10. Build notes

- Gradle 8.14.3, AGP 8.13, Kotlin 2.3, KSP 2.3, Room 3.0, androidx.sqlite 2.7, Compose Multiplatform 1.10.
- `./gradlew hearthCheck` runs everything (see CLAUDE.md).
- Chat history is kept in memory for now; the `ChatMessage` table arrives with the cloud slice.
