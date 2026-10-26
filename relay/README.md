# Hearth relay

One Cloudflare Worker, deployed once by the Hearth maintainer, that every family's tablet shares. It gives
Hearth a fixed HTTPS address, so ChatGPT and Claude (which call MCP connectors from their cloud) can reach
the tablet, and it handles the login so families never see a key.

```
ChatGPT / Claude ──POST /mcp (Bearer token)──▶ relay ──WebSocket──▶ Hearth on the family's tablet
```

## How a family links an AI app

1. In Claude or ChatGPT they add a custom connector with the address `https://<relay>/mcp` (the same for
   everyone; Hearth shows it under Koppelingen).
2. The AI app opens the relay's Hearth page ("Claude wants to use your Hearth").
3. On the tablet they tap **Toon koppelcode** and type the 6-digit code on that page. Done.

Under the hood that is standard MCP authorization (OAuth 2.1): protected-resource and authorization-server
metadata, dynamic client registration (and Client ID Metadata Documents), PKCE S256, one-hour access tokens
and rotating refresh tokens (180 days). The tablet lists the linked apps and can unlink each one.

## What it stores

- Per household (a SQLite Durable Object, keyed by a hash of the tablet's household id): a hash of the
  tablet key (the first tablet that connects owns the household), and per linked app its name and hashes of
  its tokens.
- One `Directory` object: the pairing codes valid right now (10 minutes, single use) and a counter of
  wrong guesses per IP (10 per 15 minutes).
- No readable household data: MCP requests and answers only pass through.
- Per household that syncs (a `SyncSpace`, keyed by a hash of its space id): one snapshot of the household,
  sealed on the device with AES-256-GCM (the relay never has the key), its version and a hash of the devices'
  token. Forgotten after a year without syncing. In `Directory`: join offers (a sealed box under a lookup derived
  from the code with PBKDF2; ten minutes, taken once, 10 wrong tries per IP per 15 minutes).

## Deploy (one time)

Nothing is created by hand in the Cloudflare dashboard (no "Create application"): the **Deploy relay** workflow
uploads this folder with Wrangler. It only needs two repository secrets.

1. **Account ID.** In the Cloudflare dashboard it is the 32-character code in the address bar
   (`dash.cloudflare.com/<account id>/…`), also shown on the account's home page and under Workers & Pages.
2. **API token.** My Profile → API Tokens (`dash.cloudflare.com/profile/api-tokens`) → Create Token → the
   *Edit Cloudflare Workers* template → Use template. Account Resources: your account; Zone Resources: All zones.
   Continue to summary → Create Token, and copy it (it is shown once).
3. **GitHub.** Settings → Secrets and variables → Actions → New repository secret: `CLOUDFLARE_API_TOKEN` (the
   token) and `CLOUDFLARE_ACCOUNT_ID` (the id).
4. **Deploy.** Run the **Deploy relay** workflow (or re-run its last run). Its summary shows the address,
   `https://hearth-relay.<subdomain>.workers.dev`. It also runs on every change to `relay/`. If Wrangler asks to
   register a workers.dev subdomain, pick one once under Workers & Pages.

The **Web preview** workflow then bakes the address into the web and Android builds by itself (it asks Cloudflare
for the account's workers.dev subdomain); the next build has it. Families never type it: there is no field for it
in the app. To use another address (a custom domain), set the repository *variable* `HEARTH_RELAY_URL`; it wins.

**From a laptop:** `npx wrangler deploy` in this folder, then build with `-Phearth.relayUrl=<its address>`.

The Workers Free plan is enough for many households (SQLite Durable Objects are included).

## Test

```
cd relay && npm install && npm test
```

The Kotlin end-to-end test (`mcp/src/jvmTest/.../RelayEndToEndTest.kt`) runs the real tablet client
against a live relay when `HEARTH_E2E_RELAY_URL` is set (not `HEARTH_RELAY_URL`, which only bakes the address into the builds), e.g. a Miniflare server on `http://127.0.0.1:8787`:
pairing code, OAuth login, a tool call, unlink.

## Sync between a household's devices

`GET /sync/<space>?since=<version>` returns `{version}` when nothing changed, else `{version, data}`;
`POST /sync/<space>` with `{base, data}` stores a new version, or answers 409 with the current version when another
device pushed first (the device pulls, merges and pushes again). Both need `Authorization: Bearer <token>`; the first
push sets it. `POST /sync/offer` `{lookup, box}` and `POST /sync/claim` `{lookup}` hand the household's keys to a
new device once. Deploying this version adds the `SyncSpace` class (migration `v3` in `wrangler.jsonc`).

## Recipe import for the web preview

A browser may not read other sites, so the web app imports a recipe link through `GET /page?url=…` (returns
only the page's JSON-LD scripts and og:image, nothing else) and `GET /image?url=…` (images only, at most 3 MB).
The tablet app fetches pages itself and never uses these.

