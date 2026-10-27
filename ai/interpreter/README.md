# The interpreter (Gesprekken begrijpen)

FunctionGemma 270M, fine-tuned to read a weekoverleg and name what was decided as Hearth actions. The app side lives
in `ai/src/commonMain/kotlin/app/hearth/ai/interpret`; this folder is how the model is made.

FunctionGemma is a Gemma model: it is provided under and subject to the Gemma Terms of Use
(https://ai.google.dev/gemma/terms). The base weights come from `google/functiongemma-270m-it` (or a mirror of it).

## Files

- `fmt.py`: the prompt and answer format (the app's `Interpreter.promptFor` is the same, character for character).
- `gen.py`: the training talk. Scenes (tasks, appointments, free time, routines, shopping, meals, house rules,
  frictions, ideas, goals, wins, questions) said the way people talk: small talk around them, options before a
  decision, someone changing their mind ("nee toch maar vrijdag"), a topic that comes back, a plan that is dropped.
  `python gen.py 3600 train.jsonl` writes conversations; `python gen.py 1600 windows.jsonl windows [seed]` writes
  longer stretches (1–4 scenes, up to 16 lines) like the ones `MeetingReader` hands over. Extend the phrase banks at
  the top to teach it new ways of saying things.
- `test.jsonl`: 60 conversations written by hand, never trained on. The score that counts.
- `train.py OUT EPOCHS`: full fine-tune on the CPU (embeddings frozen, loss on the answer only). `DATA=…` picks the
  data, `START=…` continues from an earlier model, `BASE=…` the base model. Resumes from `OUT/progress.json`.
- `evaluate.py MODEL DATA`: "fully right" (every action and its words) and "right actions" (the right kinds).
- Export: the onnxruntime-genai model builder, int4 for the CPU, then `lastlogits.py` (only the last position's word
  scores, so the first step doesn't fill 150 MB with them), and the base model's `tokenizer.json` next to it:

  ```
  python -m onnxruntime_genai.models.builder -m OUT -o onnx -p int4 -e cpu
  python lastlogits.py onnx final && cp onnx/tokenizer.json final/
  ```

  Builder 0.18 with transformers 5 needs two fixes in `builders/gemma.py` (Gemma 3 RoPE): read the sliding and global
  theta from `config.rope_parameters`, and set `self.rope_attrs["theta"] = config.rope_theta` after init (without it
  the global layers use 10000 and the output drifts). `ortloop.py` checks the export against torch.
- Host `tokenizer.json`, `model.onnx` and `model.onnx.data` side by side and build with
  `-Phearth.interpreterUrl=<that folder>` (or `HEARTH_INTERPRETER_URL`); without it the option stays hidden.
