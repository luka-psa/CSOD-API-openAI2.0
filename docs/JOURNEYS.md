# Hearth: user journeys and how the screens fit together

The family should never have to wonder "where was that again?". This page is the map: which topics
exist, where each one lives, and the journeys people take through the app. Keep it in sync with the UI
(see also `docs/PROPOSAL.md` and the terminology in `CLAUDE.md`).

## 1. One household, ten topics

Everything in Hearth belongs to one **topic** (`Topic` in `core/model/Week.kt`). A topic has one name,
one icon and one colour, the same on every screen (`Topic.title`, `Topic.icon()` in `ui/Topics.kt`,
`Topic.style()` in `ui/theme/SectionStyle.kt`).

| Topic | Icon | Colour | Its home (where you manage it) | On Vandaag | Weekoverleg tile |
|---|---|---|---|---|---|
| Vrije tijd | sofa | pink | Taken panel | the Vrije tijd note, "vrij" days in Binnenkort | yes |
| Doelen | star | olive | Werkbord column | — | yes (next small step) |
| Paperassen & afspraken | sheet of paper | purple | Taken panel | Niet vergeten, Vandaag, Binnenkort | yes |
| Grote taken | wrench | orange | Taken panel | Grote taken card | yes |
| Huishouden | house | teal | Taken panel | In het ritme (aan de beurt) | yes |
| Ritmes & routines | repeat arrows | amber | Taken panel (per moment of the day) | In het ritme (per moment, with steps) | yes |
| Boodschappen | cart | green | Boodschappen screen (list + Voorraad) | Boodschappen card | yes |
| Weekmenu | fork and knife | blue | Weekmenu screen (week + Recepten) | Vanavond | yes |
| Frictie & ideeën | bolt | brick | Werkbord column | "n te bespreken" on the Huisregels card | yes |
| Huisregels | clipboard | slate | Werkbord column | Huisregels card | yes |

**The rule:** every topic has exactly one home, shows as a tile in the weekoverleg, and shows on Vandaag
only when it matters today. Something that fits no topic yet waits in **Te sorteren** on top of Taken.

Cards that are about *time*, not a topic (Vandaag, Niet vergeten, Binnenkort, Deze week, Vooruitblik,
Terugblik, Wat ging goed) and Hearth's own voice (Luister mee, calm notes, the chat) use the **ember**
accent. Ember is never a topic colour.

### One icon, one meaning

| Icon | Means |
|---|---|
| sun | Vandaag (and the "hoe liep het" scale) |
| flag | needs attention by a time: Niet vergeten, "klaar tegen", Dringend |
| calendar | days: Binnenkort, Deze week, plan a day, Google Agenda |
| clock | later: Vooruitblik, Later, wacht op antwoord, minder vaak |
| heart | what went well, Samen |
| check list | the Taken screen |
| people | the Weekoverleg |
| grid | the Werkbord |
| gear | Instellingen |
| link | koppelingen (ChatGPT/Claude) |
| flame | Hearth itself |
| light bulb | an idea |

## 2. Screens

- **Vandaag**: today, readable from across the room. Time views plus a card for the topics that matter today.
- **Taken**: a dashboard of the five work topics as panels (Paperassen & afspraken, Grote taken, Huishouden,
  Ritmes & routines, Vrije tijd), each with its glance, its own grouping and an add field. A panel folds with
  a tap on its title; on a phone they start folded, so the first look is the overview. Te sorteren on top.
- **Weekmenu**: the week's dinners and the Recepten (typed, spoken, pasted or from a link).
- **Weekoverleg**: Luister mee, the ten topic tiles (tap = that topic's list), Terugblik, Deze week, Vooruitblik.
- **Werkbord**: who lives here, then Huisregels, Frictie & ideeën and Doelen.
- **Boodschappen**: the list per shop and the Voorraad per spot.
- **Instellingen**: Taal, ChatGPT/Claude, Google, Voorbeelden.
- The **capture bar** sits under every screen.

## 3. Journeys

Each journey: who, the trigger, the steps, and what "done" looks like.

**J1. "Ik wil iets kwijt" (capture).** Anyone, any screen. Say or type it in the capture bar ("tandarts
morgen", "melk", a pasted list, a recipe link). Hearth puts it in its topic and says where, in the topic's
name ("Bij Taken: … (paperassen) · morgen"). Unclear? It waits in Te sorteren on top of Taken; one tap
places it. Done: it's visible where the family will look, nothing was lost.

**J2. "Wat moet er vandaag?"** A glance at the tablet in the morning. Vandaag: Niet vergeten, then today
(vanavond eten, vrije tijd, In het ritme per moment, Volgende), Binnenkort, and the topic cards. Tick what's
done; the counter only goes up. Done: everyone knows today without asking.

**J3. "Een routine leven."** Morning or evening. Vandaag shows each routine at its moment with its steps
(boodschappen → Mia ophalen → koken → pyjama); tick the steps, all ticked is done for today, it comes back
tomorrow. A checklist without a moment (Tas voor de opvang) is in Lijstjes, used when needed. Set up or
change: Taken → Ritmes & routines, or say "avondroutine: a, b en c". Habits are rated together in the
weekoverleg, never ticked.

**J4. "Er is iets op."** Tap it in Voorraad (or say "melk"): it's on the list, per shop. In the shop: tick.
Bought = back in stock. The weekmenu adds ingredients by itself.

**J5. "Wat eten we?"** Weekmenu: tap a day, pick a recipe or type a dish; its ingredients go on the list.
Save a recipe by telling what you made, pasting a list, or pasting a link. A half-typed form never
disappears: a tap outside, back or a swipe asks first (Verder typen / Weggooien).

**J6. "Paperassen regelen."** Say it or add it in the Paperassen & afspraken panel. Dringend, a day to finish
by, or "wacht op antwoord" with a day to follow up. It comes to Niet vergeten on Vandaag when it matters.
Done: ticked, counted as done today.

**J7. "Een grote taak."** Add it in Grote taken, add its steps, plan steps on a day. Vandaag shows its
progress and next step. Pushed back often? It turns amber, then orange: make the first step smaller.

**J8. "Het weekoverleg."** Once a week, about 15 minutes, together. Vandaag suggests it near the weekend.
Luister mee on top (each sentence lands on a tile, which lights up); the ten tiles are the same topics as
everywhere, tap one for its list. Terugblik (wat ging goed, the two meters, the huisregels), Deze week,
Vooruitblik. Klaar first shows "Samen akkoord?"; agreeing keeps the summary. Done: everyone knows the week.

**J9. "Iets schuurt."** Note it on the Werkbord or say "frictie: …". In the weekoverleg (Frictie & ideeën)
decide together: wordt huisregel, veranderen (it becomes a task) or loslaten; all three are good outcomes.
Huisregels show on Vandaag and get a calm check each week (liep goed / liep nog niet, never who).

**J10. "Een doel."** Add it on the Werkbord (gezin, samen or persoonlijk). In the weekoverleg (Doelen) pick
one small next step, optionally as Volgende on Taken. Reached: tick it, it moves to "Samen bereikt".

**J11. "Instellen."** Instellingen: Taal first (Nederlands (Vlaams) by default, English on request), then
ChatGPT/Claude (koppelcode), Google back-up and agenda, and Voorbeelden to see how it works.

## 4. Checklist for new work

- It belongs to a topic: use `Topic.title` / `icon()` / `style()`; it shows in the topic's home, its tile,
  and on Vandaag only when it matters today.
- No new colour or icon for an existing meaning; no icon reused for a different one (table above).
- A form with typed text uses `FormSheet` or `DiscardDialog`: nothing typed disappears without asking.
- Dutch copy is Dutch: Taken (not backlog), paperassen (not admin), einddatum (not deadline), koppelserver
  (not relay), verbonden (not online). Brand and menu names (ChatGPT, Claude, Google Drive) stay as they are.
