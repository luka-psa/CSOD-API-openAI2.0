# Synthetic weekoverleg threads for Hearth's interpreter: one topic thread in, the final actions out.
import random, json

R = random.Random(7)
def pick(xs): return R.choice(xs)
def cap(s): return s[:1].upper() + s[1:]

NAMES = ["Mia", "Tom", "Lien", "Sam", "Noor", "Jules"]
DAYS = ["maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag", "morgen", "overmorgen", "vandaag", "dit weekend", "volgende week", "deze week", "zaterdagvoormiddag", "zondagnamiddag", "vrijdagavond"]
DAYS_EN = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "tomorrow", "this weekend", "next week", "tonight"]
FILL = ["", "", "", "euh ", "allee ", "ja ", "oké ", "goh ", "zo ", "nu ja "]
ACK = ["ja", "oké", "mhm", "ja ja", "juist", "inderdaad", "klopt", "ja da's waar", "ok"]
ACK_EN = ["yeah", "okay", "right", "mhm", "true"]
YES = ["ja dat kan ik doen", "oké, doe ik", "is goed", "ja hoor", "da's goed voor mij", "ja, dat lukt", "zeker", "geen probleem, doe ik", "ja, ik neem dat op mij", "afgesproken"]
YES_EN = ["sure, I can do that", "yes, I'll do it", "fine by me", "okay, deal", "no problem"]
NO = ["nee, dat lukt mij niet", "nee sorry, dan kan ik niet", "liever niet eigenlijk", "dat zie ik niet zitten", "nee, ik werk dan tot laat"]
NO_EN = ["no, I can't then", "I'd rather not", "that won't work for me"]
CHAT = ["hoe was jouw week eigenlijk", "druk op het werk deze week", "ik was echt moe donderdag", "het regende de hele tijd", "de kinderen waren wat ziek", "ik heb slecht geslapen", "gewoon, de gewone dingen", "mijn baas zat me weer op de nek", "het was allemaal wat veel", "we hebben veel tv gekeken"]
CHAT_EN = ["how was your week", "work was crazy", "I was so tired on Thursday", "the kids were a bit sick"]

# (NL infinitive phrase, EN imperative phrase)
TASKS = [
    ("de verzekering bellen", "call the insurance"), ("de dakgoot kuisen", "clean the gutters"), ("het gras afrijden", "mow the lawn"),
    ("de belastingen indienen", "file the taxes"), ("de fiets van {n} herstellen", "fix {n}'s bike"), ("de kast in elkaar zetten", "assemble the wardrobe"),
    ("een babysit regelen", "find a babysitter"), ("de school terugbellen", "call the school back"), ("het formulier van de opvang invullen", "fill in the daycare form"),
    ("de vakantie boeken", "book the holiday"), ("de zolder opruimen", "clear out the attic"), ("de garantie van de wasmachine opzoeken", "look up the washing machine warranty"),
    ("de auto naar de garage brengen", "take the car to the garage"), ("de rekeningen betalen", "pay the bills"), ("de lampen in de gang vervangen", "replace the hallway lights"),
    ("de verwarming laten nakijken", "get the heating checked"), ("de kleren van {n} sorteren", "sort out {n}'s clothes"), ("een cadeau zoeken voor {n}", "find a present for {n}"),
    ("de tandarts bellen voor een afspraak", "call the dentist for an appointment"), ("de ramen wassen", "wash the windows"), ("de garage opruimen", "tidy the garage"),
    ("het abonnement van de krant opzeggen", "cancel the newspaper subscription"), ("de foto's van de vakantie afdrukken", "print the holiday photos"),
    ("de lekkende kraan maken", "fix the leaking tap"), ("de uitnodigingen voor het feest versturen", "send the party invitations"), ("de bank bellen over de lening", "call the bank about the loan"),
    ("de schoenen van {n} laten maken", "get {n}'s shoes repaired"), ("de vriezer ontdooien", "defrost the freezer"), ("het rapport van {n} tekenen", "sign {n}'s school report"),
    ("de hond naar de dierenarts brengen", "take the dog to the vet"), ("de containerpark-rit doen", "do the recycling centre run"), ("de tuin winterklaar maken", "get the garden ready for winter"),
]
# (NL said, output NL, EN said, output EN)  {n} name, {t} time
APPTS = [
    ("{n} heeft {d} tandarts", "Tandarts {n}", "{n} has the dentist {d}", "Dentist {n}"),
    ("{d} moet {n} naar de dokter", "Dokter {n}", "{n} has to see the doctor {d}", "Doctor {n}"),
    ("{d} is het oudercontact", "Oudercontact", "the parent evening is {d}", "Parent evening"),
    ("{n} heeft {d} zwemles", "Zwemles {n}", "{n} has swimming lessons {d}", "Swimming lesson {n}"),
    ("ik heb {d} een afspraak bij de kapper", "Kapper", "I have a haircut {d}", "Haircut"),
    ("{d} komen mijn ouders eten", "Mijn ouders komen eten", "my parents come for dinner {d}", "My parents come for dinner"),
    ("{d} gaan we naar het feest van {n}", "Feest van {n}", "we're going to {n}'s party {d}", "{n}'s party"),
    ("{d} heeft {n} voetbal", "Voetbal {n}", "{n} has football {d}", "Football {n}"),
    ("ik ben er {d} niet, ik heb een teambuilding", "Teambuilding", "I'm away {d}, team building", "Team building"),
    ("{n} gaat {d} naar de opvang", "{n} naar de opvang", "{n} goes to daycare {d}", "{n} to daycare"),
    ("{d} gaan we op bezoek bij oma", "Op bezoek bij oma", "we're visiting grandma {d}", "Visit grandma"),
    ("{n} is {d} jarig", "Verjaardag {n}", "it's {n}'s birthday {d}", "{n}'s birthday"),
    ("{d} is de keuring van de auto", "Keuring van de auto", "the car inspection is {d}", "Car inspection"),
    ("{n} heeft {d} muziekschool", "Muziekschool {n}", "{n} has music school {d}", "Music school {n}"),
]
TIMES = ["om negen uur", "om half vier", "om tien uur", "om twee uur", "om acht uur 's avonds", "om kwart over vijf"]
FREE = [("niks plannen", "nothing planned"), ("uitslapen", "sleep in"), ("een filmavond met de kinderen", "a movie night with the kids"),
        ("een avondje met z'n twee", "a date night"), ("een spelletjesavond", "a games night"), ("naar de zee", "a trip to the seaside"),
        ("een fietstocht", "a bike ride"), ("gewoon thuis rusten", "just rest at home"), ("een middag voor mezelf", "an afternoon to myself"), ("gaan zwemmen met de kinderen", "go swimming with the kids")]
# (NL item, NL when, EN item, EN when)
ROUTINES = [("de tassen klaarzetten", "elke avond", "pack the bags", "every evening"), ("de lakens verversen", "om de twee weken", "change the sheets", "every two weeks"),
            ("samen afruimen", "na het eten", "clear the table together", "after dinner"), ("de vuilbak buitenzetten", "elke dinsdag", "put the bins out", "every Tuesday"),
            ("de planten water geven", "elke zondag", "water the plants", "every Sunday"), ("een verhaaltje voorlezen", "voor het slapengaan", "read a bedtime story", "at bedtime"),
            ("de vaatwasser uitladen", "elke ochtend", "empty the dishwasher", "every morning"), ("de badkamer poetsen", "elke zaterdag", "clean the bathroom", "every Saturday"),
            ("naar het containerpark", "de eerste zaterdag van de maand", "go to the recycling centre", "the first Saturday of the month"), ("huiswerk maken", "na school", "do homework", "after school"),
            ("tien minuten opruimen", "elke avond", "a ten-minute tidy", "every evening"), ("de brooddozen maken", "'s avonds", "make the lunchboxes", "in the evening")]
GROCERIES = ["melk", "eieren", "brood", "koffie", "wc-papier", "luiers", "batterijen", "wasmiddel", "kaas", "appels", "bananen", "yoghurt", "afwasblokjes", "boter", "pasta", "rijst", "tomaten", "choco", "fruitsap", "vuilniszakken"]
GROCERIES_EN = ["milk", "eggs", "bread", "coffee", "toilet paper", "nappies", "batteries", "laundry detergent", "cheese", "apples", "bananas", "yoghurt"]
DISHES = ["lasagne", "spaghetti", "curry", "soep met balletjes", "stoofvlees met frietjes", "vol-au-vent", "pizza", "wraps", "vispannetje", "pasta pesto", "witloof met kaas", "kip met appelmoes", "tacos", "risotto", "gourmet", "pannenkoeken", "stoemp met worsten", "chili con carne"]
DISHES_EN = ["lasagne", "curry", "pizza", "tacos", "fish pie", "pasta", "soup"]
# Rules: topic friction (said, out), options (dat-clause forms), final (dat-clause, infinitive out), refinement (said, new infinitive out)
RULES = [
    dict(frd="de keuken 's avonds altijd vol staat", fr=("de keuken staat 's avonds altijd vol", "De keuken staat 's avonds altijd vol"),
         opts=["wie kookt niet afwast", "we de vaatwasser direct na het eten inladen", "we om beurten de keuken doen"],
         final=("we elke avond voor het slapengaan tien minuten samen de keuken opruimen", "elke avond voor het slapengaan tien minuten samen de keuken opruimen"),
         ref=("maak er maar vijftien minuten van", "elke avond voor het slapengaan vijftien minuten samen de keuken opruimen")),
    dict(frd="de schoenen overal in de gang liggen", fr=("de schoenen liggen overal in de gang", "De schoenen liggen overal in de gang"),
         opts=["iedereen zijn schoenen in de kast zet", "we een schoenenrek bij de deur zetten", "de kinderen hun schoenen zelf opruimen"],
         final=("iedereen zijn schoenen meteen in de kast zet", "schoenen meteen in de kast zetten"),
         ref=("en jassen aan de kapstok ook", "schoenen meteen in de kast zetten en jassen aan de kapstok hangen")),
    dict(frd="we aan tafel altijd op onze gsm zitten", fr=("we zitten aan tafel altijd op onze gsm", "We zitten aan tafel altijd op onze gsm"),
         opts=["er geen gsm aan tafel komt", "we de gsm's in een mandje leggen", "alleen in het weekend geen gsm"],
         final=("er geen gsm's aan tafel komen", "geen gsm's aan tafel"),
         ref=("behalve als iemand moet bellen voor het werk", "geen gsm's aan tafel, behalve voor dringend werk")),
    dict(frd="de kinderen veel te laat gaan slapen", fr=("de kinderen gaan veel te laat slapen", "De kinderen gaan veel te laat slapen"),
         opts=["ze om acht uur in bed liggen", "we om half acht met de avondroutine beginnen", "er na zeven uur geen schermen meer zijn"],
         final=("de kinderen om half negen in bed liggen", "de kinderen liggen om half negen in bed"),
         ref=("in het weekend mag het negen uur zijn", "de kinderen liggen om half negen in bed, in het weekend om negen uur")),
    dict(frd="niemand de was opruimt", fr=("niemand ruimt de was op", "Niemand ruimt de was op"),
         opts=["iedereen zijn eigen was plooit", "we de was op zondag samen doen", "de was niet langer dan een dag in de mand blijft"],
         final=("iedereen zijn eigen propere was zelf opruimt", "iedereen ruimt zijn eigen propere was zelf op"),
         ref=("de kinderen mogen hulp vragen", "iedereen ruimt zijn eigen propere was zelf op, de kinderen mogen hulp vragen")),
    dict(frd="de vaatwasser nooit uitgeladen wordt", fr=("de vaatwasser wordt nooit uitgeladen", "De vaatwasser wordt nooit uitgeladen"),
         opts=["wie hem het eerst ziet hem uitlaadt", "de kinderen hem 's ochtends uitladen", "we een beurtrol maken"],
         final=("wie als eerste thuiskomt de vaatwasser uitlaadt", "wie als eerste thuiskomt laadt de vaatwasser uit"),
         ref=("en in het weekend doen de kinderen het", "wie als eerste thuiskomt laadt de vaatwasser uit, in het weekend de kinderen")),
    dict(frd="we nooit tijd hebben voor elkaar", fr=("we hebben nooit tijd voor elkaar", "We hebben nooit tijd voor elkaar"),
         opts=["we elke week een avond vrijhouden", "we om de twee weken een babysit nemen", "we zondagochtend samen koffie drinken"],
         final=("we elke vrijdagavond voor ons twee houden", "elke vrijdagavond voor ons twee houden"),
         ref=("of zaterdag als het vrijdag niet lukt", "elke vrijdagavond voor ons twee houden, anders zaterdag")),
]
RULES_EN = [
    dict(frd="the kitchen is always a mess at night", fr=("the kitchen is always a mess at night", "The kitchen is always a mess at night"), opts=["whoever cooks doesn't clean", "we load the dishwasher right after dinner"],
         final=("we tidy the kitchen together for ten minutes every evening", "tidy the kitchen together for ten minutes every evening"), ref=("make it fifteen minutes", "tidy the kitchen together for fifteen minutes every evening")),
    dict(frd="there are shoes all over the hallway", fr=("there are shoes all over the hallway", "There are shoes all over the hallway"), opts=["everyone puts their shoes in the cupboard", "we put a shoe rack by the door"],
         final=("everyone puts their shoes away straight away", "shoes go straight into the cupboard"), ref=("and coats on the hooks too", "shoes go straight into the cupboard and coats on the hooks")),
]
FRICTIONS = [("het stoort me dat {x}", "{X}"), ("wat ik niet zo leuk vind is dat {x}", "{X}"), ("ik erger me eraan dat {x}", "{X}"), ("ik word gek van het feit dat {x}", "{X}"), ("ik ben het beu dat {x}", "{X}"), ("da's toch altijd hetzelfde, {x}", "{X}")]
FRICTION_FACTS = [("de vuilbak altijd overloopt", "De vuilbak loopt altijd over"), ("de ochtenden altijd chaos zijn", "De ochtenden zijn altijd chaos"), ("ik alles alleen doe", "Ik doe alles alleen"),
                  ("we altijd te laat zijn op school", "We zijn altijd te laat op school"), ("de living vol speelgoed ligt", "De living ligt vol speelgoed"), ("niemand de koelkast aanvult", "Niemand vult de koelkast aan"),
                  ("er aan tafel altijd geruzied wordt", "Er wordt aan tafel altijd geruzied"), ("de badkamer nooit gepoetst wordt", "De badkamer wordt nooit gepoetst")]
WINS = [("fijn dat we woensdag samen gegeten hebben", "We hebben woensdag samen gegeten"), ("de ochtenden liepen veel vlotter", "De ochtenden liepen veel vlotter"),
        ("goed dat de garage eindelijk opgeruimd is", "De garage is eindelijk opgeruimd"), ("ik ben trots dat {n} zelf naar school fietste", "{n} fietste zelf naar school"),
        ("de zondagwandeling deed echt deugd", "De zondagwandeling deed echt deugd"), ("blij dat we eindelijk naar de tandarts zijn geweest", "We zijn eindelijk naar de tandarts geweest"),
        ("het boodschappenlijstje werkte echt goed", "Het boodschappenlijstje werkte goed"), ("da was echt gezellig zaterdag", "Zaterdag was echt gezellig"),
        ("{n} heeft zelf haar kamer opgeruimd, top", "{n} heeft zelf haar kamer opgeruimd")]
SELF = ["ik heb eerlijk gezegd de was niet goed opgevolgd", "ik heb mijn sport weer overgeslagen", "ik was er met mijn hoofd niet bij", "ik merk dat ik het altijd uitstel", "ik had weinig geduld met de kinderen"]
GOALS = [("ik wil meer sporten dit jaar", "Meer sporten"), ("we willen minder schermtijd", "Minder schermtijd"), ("ons doel is elke maand een uitstap met de kinderen", "Elke maand een uitstap met de kinderen"),
         ("ik zou graag leren koken", "Leren koken"), ("we willen sparen voor een nieuwe auto", "Sparen voor een nieuwe auto"), ("we willen het huis tegen de zomer op orde", "Het huis tegen de zomer op orde")]
IDEAS = [("misschien kunnen we de boodschappen online bestellen", "De boodschappen online bestellen"), ("wat als we een weekplanning op de koelkast hangen", "Een weekplanning op de koelkast hangen"),
         ("zouden we niet beter op zondag voor de hele week koken", "Op zondag voor de hele week koken"), ("misschien een poetshulp nemen", "Een poetshulp nemen"),
         ("we zouden een vast moment voor de administratie kunnen kiezen", "Een vast moment voor de administratie kiezen")]

def call(name, **args):
    return (name, {k: v for k, v in args.items() if v})

def f(s, n=None, d=None):
    return s.replace("{n}", n or pick(NAMES)).replace("{d}", d or "")

# ---------- scenes: (lines, calls) ----------
def task_plain():
    nl, en = pick(TASKS); n = pick(NAMES); d = pick(DAYS)
    if R.random() < 0.15:
        e = f(en, n); de = pick(DAYS_EN)
        said = pick([f"I need to {e} {('on ' + de) if de[0].isupper() else de}", f"we have to {e} {('on ' + de) if de[0].isupper() else de}", f"don't forget to {e} {de}"])
        return [said], [call("add_task", text=cap(e), day=de)]
    item = f(nl, n)
    if R.random() < 0.2: d = None
    forms = [f"ik moet {d} nog {item}", f"{d} moet ik {item}", f"we moeten {d} {item}", f"{item}, dat moet {d} gebeuren", f"niet vergeten: {item}, {d}",
             f"ik ga {d} {item}", f"ge moet {d} efkes {item}", f"allee, {item} moet echt {d}"] if d else \
            [f"we moeten nog {item}", f"ik moet nog {item}", f"{item} moet ook nog gebeuren", f"niet vergeten: {item}", f"{item} staat ook nog open", f"ge moet efkes {item}"]
    return [pick(FILL) + pick(forms)], [call("add_task", text=cap(item), day=d)]

def task_ask():
    nl, en = pick(TASKS); n = pick(NAMES); d = pick(DAYS); item = f(nl, n)
    ask = pick([f"kan jij {d} {item}", f"zou jij {d} {item}", f"misschien kan jij {d} {item}", f"wie kan er {d} {item}", f"kun jij {item} {d}"])
    r = R.random()
    if r < 0.55:
        return [ask, pick(YES)], [call("add_task", text=cap(item), day=d)]
    if r < 0.8:
        return [ask, pick(NO)], []
    d2 = pick([x for x in DAYS if x != d])
    return [ask, f"nee, {d} niet, maar {d2} wel", pick(ACK)], [call("add_task", text=cap(item), day=d2)]

def appt(change=False):
    nl, onl, en, oen = pick(APPTS); n = pick(NAMES)
    if R.random() < 0.15:
        de = pick(DAYS_EN); dd = ("on " + de) if de[0].isupper() else de
        return [f(en, n, dd)], [call("add_appointment", text=f(oen, n), day=de)]
    d = pick(DAYS); t = pick(TIMES) if R.random() < 0.3 else None
    said = f(nl, n, d) + (f" {t}" if t else "")
    if change:
        d2 = pick([x for x in DAYS if x != d])
        return [said, pick(ACK), pick([f"o nee wacht, dat is verzet naar {d2}", f"nee, het is {d2} geworden", f"oei, ze hebben dat verplaatst naar {d2}", f"correctie: dat is {d2}"])], \
               [call("add_appointment", text=f(onl, n), day=d2, time=t)]
    return [pick(FILL) + said], [call("add_appointment", text=f(onl, n), day=d, time=t)]

def cancel():
    lines, calls = pick([task_plain, appt])()
    return lines + [pick(["ah nee, dat heb ik gisteren al gedaan", "laat maar, dat is niet meer nodig", "o wacht, dat is al geregeld", "nee, dat gaat niet meer door", "dat is afgelast trouwens"])], []

def free_time():
    nl, en = pick(FREE); d = pick(["zondag", "zaterdag", "dit weekend", "vrijdagavond", "zaterdagnamiddag", "zondagvoormiddag"])
    if R.random() < 0.15:
        de = pick(["on Sunday", "on Saturday", "this weekend"])
        return [f"let's plan {en} {de}"], [call("add_free_time", text=cap(en), day=de.replace("on ", ""))]
    said = pick([f"{d} {nl}", f"zullen we {d} {nl} doen", f"ik wil {d} gewoon {nl}", f"{d} houden we vrij voor {nl}", f"wat als we {d} {nl} doen"])
    if said.startswith(("zullen", "wat als")):
        return [said, pick(YES + ["ja, goed idee", "oh ja, leuk"])], [call("add_free_time", text=cap(nl), day=d)]
    return [said], [call("add_free_time", text=cap(nl), day=d)]

def routine():
    nl, w, en, wen = pick(ROUTINES)
    if R.random() < 0.15:
        return [f"let's {en} {wen}"], [call("add_routine", text=cap(en), when=wen)]
    said = pick([f"{w} {nl}", f"we moeten {w} {nl}", f"vanaf nu {w} {nl}?", f"wat als we {w} {nl}", f"{nl}, dat moet {w}"])
    if said.endswith("?") or said.startswith("wat als"):
        return [said, pick(YES + ["ja, dat lijkt me haalbaar"])], [call("add_routine", text=cap(nl), when=w)]
    return [said], [call("add_routine", text=cap(nl), when=w)]

def shopping():
    if R.random() < 0.15:
        a, b = R.sample(GROCERIES_EN, 2)
        if R.random() < 0.5: return [f"we need {a} and {b}"], [call("add_shopping", items=f"{a}, {b}")]
        return [pick([f"we're out of {a}", f"put {a} on the list"])], [call("add_shopping", items=a)]
    items = R.sample(GROCERIES, R.randint(1, 3))
    lines = [pick([f"we moeten nog {' en '.join(items)} halen", f"de {items[0]} is op" if len(items) == 1 else f"we hebben geen {' en '.join(items)} meer", f"zet {' en '.join(items)} op de lijst"])]
    if R.random() < 0.4:
        extra = pick([g for g in GROCERIES if g not in items]); lines.append(pick([f"en ook nog {extra}", f"oh en {extra}", f"{extra} mag er ook bij"])); items.append(extra)
    if R.random() < 0.25 and len(items) > 1:
        gone = items[0]; lines.append(pick([f"nee wacht, {gone} hebben we nog", f"{gone} moet niet, er is nog", f"schrap de {gone} maar"])); items = items[1:]
    return lines, [call("add_shopping", items=", ".join(items))]

def meals():
    k = R.randint(1, 3); days = R.sample(["maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag", "morgen"], k); dishes = R.sample(DISHES, k)
    if R.random() < 0.12:
        dd = pick(["Tuesday", "Friday", "Sunday"]); di = pick(DISHES_EN)
        return [f"{di} on {dd}?", pick(YES_EN)], [call("plan_meal", dish=cap(di), day=dd)]
    lines = []
    for d, di in zip(days, dishes):
        lines.append(pick([f"{d} eten we {di}", f"{d} {di}", f"ik dacht {d} {di}", f"wat dacht je van {di} op {d}"]))
        if lines[-1].startswith("wat dacht") or R.random() < 0.3: lines.append(pick(ACK + ["lekker", "ja, goed idee"]))
    if R.random() < 0.3 and k >= 1:
        i = R.randrange(k); new = pick([x for x in ["maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag"] if x not in days])
        lines.append(pick([f"nee, de {dishes[i]} doen we beter {new}", f"wacht, {dishes[i]} schuift naar {new}"])); days[i] = new
    return lines, [call("plan_meal", dish=cap(di), day=d) for d, di in zip(days, dishes)]

def rule_scene(kind):
    en = R.random() < 0.15
    r = pick(RULES_EN if en else RULES)
    lines, calls = [], []
    with_friction = kind in ("friction_rule", "full") or R.random() < 0.4
    if with_friction:
        lead = pick(["it annoys me that ", "honestly "]) if en else pick(["het stoort mij echt dat ", "wat ik niet leuk vind is dat ", "ik erger me eraan dat ", ""])
        lines += [lead + (r["frd"] if lead.endswith("dat ") else r["fr"][0])]
        lines.append(pick(ACK_EN) if en else pick(["ja, dat stoort mij ook", "ja, dat herken ik", "inderdaad", "mij ook"]))
    opts = R.sample(r["opts"], min(len(r["opts"]), R.randint(1, 3)))
    for o in opts:
        lines.append((pick(["could we agree that ", "what if "]) if en else pick(["zouden we niet kunnen afspreken dat ", "misschien dat ", "of dat ", "wat als ", "kunnen we niet zeggen dat "])) + o)
        if R.random() < 0.6: lines.append(pick(["I'm not sure that works", "but then the weekend is a mess"]) if en else pick(["ik weet niet of dat gaat lukken", "ja maar dan blijft het weekend een rommel", "hmm, dat is veel gevraagd", "ik twijfel", "maar wie gaat dat dan doen"]))
    if kind == "undecided":
        lines.append(pick(["let's think about it", "we'll see next week"]) if en else pick(["laten we er nog eens over nadenken", "we bekijken het volgende week", "goh, ik weet het nog niet", "dat bespreken we nog"]))
        return lines, [call("log_friction", text=r["fr"][1])] if with_friction and R.random() < 0.5 and False else []
    lead = pick(["okay, so let's agree that ", "right, from now on "]) if en else pick(["goed, dan spreken we af dat ", "oké, afgesproken dat ", "dus vanaf nu: ", "dan zeggen we dat ", "allee, we spreken af dat ", "ok, de afspraak wordt: "])
    lines.append(lead + (r["final"][0] if lead.endswith(("dat ", "that ", "on ")) else r["final"][1]))
    lines.append(pick(["deal", "agreed"]) if en else pick(["afgesproken", "ja, doen we", "top", "akkoord"]))
    text = r["final"][1]
    if kind in ("refine", "full") and R.random() < 0.6:
        lines.append(r["ref"][0]); lines.append(pick(["fine", "yes"]) if en else pick(["ja, goed", "oké, dat is beter", "akkoord"])); text = r["ref"][1]
    return lines, [call("add_house_rule", text=cap(text))]

def friction_only():
    s, _ = pick(FRICTIONS); x, out = pick(FRICTION_FACTS)
    lines = [s.replace("{x}", out[0].lower() + out[1:] if s.startswith("da's") else x)]
    if R.random() < 0.6: lines.append(pick(["ja, dat stoort mij ook", "dat herken ik", "inderdaad", "ja, dat is waar"]))
    return lines, [call("log_friction", text=out)]

def win():
    s, out = pick(WINS); n = pick(NAMES)
    lines = [f(s, n)]
    if R.random() < 0.5: lines.append(pick(["ja, dat was fijn", "zeker", "echt waar", "ja, top"]))
    return lines, [call("add_win", text=f(out, n))]

def talk():
    lines = R.sample(CHAT, R.randint(1, 3))
    if R.random() < 0.4: lines.append(pick(SELF))
    if R.random() < 0.3: lines.append(pick(["ja, dat herken ik", "mja", "dat is normaal hoor"]))
    if R.random() < 0.15: lines = R.sample(CHAT_EN, 2)
    return lines, []

def goal():
    s, out = pick(GOALS); return [s] + ([pick(["ja, dat wil ik ook", "goed plan"])] if R.random() < 0.5 else []), [call("add_goal", text=out)]

def idea():
    s, out = pick(IDEAS); return [s], [call("add_idea", text=out)]

def query():
    r = R.random()
    if r < 0.6:
        d = pick(["morgen", "vandaag", "deze week", "zaterdag", "dit weekend", "volgende week", "donderdag"])
        return [pick([f"wat staat er {d} op de agenda", f"hey, wat is er {d}", f"wat hebben we {d}", f"wat moeten we {d} doen", f"hebben we {d} iets gepland", f"wat is er {d} allemaal te doen", f"toon de agenda van {d}", f"wat hebben we {d} allemaal"])], [call("show_agenda", day=d)]
    if r < 0.85:
        return [pick(["wat moeten we nog kopen", "wat staat er op de boodschappenlijst", "toon de boodschappen", "wat staat er op het lijstje"])], [call("show_list", list="boodschappen")]
    d = pick(["tomorrow", "this week", "today"])
    return [pick([f"what's on the agenda {d}", f"what do we have {d}"])], [call("show_agenda", day=d)]

# (problem said, commitment with {d}, task out)
PROBLEMS = [
    ("de kraan in de badkamer lekt", "ik maak die {d} wel", "Kraan in de badkamer maken"), ("de lamp in de keuken is kapot", "ik vervang die {d}", "Lamp in de keuken vervangen"),
    ("de printer doet het weer niet", "ik kijk er {d} naar", "Printer nakijken"), ("de vuilbakken stinken", "ik kuis ze {d} wel uit", "Vuilbakken uitkuisen"),
    ("het gras staat veel te hoog", "ik rij het {d} wel af", "Gras afrijden"), ("de fiets van {n} heeft een platte band", "ik plak die {d}", "Band van {n} zijn fiets plakken"),
    ("de verwarming maakt raar lawaai", "ik bel {d} de chauffagist", "Chauffagist bellen"), ("er ligt nog een stapel strijk", "ik strijk {d} wel", "Strijken"),
    ("de zolder staat vol rommel", "ik ruim die {d} op", "Zolder opruimen"), ("de auto maakt een vreemd geluid", "ik ga er {d} mee naar de garage", "Auto naar de garage"),
]
def problem():
    s, c, out = pick(PROBLEMS); n = pick(NAMES); d = pick(DAYS)
    lines = [f(s, n)]
    if R.random() < 0.5: lines.append(pick(["ja, dat moet echt eens gebeuren", "oei ja", "al een tijdje"]))
    lines.append(c.replace("{d}", d))
    return lines, [call("add_task", text=f(out, n), day=d)]

def who():
    nl, _ = pick(TASKS); n = pick(NAMES); d = pick(DAYS); item = f(nl, n)
    lines = [pick([f"wie gaat {d} {item}", f"wie kan er {d} {item}", f"en {item}, wie doet dat {d}"])]
    if R.random() < 0.75:
        lines.append(pick(["ik", "ik doe dat wel", "dat doe ik", "ik neem dat", "laat mij maar"]))
        return lines, [call("add_task", text=cap(item), day=d)]
    lines.append(pick(["goh, dat zien we nog", "geen idee, we kijken nog"]))
    return lines, []

SCENES = [(problem, 4), (who, 4), (task_plain, 10), (task_ask, 8), (lambda: appt(False), 8), (lambda: appt(True), 5), (cancel, 4), (free_time, 5), (routine, 5), (shopping, 6), (meals, 6),
          (lambda: rule_scene("plain"), 4), (lambda: rule_scene("friction_rule"), 4), (lambda: rule_scene("refine"), 4), (lambda: rule_scene("full"), 3), (lambda: rule_scene("undecided"), 4),
          (friction_only, 5), (win, 5), (talk, 7), (goal, 3), (idea, 3), (query, 5)]

def scene():
    fs, ws = zip(*SCENES); return R.choices(fs, ws)[0]()

def thread():
    lines, calls = scene()
    # A topic sometimes holds two things, or chit-chat around it; a topic that comes back says so.
    if R.random() < 0.18:
        l2, c2 = scene(); lines, calls = lines + l2, calls + c2
    if R.random() < 0.12:
        lines.insert(R.randint(0, len(lines)), pick(CHAT + ["oei, de soep kookt over", "wacht, ik pak even koffie", "waar was ik"]))
    if R.random() < 0.08 and len(lines) > 2:
        k = R.randint(1, len(lines) - 1); lines.insert(k, pick(["om nog eens terug te komen op wat we zeiden", "en nog even over dat van daarnet", "trouwens, over dat van daarnet"]))
    return lines, calls

WEEKDAYS = ["maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag"]
def prompt_date():
    d = R.randint(1, 28); m = pick(["januari", "maart", "mei", "juni", "september", "oktober", "november", "december"]); return f"{pick(WEEKDAYS)} {d} {m}"

import sys
if __name__ == "__main__" and (len(sys.argv) <= 3 or sys.argv[3] != "windows"):
    n = int(sys.argv[1]); out = open(sys.argv[2], "w")
    for _ in range(n):
        lines, calls = thread()
        out.write(json.dumps({"date": prompt_date(), "lines": [l.strip() for l in lines], "calls": calls}, ensure_ascii=False) + "\n")

# ---------- windows: what the app actually feeds the interpreter (the last lines of a meeting) ----------
RETURN = ["om nog eens terug te komen op wat we zeiden", "en nog even over dat van daarnet", "trouwens, over dat van daarnet", "oh ja, nog iets over dat van daarnet"]
CHANGE_DAY = ["o nee wacht, dat wordt {d}", "correctie: dat is {d}", "dat is trouwens verschoven naar {d}", "nee, het is {d} geworden"]
CANCEL = ["dat van daarnet mag weg, dat is niet meer nodig", "laat dat van daarnet maar, het is al geregeld", "dat gaat toch niet door"]

def window():
    """1–4 topics in a row, some talk in between, and now and then a topic that comes back changed."""
    k = R.choices([1, 2, 3, 4], [2, 4, 3, 2])[0]
    parts = []
    for _ in range(k):
        lines, calls = scene()
        parts.append([lines, calls])
        if R.random() < 0.25: parts.append([R.sample(CHAT, 1), []])
    lines = [l for p in parts for l in p[0]]
    calls = [c for p in parts for c in p[1]]
    # A topic comes back after another one: it moves to another day, or it is dropped.
    dated = [i for i, c in enumerate(calls) if c[0] in ("add_task", "add_appointment", "plan_meal", "add_free_time") and c[1].get("day")]
    if k >= 2 and dated and R.random() < 0.35:
        i = pick(dated)
        if R.random() < 0.7:
            d2 = pick([x for x in DAYS if x != calls[i][1]["day"]])
            name, args = calls[i]
            what = args.get("text") or args.get("dish")
            lines += [pick(RETURN) + ", " + what.lower(), pick(CHANGE_DAY).replace("{d}", d2)]
            calls[i] = (name, {**args, "day": d2})
        else:
            name, args = calls[i]
            lines += [pick(RETURN) + ", " + (args.get("text") or args.get("dish")).lower(), pick(CANCEL)]
            calls.pop(i)
    return lines, calls

if __name__ == "__main__" and len(sys.argv) > 3 and sys.argv[3] == "windows":
    R.seed(int(sys.argv[4]) if len(sys.argv) > 4 else 11)
    n = int(sys.argv[1]); out = open(sys.argv[2], "w")
    for _ in range(n):
        lines, calls = window()
        while len(lines) > 16: lines, calls = window()
        # a window cut at its start loses what came before: only keep calls whose topic is still in view
        out.write(json.dumps({"date": prompt_date(), "lines": [l.strip() for l in lines], "calls": calls}, ensure_ascii=False) + "\n")
