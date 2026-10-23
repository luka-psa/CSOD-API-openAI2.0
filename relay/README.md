# Hearth relay

A tiny Cloudflare Worker that gives the family's tablet a fixed HTTPS address, so ChatGPT and Claude
(which call MCP connectors from their cloud) can reach Hearth.

```
ChatGPT / Claude ──POST /mcp/<secret>──▶ relay ──WebSocket──▶ Hearth on the tablet
```

- The tablet keeps one outgoing WebSocket to `/connect/<secret>?key=<tablet key>` and reconnects by itself.
- Each MCP request (JSON-RPC, streamable HTTP with JSON responses) is forwarded and Hearth's answer returned.
- The relay stores nothing but a hash of the tablet key (the first tablet to connect owns the household).
- When the tablet is offline the AI gets a clear JSON-RPC error.
- One Durable Object per household (SQLite-backed, available on the Workers Free plan).

## Install

**From Hearth (recommended):** Verbind AI → paste a Cloudflare API token made from the
“Edit Cloudflare Workers” template → Relay installeren. Hearth uploads `src/worker.js` (embedded at build
time), enables workers.dev and saves the address. The token is not stored.

**By hand (laptop):** `npx wrangler deploy` in this folder, then type the address in Hearth under
“Al een relay-adres?”.

## Test

```
npm install miniflare
node --test relay/test/relay.test.mjs
```

The Kotlin end-to-end test (`mcp/src/jvmTest/.../RelayEndToEndTest.kt`) runs against a live relay when
`HEARTH_RELAY_URL` is set, e.g. a Miniflare server on `http://127.0.0.1:8787`.
