# Hearth — design proposal (awaiting approval)

Status: **DRAFT — no code until approved.**

## 1. Tech stack

| Concern | Choice | Why |
|---|---|---|
| Language/UI | Kotlin, Jetpack Compose, Material 3 | Standard, tablet-friendly |
| Storage | Room (SQLite) + Flow | Local-first, reactive dashboard |
| DI | Manual `AppContainer` | No Hilt/kapt cost on build or low-spec runtime |
| Serialization / schemas | kotlinx.serialization + small schema DSL | One definition → JSON Schema for LLMs & MCP |
| HTTP (LLM, Google) | OkHttp | Small, already transitive in most stacks |
| Voice | Android `SpeechRecognizer` (on-device where available) + TextToSpeech | Free, offline-capable |
| Local model | llama.cpp (JNI) with ~0.5B 4-bit model (e.g. Qwen2.5-0.5B-Instruct Q4_K_M, ~400 MB), downloaded on demand | Fits 3 GB devices; optional |
| MCP server | Ktor CIO embedded server, streamable HTTP | Lightweight, LAN only |
| Google | Credential Manager sign-in + AuthorizationClient scopes; Calendar & Drive REST via OkHttp | No heavy Google API client libs |
| Min SDK | 26 (Android 8.0) | Covers old/cheap tablets |

## 2. Tool list

Every tool: `name`, `description`, typed `Args` (→ JSON Schema), typed `Result`
with a `summary`, and `destructive` flag. ⚠ = destructive (needs confirmation).

**Household & members**
- `get_household` — members, areas, owners
- `add_member` / `update_member` (name, color, avatar, style: `DOER|PLANNER`, prefers: `QUICK|PROJECT|MIXED`, strengths[])
- `remove_member` ⚠
- `add_area` / `update_area` (name, icon, ownerId)
- `remove_area` ⚠
- `set_area_owner`

**Tasks**
- `list_tasks` (filters: memberId, areaId, status, size, today)
- `get_today` (per-member Today view: tasks, rhythm step, anchors, protected time)
- `add_task` (title, areaId?, assigneeId?, size?, due?, recurrence?, notes?) — AI suggests area/assignee/size if omitted
- `update_task`
- `complete_task` / `reopen_task` / `skip_task_today`
- `delete_task` ⚠
- `break_down_task` (taskId, steps[]) — creates linked sub-tasks; the AI generates the steps, the tool just stores them
- `ask_for_help` (taskId, helperId, stepTitle) — linked sub-task owned by the area owner, assigned to helper
- `suggest_quick_task` (memberId?, minutes?) — "give me one quick task"
- `route_task` (taskId) — re-runs owner/size matching

**Groceries & meals**
- `add_grocery_item` (name, quantity?, note?, addedBy?) — de-dupes, merges quantities
- `list_grocery_items`
- `check_grocery_item` / `uncheck_grocery_item`
- `remove_grocery_item` ⚠ (single item, soft confirm)
- `clear_checked_groceries` ⚠
- `add_staple` / `remove_staple` ⚠ / `list_staples`
- `restock_staples` (staple ids or "all low") → grocery items
- `set_meal_plan` (weekStart, days[{day, meal, ingredients[]}])
- `get_meal_plan`
- `create_shopping_task_from_meal_plan` — adds missing ingredients + a linked "Do the shop" task

**Rhythm, anchors, protected time**
- `list_rhythm_cards` / `add_rhythm_card` (name, anchor: MORNING|AFTER_SCHOOL|EVENING|BEDTIME|custom, steps[]) / `update_rhythm_card` / `delete_rhythm_card` ⚠
- `check_rhythm_step` (resets daily)
- `add_anchor` ("after X, do Y", memberId?) / `update_anchor` / `fade_anchor` (FORMING → FADING → FORMED) / `delete_anchor` ⚠
- `log_anchor_done`
- `add_protected_time` (title, members[], recurrence, start, durationMin, kind: FUN|REST) / `update_protected_time` / `delete_protected_time` ⚠
- `get_progress` (period: TODAY|WEEK) — done counts per member/area, protected time honored

**Batching**
- `add_batch_list` (name, slot: recurrence + time, e.g. "Calls — Tue 10:00") / `delete_batch_list` ⚠
- `add_batch_item` / `complete_batch_item` / `list_batch_items`

**Friction log**
- `log_friction` (title, area?, note?)
- `add_friction_idea` (entryId, idea)
- `set_friction_status` (OPEN|TO_TRY|TO_BUY|SOLVED)
- `list_friction` / `delete_friction` ⚠

**Notes**
- `add_note` / `update_note` / `search_notes` / `delete_note` ⚠

**System (not exposed over MCP)**
- `confirm_pending_action` / `cancel_pending_action`
- `push_to_calendar` (itemId) / `set_calendar_sync` (on/off)
- `backup_now` / `restore_backup` ⚠ / `set_backup_enabled`

## 3. Data schema (Room)

All tables: `id TEXT PK (UUID)`, `createdAt`, `updatedAt` (epoch ms). Enums stored as TEXT.

| Entity | Key columns |
|---|---|
| **Household** | name, timezone, dayStartsAt (e.g. 04:00), anchors JSON (labels + typical times) |
| **Member** | householdId, name, color, avatar, style (DOER/PLANNER), prefersSize (QUICK/PROJECT/MIXED), strengths (JSON list), isKid |
| **Area** | householdId, name, icon, ownerId → Member |
| **Task** | householdId, areaId?, title, notes, ownerId? (from area), assigneeId?, size (QUICK/PROJECT), estimateMin?, parentId? → Task, isHelpRequest, status (OPEN/DONE/SKIPPED), dueDate?, anchor?, recurrence? (simple RRULE subset), completedAt?, completedById?, sortOrder, calendarEventId? |
| **GroceryItem** | householdId, name, normalizedName, quantity?, note?, checked, checkedAt?, addedById?, sourceStapleId?, sourceMealPlanId? |
| **Staple** | householdId, name, normalizedName, defaultQuantity?, category |
| **MealPlan** | householdId, weekStart (date), shoppingTaskId? |
| **MealPlanDay** | mealPlanId, day (0–6), meal, ingredients (JSON) |
| **RhythmCard** | householdId, name, anchor, memberIds (JSON), isActive, sortOrder |
| **RhythmStep** | rhythmCardId, title, sortOrder |
| **RhythmCheck** | rhythmStepId, date, checkedById — daily state without mutating steps |
| **Anchor** | householdId, memberId?, afterWhat, doWhat, phase (FORMING/FADING/FORMED), startedAt |
| **AnchorLog** | anchorId, date |
| **ProtectedTime** | householdId, title, kind (FUN/REST), memberIds (JSON), recurrence, startTime, durationMin, calendarEventId? |
| **BatchList** | householdId, name, slotRecurrence, slotTime, calendarEventId? |
| **BatchItem** | batchListId, title, note?, done, doneAt? |
| **FrictionEntry** | householdId, areaId?, title, note, status (OPEN/TO_TRY/TO_BUY/SOLVED), solvedAt? |
| **FrictionIdea** | frictionEntryId, text, tried |
| **Note** | householdId, title, body, tags (JSON), pinned |
| **ChatMessage** | role, text, toolCallsJson?, pendingActionId?, createdAt (short rolling history) |
| **PendingAction** | toolName, argsJson, summary, expiresAt, source (CHAT/MCP) |

Recurrence: completing a recurring task creates the next instance (keeps history = visible progress).

## 4. Dashboard layout (landscape tablet, ~1280×800)

```
┌──────────────────────────┬───────────────────────────────────────────────┐
│  🔥 Hearth   Tue 20 Oct  │  TODAY                                         │
│ ─────────────────────── │ ┌─────────┐┌─────────┐┌─────────┐               │
│  Chat (orchestrator)     │ │ Sam     ││ Alex    ││ Mia     │ per-member    │
│                          │ │ 3 open  ││ 1 open  ││ ✓ 2 done│ Today columns │
│  you: add milk           │ │ □ bins  ││ □ call  ││         │ (tap = done)  │
│  🔥 Added milk to the    │ └─────────┘└─────────┘└─────────┘               │
│     list. ✓              │ ┌───────────────────┐┌──────────────────────┐   │
│                          │ │ Evening rhythm  ◐ ││ Groceries (6)        │   │
│  [confirm card for ⚠]    │ │ ☑ dishes          ││ □ milk  □ eggs       │   │
│                          │ │ ☐ lunchboxes      ││ □ bread …  + Staples │   │
│                          │ └───────────────────┘└──────────────────────┘   │
│  quick chips:            │ ┌──────────┐┌──────────────┐┌───────────────┐  │
│  [What's open?]          │ │ Anchors  ││ Protected 🎉 ││ Done this week│  │
│  [One quick task]        │ │ after    ││ Fri movie    ││ ▇▇▇▅ 23 things│  │
│  [Add to groceries]      │ │ coffee → ││ night 19:30  ││               │  │
│ ┌──────────────────┐ 🎤 │ └──────────┘└──────────────┘└───────────────┘  │
│ │ Type or speak…   │    │  Batches: 📞 Calls (3) Tue 10:00               │
│ └──────────────────┘    │ ─ nav: Today · Tasks · Food · Rhythm · Friction · Notes · ⚙ │
└──────────────────────────┴───────────────────────────────────────────────┘
```

- Left ~35%: chat, always visible, big mic button, quick-action chips.
- Right ~65%: "Today" dashboard; cards are tappable and call the same tools.
- The rhythm card shown is the one for the current time of day (morning → evening).
- Bottom nav opens full screens; chat stays docked. Portrait: chat collapses to a bottom sheet.
- Ambient/night mode: dims after inactivity, shows clock + next protected time.

## 5. Folder structure (Gradle modules)

```
hearth/
├── CLAUDE.md
├── docs/PROPOSAL.md
├── settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
├── app/                         # Activity, kiosk, boot receiver, navigation, AppContainer
│   └── src/main/java/app/hearth/
│       ├── MainActivity.kt
│       ├── kiosk/               # BootReceiver, keep-awake, lock-task
│       ├── di/AppContainer.kt
│       └── ui/
│           ├── chat/            # ChatPanel, ChatViewModel, ConfirmCard
│           ├── dashboard/       # TodayScreen + cards (Groceries, Rhythm, …)
│           ├── tasks/ food/ rhythm/ friction/ notes/ settings/
│           └── theme/
├── core/model/                  # pure Kotlin data classes & enums (no Android)
├── core/data/                   # Room entities, DAOs, HearthDatabase, repositories
├── core/tools/                  # Tool interface, schema DSL, ToolRegistry, all tools
│   └── tools/{household,tasks,groceries,rhythm,batch,friction,notes,system}/
├── ai/                          # Orchestrator, LlmProvider + Claude/OpenAI/Grok,
│                                # LocalIntentParser (tier 1), LocalModel (tier 2, JNI)
├── mcp/                         # Ktor MCP server adapting ToolRegistry
└── integrations/google/         # sign-in, Calendar push, Drive appData backup
```

Dependency direction: `app → ai, mcp, integrations → core:tools → core:data → core:model`.

## 6. Orchestrator flow

1. User text/voice → `Orchestrator.handle(text)`.
2. Tier 1 `LocalIntentParser` (regex/grammar, multilingual-ready) → tool call if confident.
3. Else tier 2 local model (if installed) with a *small* tool subset → tool call if valid JSON.
4. Else tier 3 cloud provider with full tool schemas; loop tool calls until a final reply.
5. Any `destructive` call → `PendingAction` + confirm card; executes only on confirm.
6. Offline + complex → "I can't do that offline yet — I've saved it as a note/task to revisit."

## 7. Build slices

1. **Slice 1 (first):** project skeleton, `core:model/data/tools`, `add_grocery_item` + `list_grocery_items` + `check_grocery_item`, tier-1 parser ("add milk", "we need eggs and bread"), chat panel + dashboard grocery card updating live. Tests for tool + parser.
2. Cloud providers (Claude/OpenAI/Grok) + API-key settings + tool-calling loop + confirm flow.
3. Household setup (members, areas, owners) + tasks + per-member Today + routing.
4. Staples, meal plan → shopping task.
5. Rhythm cards, anchors, protected time, progress.
6. Batching, friction log, notes.
7. Kiosk mode hardening, voice polish.
8. MCP server.
9. Local quantized model.
10. Google Calendar push + Drive backup/restore.

## 8. Open questions

1. App name **Hearth** OK? (package `app.hearth`)
2. Language(s) for UI and the local parser — English only, or also Dutch?
3. Min SDK 26 OK, or do you have a specific tablet in mind?
4. Kiosk: is "set as default launcher + immersive" enough, or do you want full device-owner lock-task?
