# Round 3: copy words it never saw. Real Dutch compounds from the OpenTaal word list (used only to make sentences)
# fill the groceries, dishes, appointments and things to fix, so the model learns to copy what was said, letter by
# letter, instead of a word it knows. Plus the kinds round 2 mixed up, and past events that stay talk.
# Usage: python gen3.py WORDLIST N OUT threads|windows SEED
import json, re, sys
import gen, gen2
from gen import R, pick, cap, call, f, NAMES, ACK, YES

TEST_WORDS = {w for l in open("test.jsonl") for line in json.loads(l)["lines"] for w in re.findall(r"[\w-]+", line.lower())}

def compounds(words, heads, n):
    out = [w for w in words if re.fullmatch(r"[a-zë-]{6,18}", w) and any(w.endswith(h) and len(w) > len(h) + 2 for h in heads) and w not in TEST_WORDS]
    R.shuffle(out); return out[:n]

def load(path):
    words = [w.strip() for w in open(path, encoding="utf-8")]
    words = [w for w in words if w.islower()]
    return {
        "grocery": compounds(words, ["kaas", "brood", "saus", "koek", "koekjes", "zeep", "papier", "middel", "olie", "sap", "thee", "worst", "poeder", "crème", "chips", "jam", "pasta", "vla", "yoghurt", "melk", "mosterd", "kruiden", "zakjes", "tabletten"], 1500),
        "dish": compounds(words, ["soep", "stoofpot", "schotel", "taart", "salade", "curry", "burger", "puree", "ragout", "lasagne", "risotto", "stamppot", "quiche", "omelet", "wok"], 900),
        "appt": compounds(words, ["arts", "dokter", "les", "training", "feest", "feestje", "controle", "afspraak", "avond", "vergadering", "keuring", "consult", "wedstrijd", "repetitie", "voorstelling", "examen"], 900),
        "thing": compounds(words, ["kast", "deur", "lamp", "raam", "fiets", "tafel", "stoel", "bed", "kraan", "leiding", "goot", "poort", "hek", "plank", "rek", "lade", "spiegel", "bank", "mand", "schakelaar"], 900),
    }

FIX = [("herstellen", "repareren"), ("nakijken", "laten nakijken"), ("vervangen", "vervangen"), ("opruimen", "opruimen"), ("schilderen", "schilderen"), ("kuisen", "kuisen"), ("ophangen", "ophangen"), ("monteren", "monteren")]
DAYS = ["maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag", "morgen", "volgende week", "dit weekend", "overmorgen", "volgende dinsdag", "zaterdagvoormiddag"]

def new_words(W):
    def shopping():
        items = R.sample(W["grocery"], R.randint(1, 3)); lines = [pick([f"zet {' en '.join(items)} op de lijst", f"we hebben geen {' en '.join(items)} meer", f"we moeten nog {' en '.join(items)} halen"])]
        if R.random() < 0.4:
            extra = pick(W["grocery"]); lines.append(pick([f"en {extra}", f"o en ook {extra}"])); items.append(extra)
        if R.random() < 0.3 and len(items) > 1:
            gone = pick(items); lines.append(pick([f"nee, {gone} is er nog", f"{gone} heb ik al gekocht", f"schrap {gone}"])); items.remove(gone)
        return lines, [call("add_shopping", items=", ".join(items))]
    def meal():
        d = pick(gen2.OTHER_DAYS); di = pick(W["dish"])
        if R.random() < 0.3:
            d2 = pick([x for x in gen2.OTHER_DAYS if x != d])
            return [f"{d} {di}?", pick([f"{d} ben ik er niet", f"nee, {d} eet ik niet thuis"]), f"doe het dan {d2}", pick(ACK)], [call("plan_meal", dish=cap(di), day=d2)]
        return [pick([f"{d} maak ik {di}", f"{d} eten we {di}", f"ik dacht {d} {di}", f"wat dacht je van {di} op {d}"]), pick(ACK + ["lekker"])], [call("plan_meal", dish=cap(di), day=d)]
    def appt():
        n = pick(NAMES); d = pick(DAYS); x = pick(W["appt"]); t = pick(gen.TIMES) if R.random() < 0.3 else None
        said = pick([f"{n} heeft {d} {x}", f"{d} is de {x} van {n}", f"de {x} van {n} is {d}", f"{d} hebben we de {x}"]) + (f" {t}" if t else "")
        out = f"{cap(x)} {n}" if f"van {n}" in said or said.startswith(n) else cap(x)
        if R.random() < 0.25:
            d2 = pick([y for y in DAYS if y != d]); return [said, pick(gen.CHANGE_DAY).replace("{d}", d2)], [call("add_appointment", text=out, day=d2, time=t)]
        return [said], [call("add_appointment", text=out, day=d, time=t)]
    def fix():
        thing = pick(W["thing"]); verb, said_verb = pick(FIX); d = pick(DAYS)
        lines = [pick([f"de {thing} moet nog {said_verb}", f"ik moet {d} de {thing} {verb}", f"kan jij {d} de {thing} {verb}"])]
        if lines[0].startswith("kan jij"):
            if R.random() < 0.3: return lines + [pick(gen.NO)], []
            lines.append(pick(YES))
        if lines[0].startswith("de "): lines.append(pick([f"ik doe dat {d}", f"{d} doe ik het"]))
        return lines, [call("add_task", text=cap(f"de {thing} {verb}"), day=d)]
    return [(shopping, 6), (meal, 5), (appt, 6), (fix, 5)]

def routine_evening():
    x = pick(["pizza-avond", "spelletjesavond", "filmavond", "pannenkoekendag", "frietjesavond", "soepdag", "wafelavond", "voorleesavond"]); w = pick(["iedere vrijdag", "elke zaterdag", "elke woensdag", "iedere zondag", "elke dinsdag"])
    return [pick([f"{w} {x}?", f"wat als we {w} {x} doen"]), pick(["ja leuk", "ja, doen we", "top, de kinderen gaan dat leuk vinden"])], [call("add_routine", text=cap(x), when=w)]

KIDS_RULES = [("de kinderen moeten hun bord zelf afruimen", "De kinderen ruimen hun bord zelf af", "ook bij oma", "De kinderen ruimen hun bord zelf af, ook bij oma"),
              ("de kinderen moeten eerst hun huiswerk maken", "Eerst huiswerk, dan spelen", "voor ze buiten gaan spelen", "Eerst huiswerk, dan buiten spelen"),
              ("iedereen moet zijn jas ophangen", "Iedereen hangt zijn jas op", "ook de grote mensen", "Iedereen hangt zijn jas op, ook de grote mensen"),
              ("the kids should make their beds every morning", "The kids make their beds every morning", "before breakfast", "The kids make their beds every morning before breakfast"),
              ("everyone should put their dishes in the dishwasher", "Everyone puts their dishes in the dishwasher", "straight after eating", "Everyone puts their dishes in the dishwasher straight after eating")]
def kids_rule():
    said, out, ext, out2 = pick(KIDS_RULES); en = said.startswith(("the", "everyone"))
    lines = [said]
    if R.random() < 0.5: lines.append(ext if not en else ext); out = out2
    lines.append(pick(["agreed", "deal"]) if en else pick(["akkoord", "afgesproken", "ja, goed"]))
    return lines, [call("add_house_rule", text=out)]

PAST = ["veel vergaderingen gehad deze week", "de tandarts viel goed mee", "het oudercontact was oké", "the meeting ran late again", "lots of calls at work",
        "de training van gisteren was zwaar", "het feestje van zaterdag was leuk", "ik had drie afspraken op een dag", "de dokter zei dat het niks ernstigs is",
        "the dentist was fine", "de les ging niet door vorige week", "we waren gisteren bij de kapper"]
def past_talk():
    return R.sample(PAST, R.randint(1, 2)) + ([pick(["mja", "ok", "goed zo", "nice", "ja"])] if R.random() < 0.6 else []), []

if __name__ == "__main__":
    W = load(sys.argv[1]); n = int(sys.argv[2]); out = sys.argv[3]; mode = sys.argv[4]; R.seed(int(sys.argv[5]))
    print({k: len(v) for k, v in W.items()}, {k: v[:4] for k, v in W.items()})
    gen.SCENES += new_words(W) + [(routine_evening, 2), (kids_rule, 2), (past_talk, 3)]
    rows = []
    for _ in range(n):
        if mode == "windows":
            lines, calls = gen.window()
            while len(lines) > 16: lines, calls = gen.window()
        else:
            lines, calls = gen.thread()
        rows.append({"date": gen.prompt_date(), "lines": [l.strip() for l in lines], "calls": calls})
    print("long lines also in the test set:", gen2.check_overlap(rows))
    with open(out, "w") as fo:
        for r in rows: fo.write(json.dumps(r, ensure_ascii=False) + "\n")
