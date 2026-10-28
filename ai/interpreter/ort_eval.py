# The exported model (what the tablet runs) on a test set: same scoring as evaluate.py, plus how long a reading takes.
import json, sys, time, re
from ortloop import OrtGen
from fmt import prompt, parse
src = open("evaluate.py").read()
exec(src[src.index("def words"):src.index("full = names = 0")])
folder, data = sys.argv[1], sys.argv[2]; threads = int(sys.argv[3]) if len(sys.argv) > 3 else 4
g = OrtGen(folder, threads); rows = [json.loads(l) for l in open(data)]
full = names = 0; times = []; ptoks = []; out = []
for r in rows:
    p = prompt(r["date"], r["lines"]); t = time.time(); text = g.generate(p); times.append(time.time() - t)
    ptoks.append(len(g.tok.encode(p, add_special_tokens=False).ids))
    got = parse(text); exp = [(n, a) for n, a in r["calls"]]
    ok_names = sorted(n for n, _ in got) == sorted(n for n, _ in exp); left = list(got); ok_full = ok_names
    for e in exp:
        hit = next((x for x in left if call_ok(e, x)), None)
        if hit is None: ok_full = False
        else: left.remove(hit)
    names += ok_names; full += ok_full; out.append({"lines": r["lines"], "expected": exp, "got": got, "full": ok_full})
n = len(rows); times.sort()
print(f"{folder} {threads} threads: fully right {full}/{n} = {100*full/n:.0f}%, right actions {names}/{n} = {100*names/n:.0f}%, "
      f"median {times[n//2]:.2f}s, slowest {times[-1]:.2f}s, prompt {sum(ptoks)/n:.0f} tokens on average")
json.dump(out, open("ort_eval.json", "w"), ensure_ascii=False, indent=1)
