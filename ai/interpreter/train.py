import json, math, random, sys, time, os, torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from fmt import prompt, answer
torch.set_num_threads(4); torch.manual_seed(0)
BASE = os.environ.get("BASE", "../models/functiongemma"); OUT = sys.argv[1]; EPOCHS = float(sys.argv[2]); LIMIT = int(sys.argv[3]) if len(sys.argv) > 3 else None
tok = AutoTokenizer.from_pretrained(BASE)
start = OUT if os.path.exists(OUT + "/config.json") else os.environ.get("START", BASE)
m = AutoModelForCausalLM.from_pretrained(start, dtype=torch.float32)
emb = m.get_input_embeddings(); emb.weight.requires_grad_(False)   # 168M of 268M: the vocabulary stays as it is
rows = [json.loads(l) for l in open(os.environ.get("DATA", "train.jsonl"))][:LIMIT]
data = []
for r in rows:
    p = tok(prompt(r["date"], r["lines"]), add_special_tokens=False)["input_ids"]
    a = tok(answer([(n, a) for n, a in r["calls"]]) + "<end_of_turn>", add_special_tokens=False)["input_ids"]
    data.append((p, a))
BATCH = 8
steps_per_epoch = math.ceil(len(data) / BATCH); total = int(steps_per_epoch * EPOCHS)
params = [p for p in m.parameters() if p.requires_grad]
opt = torch.optim.AdamW(params, lr=1e-4, weight_decay=0.0)
sched = torch.optim.lr_scheduler.LambdaLR(opt, lambda s: min(1, (s + 1) / 30) * 0.5 * (1 + math.cos(math.pi * min(s, total) / total)))
state = OUT + "/progress.json"
done = json.load(open(state))["step"] if os.path.exists(state) else 0
if done: print("resuming at", done)
rng = random.Random(1)
order = []
for e in range(math.ceil(EPOCHS) + 1):
    idx = list(range(len(data))); rng.shuffle(idx)
    # similar lengths together: less padding
    chunks = [sorted(idx[i:i + BATCH * 16], key=lambda j: len(data[j][0]) + len(data[j][1])) for i in range(0, len(idx), BATCH * 16)]
    order += [c[i:i + BATCH] for c in chunks for i in range(0, len(c), BATCH)]
for _ in range(done): sched.step()
m.train(); t0 = time.time(); W = emb.weight
for step in range(done, total):
    b = order[step]; seqs = [data[j][0] + data[j][1] for j in b]; L = max(map(len, seqs))
    ids = torch.zeros(len(b), L, dtype=torch.long); att = torch.zeros(len(b), L, dtype=torch.long); tgt = []
    for i, j in enumerate(b):
        s = seqs[i]; ids[i, :len(s)] = torch.tensor(s); att[i, :len(s)] = 1
        p = len(data[j][0]); tgt += [(i, k - 1, s[k]) for k in range(p, len(s))]   # predict only the answer
    h = m.model(input_ids=ids, attention_mask=att).last_hidden_state
    bi, pos, lab = zip(*tgt)
    logits = h[list(bi), list(pos)] @ W.T
    loss = torch.nn.functional.cross_entropy(logits, torch.tensor(lab))
    loss.backward(); torch.nn.utils.clip_grad_norm_(params, 1.0); opt.step(); sched.step(); opt.zero_grad()
    if step % 10 == 0: print(f"step {step}/{total} loss {loss.item():.3f} {(time.time()-t0)/(step-done+1):.1f}s/step", flush=True)
    if (step + 1) % 100 == 0 or step + 1 == total:
        m.save_pretrained(OUT); tok.save_pretrained(OUT); json.dump({"step": step + 1}, open(state, "w"))
print("done", flush=True)
