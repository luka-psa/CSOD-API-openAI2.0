import json, sys, re, time, torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from fmt import prompt, parse
torch.set_num_threads(int(sys.argv[3]) if len(sys.argv) > 3 else 4)
path = sys.argv[1]; data = sys.argv[2]
tok = AutoTokenizer.from_pretrained(path); m = AutoModelForCausalLM.from_pretrained(path, dtype=torch.float32).eval()
STOP = [tok.convert_tokens_to_ids("<end_of_turn>"), tok.convert_tokens_to_ids("<start_function_response>")]

def words(s): return set(w for w in re.findall(r"[\w'’-]+", s.lower()) if len(w) > 2 and w not in {"de", "het", "een", "the", "van", "voor", "met", "and", "en"})
def norm(s): return re.sub(r"^(on|at|om|op)\s+", "", s.lower().strip())
def arg_ok(k, exp, got):
    if k in ("day", "when", "time", "list"): return norm(exp) == norm(got)
    if k == "items": return set(map(str.strip, exp.lower().split(","))) == set(map(str.strip, got.lower().split(",")))
    e, g = words(exp), words(got)
    return len(e & g) / max(1, len(e)) >= 0.5    # the core words are there (wording may differ)
def call_ok(e, g): return e[0] == g[0] and all(arg_ok(k, v, g[1].get(k, "")) for k, v in e[1].items() if k != "time" or v)

full = names = 0; rows = [json.loads(l) for l in open(data)]; t0 = time.time(); out = []
for r in rows:
    ids = tok(prompt(r["date"], r["lines"]), add_special_tokens=False, return_tensors="pt")["input_ids"]
    with torch.no_grad(): g = m.generate(ids, max_new_tokens=120, do_sample=False, eos_token_id=STOP)
    text = tok.decode(g[0, ids.shape[1]:], skip_special_tokens=False)
    got = parse(text); exp = [(n, a) for n, a in r["calls"]]
    ok_names = sorted(n for n, _ in got) == sorted(n for n, _ in exp)
    left = list(got); ok_full = ok_names
    for e in exp:
        hit = next((g for g in left if call_ok(e, g)), None)
        if hit is None: ok_full = False
        else: left.remove(hit)
    names += ok_names; full += ok_full
    out.append({"lines": r["lines"], "expected": exp, "got": got, "names": ok_names, "full": ok_full})
print(f"{path}: fully right {full}/{len(rows)} = {100*full/len(rows):.0f}%, right actions {names}/{len(rows)} = {100*names/len(rows):.0f}%, {1000*(time.time()-t0)/len(rows):.0f} ms/thread")
json.dump(out, open(path.rstrip('/').split('/')[-1] + "_eval.json", "w"), ensure_ascii=False, indent=1)
