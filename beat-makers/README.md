# Beat Makers

A cooperative musical board game: players listen to a song, then rebuild it together on a wooden 8 × 16 sequencer, one instrument each.

- **Play online with friends:** https://claude.ai/artifact/NaSS7PaU5gtPRy4RTgpsKh. Network tables, seats and live sync work here. The page is private until its owner shares it with Contributor access.
- **Game manual:** https://claude.ai/code/artifact/4754fdce-0680-4eb7-acfd-5913d57ed7dd
- **Sound demos:** [`demos/sound-engine-demo.mp3`](demos/sound-engine-demo.mp3) and [`demos/famous-tunes-demo.mp3`](demos/famous-tunes-demo.mp3)

## Running this copy

`index.html` is a standalone copy of the game. Serve the folder from any static web server, for example:

```sh
npx http-server beat-makers
```

Then open `http://localhost:8080`. Opening the file directly with `file://` won't load the instrument sounds, because browsers block those requests.

Outside claude.ai there is no shared game server. This copy offers **Play on this device** (pass-and-play on one screen). Network tables only work in the claude.ai version above.

## What's inside

| Path | Contents |
| --- | --- |
| `index.html` | The whole game: board, song cards, game rules and sound engine |
| `samples/` | 113 instrument recordings: piano, guitar, bass, violin, strings, trumpet, flute, marimba, drum kit |
| `demos/` | Audio demos rendered from the game's sound engine |

## Songs

- **Base game:** 12 traditional and folk songs in four levels (Starter, Easy, Medium, Hard).
- **Famous Tunes pack:** 8 classical and public-domain pieces, including Für Elise, Can-Can and The Entertainer.

All songs are in the public domain. Happy Birthday uses the 1893 melody, which is also public domain.

## Credits

Instrument sounds were rendered from the FluidR3 GM soundfont, © 2000–2002, 2008 Frank Wen and Toby Smithe, MIT license. See `samples/LICENSE.txt`.
