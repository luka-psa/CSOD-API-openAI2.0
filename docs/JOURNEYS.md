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
| Admin & afspraken | sheet of paper | purple | Taken panel | Niet vergeten, Vandaag, Binnenkort | yes |
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
| pencil | change a suggestion (name, place, day) |
| sun · cloud · bolt | how something goes: loopt vanzelf · vraagt moeite · te zwaar (never red) |
| circling arrows | Samen op meer toestellen (sync) |
| light bulb | an idea |

## 2. Screens

- **Vandaag**: today, readable from across the room. Time views plus a card for the topics that matter today.
- **Taken**: a dashboard of the five work topics as panels (Admin & afspraken, Grote taken, Huishouden,
  Ritmes & routines, Vrije tijd), each with its glance, its own grouping and an add field. A panel folds with
  a tap on its title; on a phone they start folded, so the first look is the overview. Te sorteren on top.
- **Weekmenu**: the week's dinners and the Recepten (typed, spoken, pasted or from a link).
- **Weekoverleg**: Luister mee, the ten topic tiles (tap = that topic's list), Terugblik, Deze week, Vooruitblik.
- **Werkbord**: who lives here, then Huisregels, Frictie & ideeën and Doelen.
- **Boodschappen**: the list per shop and the Voorraad per spot.
- **Instellingen**: Taal, Samen op meer toestellen, ChatGPT/Claude, Google, Voorbeelden.
- The **capture bar** sits under every screen.

## 3. Journeys

Each journey: who, the trigger, the steps, and what "done" looks like.

**J1. "Ik wil iets kwijt" (capture).** Anyone, any screen. Say or type it in the capture bar ("tandarts
morgen", "melk", a pasted list, a recipe link). Hearth puts it in its topic and says where, in the topic's
name ("Bij Taken: … (admin) · morgen"). Unclear? It waits in Te sorteren on top of Taken; one tap
places it. Done: it's visible where the family will look, nothing was lost.

**J2. "Wat moet er vandaag?"** A glance at the tablet in the morning. Vandaag: Niet vergeten, then today
(vanavond eten, vrije tijd, In het ritme per moment, Volgende), Binnenkort, and the topic cards. Tick what's
done; the counter only goes up. Done: everyone knows today without asking.

**J3. "Een routine leven."** Morning or evening. Vandaag shows each routine at its moment, small: a bar with one
segment per step, the next step to tick and "daarna: …" (Alle stappen unfolds them numbered); tick them as the
evening goes, all ticked is done for today, it comes back tomorrow. The order is set with arrows in the routine's
sheet. A checklist without a moment (Tas voor de opvang) is in Lijstjes, used when needed. Set up or
change: Taken → Ritmes & routines, or say "avondroutine: a, b en c". What happens on a day gets fixed days in the
same sheet (the weekday chips, a time only when it is fixed) or by saying it: "vuilbak buiten elke donderdag",
"zwemles elke woensdag om 14 uur", "de eerste zaterdag van de maand containerpark". The sheet stays open while you
change its moment, rhythm and days. With Google Agenda on, those repeat in the calendar "Hearth · Ritmes". Habits
are rated together in the weekoverleg, never ticked.

**J4. "Er is iets op."** Tap it in Voorraad (or say "melk"): it's on the list, per shop. In the shop: tick.
Bought = back in stock. The weekmenu adds ingredients by itself.

**J5. "Wat eten we?"** Weekmenu: tap a day, pick a recipe or type a dish; its ingredients go on the list.
Save a recipe by telling what you made, pasting a list, or pasting a link. A half-typed form never
disappears: a tap outside, back or a swipe asks first (Verder typen / Weggooien).

**J6. "Admin regelen."** Say it or add it in the Admin & afspraken panel. Dringend, a day to finish
by, or "wacht op antwoord" with a day to follow up. It comes to Niet vergeten on Vandaag when it matters.
Done: ticked, counted as done today.

**J7. "Een grote taak."** Add it in Grote taken, add its steps, plan steps on a day. Vandaag shows its
progress and next step. Pushed back often? It turns amber, then orange: make the first step smaller.

**J8. "Het weekoverleg."** Once a week, about 15 minutes, together. Vandaag suggests it near the weekend.
Luister mee on top: talk as you talk, pauses and all. Hearth keeps a thought together ("Mia gaat donderdag naar de
opvang … dus die moet gebracht worden … voor de rest ben ik vrij"), waits until it has settled, then shows what it
concludes ("Naar de opvang · donderdag") and lights up that tile. Not quite right? Tap the conclusion: its name,
place (taak, afspraak, ritme, …) and day open, filled in from what was said; *Bewaar* or *Zet erin*. *Zet op zijn
plek* shows every conclusion by topic, ticked; the family's own ChatGPT or Claude can also read the conversation and
put it in place. The ten tiles are the same topics as everywhere, tap one for its list. Terugblik: wat ging goed,
then *Hoe liep het?*: the week, every routine and every house rule on one weather scale (bliksem, wolk, zon), with a
smaller first step right there. Then Deze week and Vooruitblik. Klaar first shows "Samen akkoord?"; agreeing keeps the summary. Done: everyone knows the week.

**J9. "Iets schuurt."** Note it on the Werkbord or say "frictie: …". In the weekoverleg (Frictie & ideeën)
choose together what it needs, in one tap: a huisregel, a nieuwe routine, a routine aanpassen, a taak met focus,
or loslaten. It then shows "Huisregel te maken" in the colour of where it goes. Someone writes it in their own
words, right away or later ("De vaatwasser is nooit leeg" becomes "Wie het laatst eet, zet hem aan"). Every
outcome is good. Huisregels show on Vandaag and get a calm check each week in the Terugblik (never who).

**J10. "Een doel."** Add it on the Werkbord (gezin, samen or persoonlijk). Give it a place in the week (on the
Werkbord or in the weekoverleg's Doelen): a *ritme* ("10 minuten yoga", ochtend, elke dag) that lands in Ritmes &
routines, *vrije tijd* on a day ("gitaar spelen", zaterdag) that lands in Vrije tijd, or one small step (optionally
Volgende on Taken). The goal shows that link in its colour. Reached: tick it, it moves to "Samen bereikt".

**J11. "Instellen."** Instellingen: Taal first (Nederlands (Vlaams) by default, English on request), then Samen op meer
toestellen, ChatGPT/Claude (koppelcode), Google back-up and agenda, and Voorbeelden to see how it works.

**J12. "Op meer toestellen."** The tablet, both phones, an iPad in the browser: one household. On the tablet,
Instellingen → Samen op meer toestellen → *Deel dit huishouden*; it shows a code ("K7QM-3XPD"). On the phone, *Doe mee
met een code*, type it, and choose: *Neem het huishouden over* (recommended) or *Voeg samen*. From then on it syncs by
itself: shortly after a change, every few minutes while Hearth is open, when it comes back on screen, and with *Nu
synchroniseren* (or say "synchroniseer"). Edits from both meet; when two people change the same thing, the latest
change wins, and nothing anyone added is lost. *Toestel toevoegen* gives a new code for each next device. Done: the
milk added on a phone is on the tablet's list a moment later.

## 4. Checklist for new work

- It belongs to a topic: use `Topic.title` / `icon()` / `style()`; it shows in the topic's home, its tile,
  and on Vandaag only when it matters today.
- No new colour or icon for an existing meaning; no icon reused for a different one (table above).
- A form with typed text uses `FormSheet` or `DiscardDialog`: nothing typed disappears without asking.
- Dutch copy is Dutch: Taken (not backlog), einddatum (not deadline), koppelserver
  (not relay), verbonden (not online). Brand and menu names (ChatGPT, Claude, Google Drive) stay as they are.
