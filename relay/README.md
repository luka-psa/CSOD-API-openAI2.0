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
- No household data: requests and answers only pass through.

## Deploy (one time)

**With GitHub Actions:** add the repository secrets `CLOUDFLARE_API_TOKEN` (template “Edit Cloudflare
Workers”) and `CLOUDFLARE_ACCOUNT_ID`, then run the **Deploy relay** workflow. It also runs on every
change to `relay/`.

**From a laptop:** `npx wrangler deploy` in this folder.

Then tell the app where it lives: set the repository variable `HEARTH_RELAY_URL` (Settings → Secrets and
variables → Actions → Variables), e.g. `https://hearth-relay.<your-subdomain>.workers.dev`. The web and
Android builds bake it in (`-Phearth.relayUrl`). A custom domain works too. Families can still point their
tablet at another relay under Koppelingen → Geavanceerd.

The Workers Free plan is enough for many households (SQLite Durable Objects are included).

## Test

```
cd relay && npm install && npm test
```

The Kotlin end-to-end test (`mcp/src/jvmTest/.../RelayEndToEndTest.kt`) runs the real tablet client
against a live relay when `HEARTH_RELAY_URL` is set, e.g. a Miniflare server on `http://127.0.0.1:8787`:
pairing code, OAuth login, a tool call, unlink.
