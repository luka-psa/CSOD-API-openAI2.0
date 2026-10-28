# Hearth interpreter model

Storage for the on-device model behind *Gesprekken begrijpen*. Nobody downloads this by hand: the Web preview workflow
copies `interpreter/` onto the download site next to `hearth.apk`, and the tablet fetches it there in the background
when the setting is on (reading `manifest.json`, joining the parts, checking the SHA-256).

`model.onnx.data` is split in parts under GitHub's 100 MB limit. How the model is made: `ai/interpreter/` on the
main branches. Licence: see `interpreter/NOTICE.txt` (Gemma Terms of Use).
