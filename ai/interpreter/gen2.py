# Round 2 of the interpreter's talk: far more words (so it copies what was said instead of a word it knows), and the
# ways of talking round 1 missed. Builds on gen.py; nothing here is taken from test.jsonl (check_overlap() makes sure).
import json, sys, re
import gen
from gen import R, pick, cap, call, f, NAMES, ACK, YES, NO, CHAT

# ---------- more words ----------
gen.GROCERIES += ["sla", "komkommer", "paprika's", "wortelen", "aardappelen", "uien", "look", "champignons", "prei", "spinazie",
    "broccoli", "bloemkool", "courgette", "witloof", "peren", "druiven", "kiwi's", "citroenen", "aardbeien", "mandarijnen",
    "ham", "kipfilet", "gehakt", "salami", "zalm", "vissticks", "mozzarella", "geraspte kaas", "room", "platte kaas",
    "volle melk", "havermelk", "beschuiten", "cornflakes", "havermout", "confituur", "honing", "speculoos", "chips", "nootjes",
    "olijfolie", "mayonaise", "ketchup", "mosterd", "bloem", "suiker", "zout", "peper", "bouillonblokjes", "tomatenpuree",
    "pasta saus", "couscous", "noedels", "wraps", "pistolets", "sandwiches", "croissants", "spuitwater", "cola", "bier",
    "wijn", "thee", "cacao", "babydoekjes", "zonnecrème", "shampoo", "douchegel", "zeep", "keukenrol", "aluminiumfolie",
    "diepvrieszakjes", "afwasborstel", "sponsjes", "allesreiniger", "wasverzachter", "vaatwasblokjes", "kaarsen", "lampjes",
    "kattenbakvulling", "hondenbrokken", "verband", "pleisters", "paracetamol", "hoestsiroop", "brooddozen", "fruit"]
gen.GROCERIES_EN += ["butter", "pasta", "rice", "tomatoes", "onions", "potatoes", "carrots", "orange juice", "crisps", "cereal",
    "dish soap", "bin bags", "kitchen roll", "tea", "ham", "chicken", "yoghurts", "nappy wipes", "shampoo", "toothbrushes"]
gen.DISHES += ["vol-au-vent met frietjes", "spaghetti bolognese", "macaroni met ham en kaas", "visstick met puree", "kip curry",
    "lasagne met spinazie", "groentesoep", "tomatensoep met balletjes", "erwtensoep", "quiche", "ovenschotel", "wokgroenten met kip",
    "nasi goreng", "bami", "sushi", "burgers", "hotdogs", "croque monsieur", "omelet", "pannenkoeken met appel", "wafels",
    "konijn met pruimen", "vispannetje met prei", "zalm met broccoli", "steak met frietjes", "videe", "kalkoen", "raclette",
    "fondue", "couscous met groenten", "falafel", "pita", "salade niçoise", "pasta carbonara", "ravioli", "gnocchi", "paella",
    "stoverij", "waterzooi", "koude schotel", "boterhammen", "soep en boterhammen", "risotto met champignons", "shoarma",
    "ovenfrietjes met kipnuggets", "zuurkool met worst", "biefstuk", "kippenboutjes", "moussaka", "chili sin carne"]
gen.DISHES_EN += ["spaghetti", "stir fry", "burgers", "pancakes", "chicken curry", "fish and chips", "roast chicken", "wraps", "omelette"]
gen.TASKS += [
    ("de autoverzekering vergelijken", "compare car insurance"), ("de energieleverancier bellen", "call the energy supplier"),
    ("de inschrijving voor de sportkamp doen", "sign up for sports camp"), ("de rookmelders testen", "test the smoke alarms"),
    ("de winterkleren van zolder halen", "get the winter clothes down"), ("een nieuwe matras bestellen", "order a new mattress"),
    ("de schoolfoto's betalen", "pay for the school photos"), ("het paspoort van {n} verlengen", "renew {n}'s passport"),
    ("de identiteitskaart van {n} aanvragen", "apply for {n}'s ID card"), ("de mutualiteit binnenbrengen", "hand in the health insurance forms"),
    ("de schouw laten vegen", "get the chimney swept"), ("de dakgoot laten herstellen", "get the gutter repaired"),
    ("de kinderopvang voor de zomer regelen", "arrange childcare for the summer"), ("het verlof aanvragen", "request time off"),
    ("de oude kleren naar de kringwinkel brengen", "take old clothes to the charity shop"), ("de boekentas van {n} herstellen", "fix {n}'s school bag"),
    ("de fietsen laten nakijken", "get the bikes serviced"), ("de terrasmeubels binnenzetten", "bring the garden furniture in"),
    ("de haag snoeien", "trim the hedge"), ("de bladeren bijeenharken", "rake the leaves"), ("de kelder opruimen", "tidy the basement"),
    ("een afspraak maken bij de oogarts", "book the eye doctor"), ("de huur overschrijven", "pay the rent"), ("de gemeente mailen", "email the council"),
    ("de schoolreis van {n} betalen", "pay for {n}'s school trip"), ("een nieuwe stofzuiger kopen", "buy a new vacuum cleaner"),
    ("de tuinslang opbergen", "put the hose away"), ("de banken laten reinigen", "get the sofa cleaned"), ("de kamer van {n} schilderen", "paint {n}'s room"),
    ("de klusjesman bellen", "call the handyman"), ("de vergunning aanvragen", "apply for the permit"), ("het contract van de gsm opzeggen", "cancel the phone contract"),
]
gen.APPTS += [
    ("{n} heeft {d} logopedie", "Logopedie {n}", "{n} has speech therapy {d}", "Speech therapy {n}"),
    ("{d} komt de loodgieter voor de boiler", "Loodgieter voor de boiler", "the plumber comes for the boiler {d}", "Plumber for the boiler"),
    ("{d} heb ik een etentje met collega's", "Etentje met collega's", "I have a dinner with colleagues {d}", "Dinner with colleagues"),
    ("{n} speelt {d} een toneel op school", "Toneel {n} op school", "{n} is in a school play {d}", "School play {n}"),
    ("{d} is de communie van {n}", "Communie {n}", "{n}'s communion is {d}", "{n}'s communion"),
    ("{d} moet ik naar de kinesist", "Kinesist", "I have physio {d}", "Physio"),
    ("{d} komen de buren een glas drinken", "Buren komen een glas drinken", "the neighbours come for drinks {d}", "Drinks with the neighbours"),
    ("{n} heeft {d} een verjaardagsfeestje", "Verjaardagsfeestje {n}", "{n} has a birthday party {d}", "Birthday party {n}"),
    ("{d} is er een infoavond op school", "Infoavond op school", "there's an info evening at school {d}", "School info evening"),
    ("{n} moet {d} naar de orthodontist", "Orthodontist {n}", "{n} sees the orthodontist {d}", "Orthodontist {n}"),
    ("{d} komt de technieker voor de verwarming", "Technieker voor de verwarming", "the heating engineer comes {d}", "Heating engineer"),
    ("{d} heeft {n} judo", "Judo {n}", "{n} has judo {d}", "Judo {n}"),
    ("{d} vertrekken we op weekend", "Vertrek op weekend", "we leave for the weekend {d}", "Weekend away"),
]
gen.TIMES += ["om elf uur", "om half negen", "om vier uur", "om kwart voor drie", "om zeven uur", "om half twaalf", "'s middags", "'s avonds om zeven"]
gen.DAYS += ["volgende week maandag", "volgende dinsdag", "eind deze maand", "begin volgende week", "woensdagnamiddag", "donderdagavond", "zaterdagochtend", "over twee weken", "na het weekend"]
gen.FREE += [("een namiddag naar het park", "an afternoon in the park"), ("samen ontbijten op bed", "breakfast in bed"), ("een wandeling in het bos", "a walk in the woods"),
             ("naar de film", "go to the cinema"), ("een dagje naar de dierentuin", "a day at the zoo"), ("bij vrienden gaan eten", "dinner with friends"),
             ("een uur voor mezelf lezen", "an hour of reading for myself"), ("gaan schaatsen", "go ice skating"), ("een picknick", "a picnic")]
gen.ROUTINES += [("de vaat doen", "na het eten", "do the dishes", "after dinner"), ("de rugzakken nakijken", "elke zondagavond", "check the school bags", "every Sunday evening"),
                 ("de handdoeken wassen", "elke maandag", "wash the towels", "every Monday"), ("de koelkast uitkuisen", "elke maand", "clean out the fridge", "every month"),
                 ("samen koken", "elke donderdagavond", "cook together", "every Thursday evening"), ("tanden poetsen", "na het ontbijt", "brush teeth", "after breakfast"),
                 ("naar het zwembad", "elke zaterdagochtend", "go to the pool", "every Saturday morning"), ("de post nakijken", "elke vrijdag", "go through the post", "every Friday")]
gen.FRICTION_FACTS += [("er nooit iemand de hond uitlaat", "Niemand laat de hond uit"), ("de was altijd blijft liggen", "De was blijft altijd liggen"),
                       ("we 's avonds altijd te laat eten", "We eten 's avonds altijd te laat"), ("de auto altijd een rommel is", "De auto is altijd een rommel"),
                       ("ik altijd moet herinneren aan het huiswerk", "Ik moet altijd herinneren aan het huiswerk"), ("de jassen overal rondslingeren", "De jassen slingeren overal rond")]
gen.FRICTIONS += [("ik vind het vervelend dat {x}", "{X}"), ("het irriteert me dat {x}", "{X}"), ("wat me stoort is dat {x}", "{X}")]
gen.IDEAS += [("misschien kunnen we een vaste avond voor de was kiezen", "Een vaste avond voor de was kiezen"), ("wat als we de kinderen een klusjesbord geven", "Een klusjesbord voor de kinderen"),
              ("we zouden een gedeelde agenda kunnen gebruiken", "Een gedeelde agenda gebruiken"), ("misschien moeten we een maaltijdbox proberen", "Een maaltijdbox proberen")]
gen.GOALS += [("ik wil dit jaar meer tijd met de kinderen", "Meer tijd met de kinderen"), ("we willen elke week samen sporten", "Elke week samen sporten"),
              ("ik wil gezonder eten", "Gezonder eten"), ("we willen minder geld uitgeven aan afhaal", "Minder geld aan afhaal")]
gen.CHAT += ["het was een rustige week", "ik ben blij dat het weekend is", "de trein had weer vertraging", "de kleine zat vol energie", "ik heb amper gesport", "het werk loopt goed"]
gen.CHAT_EN += ["it was a quiet week", "the train was late again", "pretty busy, lots of calls", "same as always", "I'm glad it's the weekend"]
gen.SELF += ["ik was te vaak op mijn gsm", "ik heb niet goed opgelet met het eten", "ik ben te laat gaan slapen", "ik had het te druk om te helpen"]

# ---------- ways of talking round 1 missed ----------
OTHER_DAYS = ["maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag"]

def meals_said():
    k = R.randint(1, 3); days = R.sample(OTHER_DAYS + ["morgen"], k); dishes = R.sample(gen.DISHES, k)
    lines = []
    for d, di in zip(days, dishes):
        lines.append(pick([f"{d} maak ik {di}", f"{d} gaan we {di} halen", f"en {d} eten we {di}", f"{d} kook ik {di}", f"op {d} {di}", f"{d} wordt het {di}"]))
    return lines, [call("plan_meal", dish=cap(di), day=d) for d, di in zip(days, dishes)]

def meal_moved():
    d, d2 = R.sample(OTHER_DAYS, 2); di = pick(gen.DISHES)
    lines = [pick([f"{d} {di}?", f"wat denk je van {di} op {d}", f"zullen we {d} {di} eten"]),
             pick([f"oei nee, {d} ben ik er niet", f"{d} eet ik niet thuis", f"{d} heb ik sport", f"nee, {d} komen we laat thuis"]),
             pick([f"doe het dan {d2}", f"dan maar {d2}", f"{d2} dan?", f"verschuif het naar {d2}"])]
    if R.random() < 0.7: lines.append(pick(ACK + ["goed"]))
    return lines, [call("plan_meal", dish=cap(di), day=d2)]

def meal_question():
    d = pick(OTHER_DAYS + ["morgen", "vanavond"]); di = pick(gen.DISHES)
    lines = [pick([f"wat eten we {d}", f"wat koken we {d}", f"heb je een idee voor {d}"])]
    r = R.random()
    if r < 0.5:
        lines += [pick([f"zullen we {di} maken", f"misschien {di}", f"{di}?"]), pick(["ja", "lekker", "ja, goed", "oké"])]
        return lines, [call("plan_meal", dish=cap(di), day=d)]
    if r < 0.8:
        kind, specific = pick([("soep", ["groentesoep", "tomatensoep", "erwtensoep", "preisoep", "wortelsoep"]), ("pasta", ["pasta pesto", "pasta carbonara", "spaghetti bolognese"]), ("iets met vis", ["zalm met broccoli", "vispannetje", "visstick met puree"])])
        s = pick(specific)
        lines += [f"zullen we {kind} maken", f"ja, {s}"]
        return lines, [call("plan_meal", dish=cap(s), day=d)]
    lines += [pick(["geen idee", "we zien wel", "goh, ik weet het niet"])]
    return lines, []

def shopping_said():
    if R.random() < 0.2:
        a, b = R.sample(gen.GROCERIES_EN, 2)
        lines = [pick([f"we're out of {a} and {b}", f"we need {a} and {b}", f"add {a} and {b} to the list"])]
        return lines, [call("add_shopping", items=f"{a}, {b}")]
    items = R.sample(gen.GROCERIES, R.randint(1, 3)); said = list(items)
    lines = [pick([f"we hebben geen {items[0]} meer", f"zet {' en '.join(items)} op de lijst", f"{' en '.join(items)} op de lijst", f"de {items[0]} is op", f"we moeten {' en '.join(items)} hebben"])]
    if not lines[0].startswith(("zet", "we moeten")) and not lines[0].endswith("op de lijst"): items = items[:1]; said = list(items)
    for _ in range(R.choice([0, 1, 1, 2])):
        extra = pick([g for g in gen.GROCERIES if g not in items])
        lines.append(pick([f"en {extra}", f"o en {extra}", f"de {extra} raakt ook op", f"{extra} ook", f"en nog wat {extra}"])); items.append(extra)
    if R.random() < 0.3 and len(items) > 1:
        gone = pick(items)
        lines.append(pick([f"nee, {gone} is er nog genoeg", f"{gone} heb ik vandaag al gekocht", f"{gone} hebben we nog", f"schrap {gone} maar", f"{gone} moet niet meer"]))
        items.remove(gone)
    return lines, [call("add_shopping", items=", ".join(items))]

def ask_then_self():
    nl, _ = pick(gen.TASKS); n = pick(NAMES); d = pick(OTHER_DAYS); item = f(nl, n)
    lines = [pick([f"kan jij {d} {item}", f"zou jij {d} {item}"]), pick([f"{d} niet, ik heb een afspraak op het werk", f"nee, {d} lukt mij niet", f"{d} ben ik weg"])]
    if R.random() < 0.6:
        lines.append(pick(["dan doe ik het wel", "ok, dan doe ik het", "goed, dan neem ik het"]))
        return lines, [call("add_task", text=cap(item), day=d)]
    lines.append(pick(["hmm, dan zien we nog", "ok, we kijken nog"]))
    return lines, []

def who_split():
    n = pick(NAMES); d1, d2 = R.sample(OTHER_DAYS[:5], 2)
    act = pick([("{n} naar school brengen", "naar school brengen"), ("{n} ophalen", "ophalen"), ("{n} naar de opvang brengen", "naar de opvang brengen"), ("{n} naar de training brengen", "naar de training brengen")])
    item = act[0].replace("{n}", n)
    lines = [f"wie gaat {d1} {item}", pick(["ik", "ik doe dat"]), pick([f"ok en {d2} doe ik het", f"en {d2} neem ik het"])]
    return lines, [call("add_task", text=cap(item), day=d1), call("add_task", text=cap(item), day=d2)]

def cancel_said():
    lines, calls = pick([gen.task_plain, gen.appt])()
    lines = lines + [pick(["ah nee, dat heb ik eergisteren al gedaan", "dat is al gebeurd hoor", "dat is afgezegd", "dat gaat niet meer door, het is afgelast", "laat maar, dat heb ik vanochtend al gedaan",
                           "dat hoeft niet meer", "o, dat is al betaald", "dat is geannuleerd"])]
    return lines, []

WIN_EVENTS = [("we zaterdag met z'n allen gaan zwemmen zijn", "We zijn zaterdag met z'n allen gaan zwemmen"),
              ("{n} zelf haar bed heeft opgemaakt", "{n} heeft zelf haar bed opgemaakt"), ("we deze week elke avond samen gegeten hebben", "We hebben deze week elke avond samen gegeten"),
              ("de boodschappen zo vlot gingen", "De boodschappen gingen vlot"), ("we de zolder eindelijk leeggemaakt hebben", "We hebben de zolder eindelijk leeggemaakt"),
              ("{n} zonder zijwieltjes leerde fietsen", "{n} leerde fietsen zonder zijwieltjes"), ("we een hele dag niks gepland hadden", "We hadden een hele dag niks gepland"),
              ("het bad-ritueel zo rustig verliep", "Het bad-ritueel verliep rustig"), ("we op tijd op school waren elke dag", "We waren elke dag op tijd op school"),
              ("{n} zelf de tafel heeft gedekt", "{n} heeft zelf de tafel gedekt"), ("we samen de tuin hebben gedaan", "We hebben samen de tuin gedaan")]
WIN_DONE = [("{n} heeft haar fietsdiploma gehaald!", "{n} heeft haar fietsdiploma gehaald"), ("{n} heeft een tien voor haar dictee!", "{n} heeft een tien voor haar dictee"),
            ("we hebben eindelijk de garage opgeruimd!", "We hebben eindelijk de garage opgeruimd"), ("het verlof is eindelijk aangevraagd, yes", "Het verlof is aangevraagd")]

def win_said():
    n = pick(NAMES)
    if R.random() < 0.65:
        c, out = pick(WIN_EVENTS)
        lines = [pick(["ik vond het tof dat ", "ik vond het wel goed dat ", "het was fijn dat ", "ik ben blij dat ", "wat ik leuk vond is dat ", "ik ben trots dat "]) + f(c, n)]
    else:
        c, out = pick(WIN_DONE); lines = [f(c, n)]
    if R.random() < 0.5: lines.append(pick(["ja, echt", "zeker", "ja, super", "ja, trots op haar", "dat was fijn"]))
    return lines, [call("add_win", text=f(out, n))]

# rule said directly: (said, out, extension said, out with extension)
DIRECT_RULES = [("geen gsm aan tafel", "Geen gsm aan tafel", "ook niet bij het ontbijt", "Geen gsm aan tafel, ook niet bij het ontbijt"),
                ("geen tv voor school", "Geen tv voor school", "ook niet in het weekend", "Geen tv voor school, ook niet in het weekend"),
                ("de schoenen gaan in de kast", "De schoenen gaan in de kast", "en de jassen aan de kapstok", "De schoenen gaan in de kast en de jassen aan de kapstok"),
                ("wie het laatste ijsje neemt zet ijs op de lijst", "Wie het laatste ijsje neemt zet ijs op de lijst", "en dat geldt voor alles", "Wie het laatste van iets neemt zet het op de lijst"),
                ("om negen uur gaan de schermen uit", "Om negen uur gaan de schermen uit", "behalve op vrijdag", "Om negen uur gaan de schermen uit, behalve op vrijdag"),
                ("iedereen ruimt zijn eigen bord af", "Iedereen ruimt zijn eigen bord af", "ook de kinderen", "Iedereen ruimt zijn eigen bord af, ook de kinderen"),
                ("wie kookt hoeft niet af te wassen", "Wie kookt hoeft niet af te wassen", "en wie afwast kiest de muziek", "Wie kookt hoeft niet af te wassen, wie afwast kiest de muziek")]
DIRECT_RULES_EN = [("no phones at the table", "No phones at the table", "not at breakfast either", "No phones at the table, not at breakfast either"),
                   ("screens off at nine", "Screens off at nine", "except on Fridays", "Screens off at nine, except on Fridays")]

def rule_direct():
    en = R.random() < 0.2
    said, out, ext, out2 = pick(DIRECT_RULES_EN if en else DIRECT_RULES)
    lines = [(pick(["from now on ", "new rule: ", "let's agree: "]) if en else pick(["vanaf nu ", "nieuwe afspraak: ", "we spreken af: ", "vanaf vandaag "])) + said,
             pick(["yes, agreed", "okay"]) if en else pick(["ja, akkoord", "oké", "goed", "ja, doen we"])]
    if R.random() < 0.5:
        lines += [ext, pick(["fine", "yes, that too"]) if en else pick(["ja, dat ook", "akkoord", "oké, ook dat"])]
        out = out2
    return lines, [call("add_house_rule", text=out)]

PASSIVE = [("de vuilbak", "buitengezet", "Vuilbak buitenzetten"), ("het aquarium", "gekuist", "Aquarium kuisen"), ("de koelkast", "uitgekuist", "Koelkast uitkuisen"),
           ("het gras", "gemaaid", "Gras maaien"), ("de ramen", "gewassen", "Ramen wassen"), ("de lakens", "ververst", "Lakens verversen"), ("de oven", "gekuist", "Oven kuisen"),
           ("de filter van de dampkap", "uitgewassen", "Filter van de dampkap uitwassen"), ("de vogelkooi", "uitgemest", "Vogelkooi uitmesten"), ("de auto", "gewassen", "Auto wassen")]
EVERY = ["om de drie dagen", "elke week", "om de twee weken", "elke maand", "twee keer per week", "elke zaterdag", "om de vier weken", "elk seizoen"]

def routine_said():
    r = R.random()
    if r < 0.4:
        thing, pp, out = pick(PASSIVE); w = pick(EVERY)
        return [pick([f"{thing} moet {w} {pp} worden", f"{thing} zou {w} {pp} moeten worden"])], [call("add_routine", text=out, when=w)]
    if r < 0.75:
        act = pick(["naar de markt", "samen koken", "met de hond naar het bos", "een spelletje spelen", "pannenkoeken bakken", "naar de speeltuin", "zwemmen met de kinderen", "fietsen met de kinderen"])
        w = pick(["elke zaterdagochtend", "elke zondagnamiddag", "elke vrijdagavond", "elke woensdagnamiddag", "elke dinsdagavond"])
        verb = "gaan we" if act.startswith(("naar", "met", "zwemmen", "fietsen")) else "gaan we"
        return [f"{w} {verb} {act}", pick(["leuk, doen we", "ja, goed idee", "top"])], [call("add_routine", text=cap(act), when=w)]
    nl, w, _, _ = pick(gen.ROUTINES)
    return [f"{nl} schiet er altijd bij in", f"{w} dan?", "ja"], [call("add_routine", text=cap(nl), when=w)]

HELPERS = [("mijn moeder", "Mama"), ("mijn vader", "Papa"), ("de buurvrouw", "De buurvrouw"), ("mijn zus", "Mijn zus"), ("oma", "Oma")]

def followups():
    r = R.random()
    if r < 0.5:
        lines, calls = gen.free_time()
        who, out = pick(HELPERS)
        lines.append(pick([f"dan vraag ik {who} om op te passen", f"dan vraag ik aan {who} of ze kan oppassen"]))
        return lines, calls + [call("add_task", text=f"{out} vragen om op te passen")]
    lines, calls = gen.appt(False)
    prep = pick([("een cadeau kopen", "Een cadeau kopen"), ("bloemen halen", "Bloemen halen"), ("de jurk ophalen bij de kuis", "De jurk ophalen bij de kuis"),
                 ("de auto tanken", "De auto tanken"), ("de papieren klaarleggen", "De papieren klaarleggen"), ("een fles wijn kopen", "Een fles wijn kopen")])
    if R.random() < 0.5:
        d2 = pick(OTHER_DAYS); lines += [f"moeten we nog {prep[0]}?", pick([f"ja, ik doe dat {d2}", f"ja, {d2} doe ik dat"])]
        return lines, calls + [call("add_task", text=prep[1], day=d2)]
    lines += [pick([f"we moeten nog {prep[0]}", f"dan moeten we nog {prep[0]}"])]
    return lines, calls + [call("add_task", text=prep[1])]

def brainstorm_open():
    topic = pick(["we zouden de garage eens moeten herinrichten", "we zouden vaker samen moeten koken", "misschien moeten we de kamers van de kinderen wisselen",
                  "zouden we een tweede auto nemen", "we zouden de tuin anders kunnen aanleggen", "zouden we de kinderen meer klusjes geven"])
    opts = R.sample(["misschien eerst eens rondvragen", "of we wachten tot de zomer", "of we maken er een lijstje van", "misschien met een planning", "of gewoon proberen"], R.randint(1, 2))
    return [topic] + opts + [pick(["pff, laten we dat later eens bekijken", "goh, daar moeten we nog eens over nadenken", "we zien het nog wel", "laat ons dat volgende week bespreken"])], []

def query_said():
    r = R.random()
    if r < 0.5:
        return [pick(["wat hebben we nog nodig van de winkel", "moet er nog iets gehaald worden", "staat er nog iets op het boodschappenlijstje", "wat moet ik meebrengen van de winkel",
                      "wat hebben we nog nodig", "wat staat er op de lijst"])], [call("show_list", list="boodschappen")]
    d = pick(["morgen", "vrijdag", "dit weekend", "deze week", "vandaag", "zondag", "volgende week"])
    if R.random() < 0.2:
        de = pick(["tomorrow", "this weekend", "on Friday", "today"])
        return [pick([f"what's on {de}", f"what are we doing {de}", f"anything planned {de}"])], [call("show_agenda", day=de.replace("on ", ""))]
    return [pick([f"wat staat er {d} op de planning", f"wat is er {d} te doen", f"hebben we {d} iets", f"wat staat er {d} in de agenda", f"hey hearth, wat hebben we {d}"])], [call("show_agenda", day=d)]

def chat_en():
    return R.sample(gen.CHAT_EN, R.randint(2, 3)), []

def task_en():
    _, en = pick(gen.TASKS); n = pick(NAMES); e = f(en, n); de = pick(gen.DAYS_EN)
    dd = ("on " + de) if de[0].isupper() else de
    if R.random() < 0.5:
        return [f"can you {e} {dd}", pick(gen.YES_EN)], [call("add_task", text=cap(e), day=de)]
    return [f"can you {e} {dd}", pick(gen.NO_EN)], []

gen.SCENES += [(meals_said, 5), (meal_moved, 3), (meal_question, 3), (shopping_said, 6), (ask_then_self, 3), (who_split, 2), (cancel_said, 3),
               (win_said, 4), (rule_direct, 3), (routine_said, 4), (followups, 4), (brainstorm_open, 3), (query_said, 3), (chat_en, 2), (task_en, 2)]

# ---------- nothing from the test set ----------
def check_overlap(rows, test="test.jsonl"):
    tests = [" ".join(json.loads(l)["lines"]).lower() for l in open(test)]
    tl = {l.lower() for t in open(test) for l in json.loads(t)["lines"]}
    same = sum(1 for r in rows for l in r["lines"] if l.lower() in tl and len(l) > 25)
    return same

if __name__ == "__main__":
    n, out, mode = int(sys.argv[1]), sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "threads"
    R.seed(int(sys.argv[4]) if len(sys.argv) > 4 else 23)
    rows = []
    for _ in range(n):
        if mode == "windows":
            lines, calls = gen.window()
            while len(lines) > 16: lines, calls = gen.window()
        else:
            lines, calls = gen.thread()
        rows.append({"date": gen.prompt_date(), "lines": [l.strip() for l in lines], "calls": calls})
    print("long lines also in the test set:", check_overlap(rows))
    with open(out, "w") as fo:
        for r in rows: fo.write(json.dumps(r, ensure_ascii=False) + "\n")
