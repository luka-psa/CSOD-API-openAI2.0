# The generation loop the app runs (plain ONNX Runtime): the prompt once, then one token at a time with the cache.
import numpy as np, onnxruntime as ort, time
from tokenizers import Tokenizer
STOP = {1, 50, 106}
CAP = 512
class OrtGen:
    def __init__(self, folder, threads=4):
        so = ort.SessionOptions(); so.intra_op_num_threads = threads
        self.s = ort.InferenceSession(f"{folder}/model.onnx", so); self.tok = Tokenizer.from_file(f"{folder}/tokenizer.json")
        self.layers = sum(1 for i in self.s.get_inputs() if i.name.endswith(".key"))
    def generate(self, text, max_new=120):
        ids = self.tok.encode(text, add_special_tokens=False).ids
        # Shared buffers (as exported): one block of CAP positions per layer, written in place; the mask says how far.
        past = {f"past_key_values.{i}.{kv}": np.zeros((1, 1, CAP, 256), np.float32) for i in range(self.layers) for kv in ("key", "value")}
        out = []; feed = ids; total = 0
        for _ in range(max_new):
            total += len(feed)
            r = self.s.run(None, {"input_ids": np.array([feed], np.int64), "attention_mask": np.ones((1, total), np.int64), **past})
            nxt = int(r[0][0, -1].argmax())
            if nxt in STOP: break
            out.append(nxt); feed = [nxt]
            names = [o.name for o in self.s.get_outputs()]
            past = {n.replace("present", "past_key_values"): v for n, v in zip(names[1:], r[1:])}
        return self.tok.decode(out, skip_special_tokens=False)
if __name__ == "__main__":
    import sys
    from fmt import prompt
    g = OrtGen(sys.argv[1]); t = time.time()
    print(repr(g.generate(prompt("maandag 2 november", ["dinsdag heeft Mia tandarts"]))), f"{time.time()-t:.2f}s")
