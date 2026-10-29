# Real meetings as training talk. A conversation exported from the tablet (Gesprek → Exporteren, format
# hearth-conversation/2) after the family checked the Voorstel holds every line as Hearth read it (the household's
# own words put right), the topics as the conversation went and the Voorstel as the family left it, with the lines
# each row rests on. This turns it into examples in the training format: per topic, windows of lines the way the app
# hands them to the model (MeetingProcessor: 10 lines, 2 overlapping), and as answer what the family kept (ticked or
# added; a change to something that exists counts as what was said). Rows the family unticked are left out of the
# answer: the model learns that talk was not an action. Messy recognizer text stays as it is: that is the point.
# Usage: python from_export.py OUT.jsonl EXPORT.json [EXPORT.json ...]
import datetime, json, sys

WINDOW, OVERLAP = 10, 2
DAYS = ["maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag"]
MONTHS = ["januari", "februari", "maart", "april", "mei", "juni", "juli", "augustus", "september", "oktober", "november", "december"]


def spoken(d):
    return f"{DAYS[d.weekday()]} {d.day} {MONTHS[d.month - 1]}"


def day_word(date, today):
    if not date: return None
    d = datetime.date.fromisoformat(date)
    diff = (d - today).days
    if diff == 0: return "vandaag"
    if diff == 1: return "morgen"
    if 0 < diff < 7: return DAYS[d.weekday()]
    return f"{d.day} {MONTHS[d.month - 1]}"


def action(row, today):
    """The interpreter's action for a Voorstel row as the family left it (None: nothing to learn from it)."""
    text, place, day = row["text"], row["place"], day_word(row.get("date"), today)
    def with_day(name, **args):
        if day: args["day"] = day
        return [name, args]
    return {
        "PLANNING": lambda: with_day("add_task", text=text),
        "EVENT": lambda: with_day("add_appointment", text=text),
        "FREE_TIME": lambda: with_day("add_free_time", text=text),
        "RHYTHM": lambda: ["add_routine", {"text": text}],
        "SHOPPING": lambda: ["add_shopping", {"items": ", ".join(t.strip().lower() for t in text.split(","))}],
        "MEAL": lambda: with_day("plan_meal", dish=text),
        "HOUSE_RULE": lambda: ["add_house_rule", {"text": text}],
        "FRICTION": lambda: ["log_friction", {"text": text}],
        "IDEA": lambda: ["add_idea", {"text": text}],
        "GOAL": lambda: ["add_goal", {"text": text}],
        "WIN": lambda: ["add_win", {"text": text}],
    }.get(place, lambda: None)()


def windows(lines):
    if len(lines) <= WINDOW: return [lines]
    out, start = [], 0
    while start < len(lines):
        out.append(lines[start:start + WINDOW])
        if start + WINDOW >= len(lines): break
        start += WINDOW - OVERLAP
    return out


def examples(doc):
    if doc.get("format") != "hearth-conversation/2" or "voorstel" not in doc:
        raise SystemExit("Needs a hearth-conversation/2 export made while the Voorstel was open.")
    today = datetime.date.fromisoformat(doc["context"]["today"])
    text = [l.get("read", l["text"]) for l in doc["lines"]]
    kept = [(r, set(r.get("lines", []))) for r in doc["voorstel"]
            if (r["checked"] or r["added"]) and r.get("lines") and not (r.get("existing") and not r["existing"]["update"])]
    out, used = [], set()
    for part in doc["topics"]:
        for window in windows(part["lines"]):
            inside = set(window)
            # Each row is taught once: in the first window that holds the line it was last said in.
            calls = []
            for n, (row, lines) in enumerate(kept):
                if n not in used and max(lines) in inside:
                    a = action(row, today)
                    if a: calls.append(a)
                    used.add(n)
            out.append({"date": spoken(today), "lines": [text[i] for i in window], "calls": calls})
    return out


if __name__ == "__main__":
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        for path in sys.argv[2:]:
            for ex in examples(json.load(open(path, encoding="utf-8"))):
                f.write(json.dumps(ex, ensure_ascii=False) + "\n")
