// Hearth relay: one shared address for every family's tablet, so ChatGPT and Claude can reach it.
//
//   ChatGPT / Claude  --POST /mcp (Bearer token)-->  this Worker  --WebSocket-->  the family's tablet
//
// Deployed once by the Hearth maintainer. Families never see a key: they add <relay>/mcp as a connector,
// the AI app opens a Hearth page, and they type the 6-digit pairing code their tablet shows. That is a
// standard OAuth 2.1 login (PKCE, dynamic client registration, refresh-token rotation), which is what
// MCP connectors in ChatGPT and Claude expect.
//
// Storage: per household (a Durable Object) a hash of the tablet key and the linked AI apps with hashes
// of their tokens; in one Directory object the pairing codes that are valid right now. No household data.

const ID = /^[A-Za-z0-9_-]{32,128}$/;
const TIMEOUT_MS = 25_000;
const CODE_TTL_MS = 10 * 60_000;
const AUTH_CODE_TTL_MS = 5 * 60_000;
const ACCESS_TTL_S = 3600;
const REFRESH_TTL_MS = 180 * 24 * 3600_000;
const MAX_FAILED_CODES = 10;
const PROTOCOL_PATH = "/.well-known/oauth-protected-resource";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const origin = url.origin;
    const path = url.pathname.replace(/\/+$/, "") || "/";
    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));

    if (path === "/") return new Response("Hearth relay", { headers: { "content-type": "text/plain" } });
    if (path === PROTOCOL_PATH || path === `${PROTOCOL_PATH}/mcp`) return cors(json(resourceMetadata(origin)));
    if (path === "/.well-known/oauth-authorization-server" || path === "/.well-known/openid-configuration" ||
        path === "/.well-known/oauth-authorization-server/mcp") {
      return cors(json(serverMetadata(origin)));
    }
    if (path === "/register") return cors(await register(request));
    if (path === "/authorize") return authorize(request, env, url);
    if (path === "/token") return cors(await token(request, env, origin));
    if (path === "/mcp") return cors(await mcp(request, env, origin));

    const [, kind, household] = path.split("/");
    if (kind === "connect" && household && ID.test(household)) {
      return env.HOUSEHOLD.get(env.HOUSEHOLD.idFromName(await sha256(household))).fetch(request);
    }
    return new Response("Not found", { status: 404 });
  },
};

// ---- OAuth: discovery ----

function resourceMetadata(origin) {
  return {
    resource: `${origin}/mcp`,
    authorization_servers: [origin],
    bearer_methods_supported: ["header"],
    scopes_supported: ["hearth"],
    resource_name: "Hearth",
  };
}

function serverMetadata(origin) {
  return {
    issuer: origin,
    authorization_endpoint: `${origin}/authorize`,
    token_endpoint: `${origin}/token`,
    registration_endpoint: `${origin}/register`,
    response_types_supported: ["code"],
    grant_types_supported: ["authorization_code", "refresh_token"],
    code_challenge_methods_supported: ["S256"],
    token_endpoint_auth_methods_supported: ["none"],
    scopes_supported: ["hearth"],
    client_id_metadata_document_supported: true,
    authorization_response_iss_parameter_supported: true,
  };
}

// ---- OAuth: clients ----

/**
 * Dynamic client registration (RFC 7591). Stateless: the client id carries the redirect URIs and name.
 * That is safe because anyone may register any redirect URI anyway; the login page shows where it goes.
 */
async function register(request) {
  if (request.method !== "POST") return new Response("Use POST", { status: 405 });
  let meta;
  try {
    meta = await request.json();
  } catch {
    return json({ error: "invalid_client_metadata", error_description: "Body must be JSON" }, 400);
  }
  const uris = Array.isArray(meta.redirect_uris) ? meta.redirect_uris.filter((u) => typeof u === "string") : [];
  if (uris.length === 0 || uris.length > 10 || !uris.every(validRedirect)) {
    return json({ error: "invalid_redirect_uri", error_description: "Redirect URIs must be https or localhost" }, 400);
  }
  const name = typeof meta.client_name === "string" ? meta.client_name.slice(0, 60) : "";
  const clientId = "hc_" + b64url(new TextEncoder().encode(JSON.stringify({ r: uris, n: name })));
  return json({
    client_id: clientId,
    client_id_issued_at: Math.floor(Date.now() / 1000),
    client_name: name || undefined,
    redirect_uris: uris,
    grant_types: ["authorization_code", "refresh_token"],
    response_types: ["code"],
    token_endpoint_auth_method: "none",
  }, 201);
}

/** A client from its id: a registered one (hc_…) or a Client ID Metadata Document (an https URL). */
async function client(clientId) {
  if (typeof clientId !== "string") return null;
  if (clientId.startsWith("hc_")) {
    try {
      const { r, n } = JSON.parse(new TextDecoder().decode(unb64url(clientId.slice(3))));
      if (!Array.isArray(r) || !r.every(validRedirect)) return null;
      return { id: clientId, redirectUris: r, name: n || "" };
    } catch {
      return null;
    }
  }
  if (clientId.startsWith("https://")) {
    try {
      const res = await fetch(clientId, { headers: { accept: "application/json" }, cf: { cacheTtl: 300 } });
      if (!res.ok) return null;
      const doc = await res.json();
      if (doc.client_id !== clientId || !Array.isArray(doc.redirect_uris) || !doc.redirect_uris.every(validRedirect)) return null;
      return { id: clientId, redirectUris: doc.redirect_uris, name: String(doc.client_name || "").slice(0, 60) };
    } catch {
      return null;
    }
  }
  return null;
}

function validRedirect(uri) {
  try {
    const u = new URL(uri);
    if (u.hash) return false;
    if (u.protocol === "https:") return true;
    return u.protocol === "http:" && (u.hostname === "localhost" || u.hostname === "127.0.0.1" || u.hostname === "[::1]");
  } catch {
    return false;
  }
}

// ---- OAuth: the login page ----

/** GET shows "type the code from your tablet"; POST checks it and sends the person back to their AI app. */
async function authorize(request, env, url) {
  const nl = (request.headers.get("accept-language") || "").toLowerCase().startsWith("nl");
  const params = request.method === "POST" ? Object.fromEntries(await request.formData()) : Object.fromEntries(url.searchParams);
  const c = await client(params.client_id);
  if (!c || !c.redirectUris.includes(params.redirect_uri)) {
    return page(nl, null, nl ? "Deze koppeling klopt niet. Begin opnieuw vanuit ChatGPT of Claude." : "This link isn't valid. Start again from ChatGPT or Claude.");
  }
  const back = (query) => {
    const to = new URL(params.redirect_uri);
    for (const [k, v] of Object.entries({ ...query, state: params.state, iss: url.origin })) if (v !== undefined) to.searchParams.set(k, v);
    return Response.redirect(to.toString(), 302);
  };
  if (params.response_type !== "code") return back({ error: "unsupported_response_type" });
  if (!params.code_challenge || params.code_challenge_method !== "S256") {
    return back({ error: "invalid_request", error_description: "PKCE with S256 is required" });
  }
  if (params.resource && !validResource(params.resource, url.origin)) return back({ error: "invalid_target" });

  const app = appName(c, params.redirect_uri);
  const fields = ["response_type", "client_id", "redirect_uri", "state", "code_challenge", "code_challenge_method", "resource", "scope"];
  const hidden = Object.fromEntries(fields.filter((f) => params[f] !== undefined).map((f) => [f, params[f]]));
  if (request.method !== "POST") return page(nl, { app, hidden });

  const ip = request.headers.get("cf-connecting-ip") || "unknown";
  const pairing = (params.pairing_code || "").replace(/\D/g, "");
  const found = await directory(env).fetch("https://directory/redeem", { method: "POST", body: JSON.stringify({ code: pairing, ip }) });
  if (!found.ok) {
    const msg = found.status === 429
      ? (nl ? "Even te veel pogingen. Probeer het over een kwartier opnieuw." : "Too many tries. Please try again in fifteen minutes.")
      : (nl ? "Die code klopt niet of is verlopen. Tik op de tablet op “Toon koppelcode” voor een nieuwe." : "That code isn't right or has expired. Tap “Show pairing code” on the tablet for a new one.");
    return page(nl, { app, hidden }, msg);
  }
  const { household } = await found.json();
  const stub = env.HOUSEHOLD.get(env.HOUSEHOLD.idFromString(household));
  const made = await stub.fetch("https://household/oauth/code", {
    method: "POST",
    body: JSON.stringify({ clientId: c.id, app, redirectUri: params.redirect_uri, challenge: params.code_challenge }),
  });
  const { code } = await made.json();
  return back({ code });
}

function validResource(resource, origin) {
  const r = resource.replace(/\/+$/, "").toLowerCase();
  return r === `${origin}/mcp`.toLowerCase() || r === origin.toLowerCase();
}

/** What to call the AI app on the page and the tablet: its name, or else where it sends you back to. */
function appName(c, redirectUri) {
  const host = new URL(redirectUri).hostname;
  const known = { "claude.ai": "Claude", "claude.com": "Claude", "chatgpt.com": "ChatGPT", "chat.openai.com": "ChatGPT" };
  for (const [domain, name] of Object.entries(known)) if (host === domain || host.endsWith("." + domain)) return name;
  return c.name ? `${c.name} (${host})` : host;
}

function page(nl, form, error) {
  const t = nl
    ? { title: "Hearth koppelen", lead: (a) => `<b>${esc(a)}</b> wil jullie Hearth gebruiken.`, ask: "Tik op de tablet in Koppelingen op <b>Toon koppelcode</b> en typ de code hier.", go: "Koppelen", note: "Je kunt dit altijd weer ontkoppelen op de tablet." }
    : { title: "Link Hearth", lead: (a) => `<b>${esc(a)}</b> wants to use your Hearth.`, ask: "On the tablet, open Connections and tap <b>Show pairing code</b>, then type the code here.", go: "Link", note: "You can unlink this any time on the tablet." };
  const inputs = form ? Object.entries(form.hidden).map(([k, v]) => `<input type="hidden" name="${esc(k)}" value="${esc(v)}">`).join("") : "";
  const body = form
    ? `<p>${t.lead(form.app)}</p><p class="muted">${t.ask}</p>
       <form method="post" action="/authorize">${inputs}
         <input name="pairing_code" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9 ]*" maxlength="9" placeholder="123 456" autofocus required>
         ${error ? `<p class="error">${esc(error)}</p>` : ""}
         <button type="submit">${t.go}</button>
       </form><p class="muted small">${t.note}</p>`
    : `<p class="error">${esc(error)}</p>`;
  const html = `<!doctype html><html lang="${nl ? "nl" : "en"}"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${t.title}</title>
<style>
  :root{color-scheme:light dark;--bg:#f6f1ea;--card:#fffaf4;--ink:#2b2420;--muted:#6f645c;--ember:#d9622b;--err:#a8432a}
  @media (prefers-color-scheme:dark){:root{--bg:#1c1816;--card:#26201d;--ink:#f3ebe4;--muted:#b4a79d;--ember:#f08a55;--err:#f0a08a}}
  body{margin:0;background:var(--bg);color:var(--ink);font:17px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif;display:grid;place-items:center;min-height:100vh}
  main{background:var(--card);border-radius:28px;padding:32px 28px;margin:16px;max-width:420px;width:calc(100% - 32px);box-sizing:border-box}
  h1{margin:0 0 12px;font-size:26px}.muted{color:var(--muted)}.small{font-size:14px}.error{color:var(--err)}
  input[name=pairing_code]{width:100%;box-sizing:border-box;font-size:34px;letter-spacing:.2em;text-align:center;padding:14px;border-radius:18px;border:2px solid var(--muted);background:transparent;color:var(--ink);margin:12px 0 4px}
  input[name=pairing_code]:focus{outline:none;border-color:var(--ember)}
  button{width:100%;margin-top:12px;font-size:19px;font-weight:600;padding:16px;border:0;border-radius:18px;background:var(--ember);color:#fff;cursor:pointer}
</style></head><body><main><h1>${t.title}</h1>${body}</main></body></html>`;
  return new Response(html, {
    status: error && !form ? 400 : 200,
    headers: {
      "content-type": "text/html; charset=utf-8",
      "x-frame-options": "DENY",
      "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; form-action 'self' https: http://localhost:* http://127.0.0.1:*; frame-ancestors 'none'",
      "referrer-policy": "no-referrer",
      "cache-control": "no-store",
    },
  });
}

// ---- OAuth: tokens ----

async function token(request, env, origin) {
  if (request.method !== "POST") return new Response("Use POST", { status: 405 });
  let p;
  try {
    p = Object.fromEntries(await request.formData());
  } catch {
    return oauthError("invalid_request", "Body must be form-encoded");
  }
  if (p.resource && !validResource(p.resource, origin)) return oauthError("invalid_target");
  const presented = p.grant_type === "authorization_code" ? p.code : p.grant_type === "refresh_token" ? p.refresh_token : null;
  if (p.grant_type !== "authorization_code" && p.grant_type !== "refresh_token") return oauthError("unsupported_grant_type");
  const stub = householdOf(env, presented);
  if (!stub) return oauthError("invalid_grant");
  const res = await stub.fetch("https://household/oauth/token", { method: "POST", body: JSON.stringify(p) });
  return new Response(res.body, { status: res.status, headers: { "content-type": "application/json", "cache-control": "no-store", pragma: "no-cache" } });
}

/** Tokens and codes start with their household's id, so they need no shared lookup table. */
function householdOf(env, credential) {
  const prefix = typeof credential === "string" ? credential.split(".")[0] : "";
  if (!/^[0-9a-f]{64}$/.test(prefix)) return null;
  try {
    return env.HOUSEHOLD.get(env.HOUSEHOLD.idFromString(prefix));
  } catch {
    return null;
  }
}

// ---- MCP ----

async function mcp(request, env, origin) {
  const auth = request.headers.get("authorization") || "";
  const bearer = auth.toLowerCase().startsWith("bearer ") ? auth.slice(7).trim() : "";
  const stub = householdOf(env, bearer);
  const res = stub ? await stub.fetch("https://household/mcp", { method: request.method, headers: { "x-hearth-token": bearer }, body: request.method === "POST" ? await request.text() : undefined }) : null;
  if (!res || res.status === 401) {
    return new Response(JSON.stringify({ error: "invalid_token", error_description: "Link Hearth again in your AI app." }), {
      status: 401,
      headers: {
        "content-type": "application/json",
        "www-authenticate": `Bearer realm="hearth", resource_metadata="${origin}${PROTOCOL_PATH}"${bearer ? ', error="invalid_token"' : ""}`,
      },
    });
  }
  return res;
}

// ---- Durable Objects ----

function directory(env) {
  return env.DIRECTORY.get(env.DIRECTORY.idFromName("directory"));
}

/** One family: its tablet's WebSocket, and the AI apps it has linked. */
export class Household {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.waiting = new Map();
  }

  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname.startsWith("/connect/")) return this.connect(request, url);
    if (url.pathname === "/mcp") return this.mcp(request);
    if (url.pathname === "/oauth/code") return this.newAuthCode(await request.json());
    if (url.pathname === "/oauth/token") return this.token(await request.json());
    return new Response("Not found", { status: 404 });
  }

  /** The tablet connects. The first tablet's key is remembered; others are turned away. */
  async connect(request, url) {
    if (request.headers.get("Upgrade") !== "websocket") return new Response("Expected a WebSocket", { status: 426 });
    const key = url.searchParams.get("key") || "";
    if (!ID.test(key)) return new Response("Missing tablet key", { status: 401 });
    const hash = await sha256(key);
    const known = await this.state.storage.get("tabletKey");
    if (known && known !== hash) return new Response("This household belongs to another tablet", { status: 403 });
    if (!known) await this.state.storage.put("tabletKey", hash);

    for (const old of this.state.getWebSockets()) old.close(4000, "replaced by a new connection");
    const [client, server] = Object.values(new WebSocketPair());
    this.state.acceptWebSocket(server);
    server.send(JSON.stringify({ type: "apps", apps: await this.apps() }));
    return new Response(null, { status: 101, webSocket: client });
  }

  /** An MCP request from a linked AI app (streamable HTTP, JSON responses). */
  async mcp(request) {
    const grant = await this.checkAccess(request.headers.get("x-hearth-token"));
    if (!grant) return new Response("Unauthorized", { status: 401 });
    if (request.method === "GET") return new Response("Use POST", { status: 405, headers: { allow: "POST" } });
    if (request.method === "DELETE") return new Response(null, { status: 204 });
    if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });

    const body = await request.text();
    const tablet = this.state.getWebSockets()[0];
    if (!tablet) return offline(body);

    const id = crypto.randomUUID();
    const answer = new Promise((resolve) => {
      this.waiting.set(id, resolve);
      setTimeout(() => {
        if (this.waiting.delete(id)) resolve(undefined);
      }, TIMEOUT_MS);
    });
    try {
      tablet.send(JSON.stringify({ id, body, app: grant.app }));
    } catch {
      this.waiting.delete(id);
      return offline(body);
    }
    const reply = await answer;
    if (reply === undefined) return offline(body);
    if (!reply) return new Response(null, { status: 202 });
    return new Response(reply, { headers: { "content-type": "application/json" } });
  }

  async newAuthCode({ clientId, app, redirectUri, challenge }) {
    const secret = randomToken();
    await this.state.storage.put(`code:${await sha256(secret)}`, { clientId, app, redirectUri, challenge, expires: Date.now() + AUTH_CODE_TTL_MS });
    return json({ code: `${this.state.id.toString()}.${secret}` });
  }

  async token(p) {
    if (p.grant_type === "authorization_code") {
      const key = `code:${await sha256(secretOf(p.code))}`;
      const code = await this.state.storage.get(key);
      await this.state.storage.delete(key);
      if (!code || code.expires < Date.now() || code.clientId !== p.client_id || code.redirectUri !== p.redirect_uri) return oauthError("invalid_grant");
      if (!p.code_verifier || b64url(await digest(p.code_verifier)) !== code.challenge) return oauthError("invalid_grant", "PKCE check failed");
      const grant = { id: crypto.randomUUID(), clientId: code.clientId, app: code.app, createdAt: Date.now() };
      const out = await this.issue(grant);
      this.tell({ type: "linked", app: grant.app });
      await this.sendApps();
      return out;
    }
    // Refresh: the grant id sits in the token; the old refresh token stops working (rotation).
    const [, grantId] = (p.refresh_token || "").split(".");
    const grant = grantId ? await this.state.storage.get(`grant:${grantId}`) : null;
    if (!grant || grant.clientId !== p.client_id || grant.refreshExpires < Date.now() ||
        grant.refreshHash !== (await sha256(secretOf(p.refresh_token)))) {
      return oauthError("invalid_grant");
    }
    return this.issue(grant);
  }

  async issue(grant) {
    const prefix = `${this.state.id.toString()}.${grant.id}.`;
    const access = prefix + randomToken();
    const refresh = prefix + randomToken();
    grant.accessHash = await sha256(secretOf(access));
    grant.accessExpires = Date.now() + ACCESS_TTL_S * 1000;
    grant.refreshHash = await sha256(secretOf(refresh));
    grant.refreshExpires = Date.now() + REFRESH_TTL_MS;
    grant.lastUsed = Date.now();
    await this.state.storage.put(`grant:${grant.id}`, grant);
    return json({ access_token: access, token_type: "Bearer", expires_in: ACCESS_TTL_S, refresh_token: refresh, scope: "hearth" });
  }

  async checkAccess(tokenValue) {
    const parts = (tokenValue || "").split(".");
    if (parts.length !== 3) return null;
    const grant = await this.state.storage.get(`grant:${parts[1]}`);
    if (!grant || grant.accessExpires < Date.now() || grant.accessHash !== (await sha256(parts[2]))) return null;
    if (Date.now() - (grant.lastUsed || 0) > 3600_000) {
      grant.lastUsed = Date.now();
      await this.state.storage.put(`grant:${grant.id}`, grant);
    }
    return grant;
  }

  async apps() {
    const grants = await this.state.storage.list({ prefix: "grant:" });
    return [...grants.values()]
      .filter((g) => g.refreshExpires > Date.now())
      .map((g) => ({ id: g.id, name: g.app, since: g.createdAt, lastUsed: g.lastUsed }))
      .sort((a, b) => a.since - b.since);
  }

  async sendApps() {
    this.tell({ type: "apps", apps: await this.apps() });
  }

  tell(msg) {
    for (const ws of this.state.getWebSockets()) {
      try {
        ws.send(JSON.stringify(msg));
      } catch {}
    }
  }

  async webSocketMessage(ws, message) {
    let msg;
    try {
      msg = JSON.parse(typeof message === "string" ? message : new TextDecoder().decode(message));
    } catch {
      return;
    }
    if (msg.ping) {
      ws.send('{"pong":true}');
      return;
    }
    if (msg.type === "pair") {
      const res = await directory(this.env).fetch("https://directory/new", { method: "POST", body: JSON.stringify({ household: this.state.id.toString() }) });
      ws.send(JSON.stringify({ type: "code", ...(await res.json()) }));
      return;
    }
    if (msg.type === "unlink") {
      if (msg.app === "all") {
        const grants = await this.state.storage.list({ prefix: "grant:" });
        await this.state.storage.delete([...grants.keys()]);
      } else if (typeof msg.app === "string") {
        await this.state.storage.delete(`grant:${msg.app}`);
      }
      await this.sendApps();
      return;
    }
    const resolve = this.waiting.get(msg.id);
    if (resolve) {
      this.waiting.delete(msg.id);
      resolve(msg.body ?? "");
    }
  }

  webSocketClose(ws, code, reason) {
    try {
      ws.close(code, reason);
    } catch {}
  }
}

/** The pairing codes valid right now (code -> household), with a brake on guessing. */
export class Directory {
  constructor(state) {
    this.state = state;
  }

  async fetch(request) {
    const url = new URL(request.url);
    const body = await request.json();
    const now = Date.now();
    if (url.pathname === "/new") {
      // One code per household at a time; a new one replaces the old one.
      const previous = await this.state.storage.get(`h:${body.household}`);
      if (previous) await this.state.storage.delete(`c:${previous}`);
      let code;
      do {
        code = String(crypto.getRandomValues(new Uint32Array(1))[0] % 1_000_000).padStart(6, "0");
      } while (await this.state.storage.get(`c:${code}`));
      const expiresAt = now + CODE_TTL_MS;
      await this.state.storage.put({ [`c:${code}`]: { household: body.household, expiresAt }, [`h:${body.household}`]: code });
      await this.state.storage.setAlarm(now + CODE_TTL_MS + 60_000);
      return json({ code, expiresAt });
    }
    if (url.pathname === "/redeem") {
      const ipKey = `ip:${body.ip}`;
      const tries = (await this.state.storage.get(ipKey)) || { count: 0, until: now + 15 * 60_000 };
      if (tries.until < now) Object.assign(tries, { count: 0, until: now + 15 * 60_000 });
      if (tries.count >= MAX_FAILED_CODES) return new Response("Too many tries", { status: 429 });
      const entry = /^\d{6}$/.test(body.code) ? await this.state.storage.get(`c:${body.code}`) : null;
      if (!entry || entry.expiresAt < now) {
        tries.count++;
        await this.state.storage.put(ipKey, tries);
        return new Response("Unknown code", { status: 404 });
      }
      await this.state.storage.delete([`c:${body.code}`, `h:${entry.household}`]);
      return json({ household: entry.household });
    }
    return new Response("Not found", { status: 404 });
  }

  /** Tidy up expired codes and old guess counters. */
  async alarm() {
    const now = Date.now();
    const all = await this.state.storage.list();
    const stale = [];
    for (const [k, v] of all) {
      if (k.startsWith("c:") && v.expiresAt < now) stale.push(k, `h:${v.household}`);
      if (k.startsWith("ip:") && v.until < now) stale.push(k);
    }
    for (let i = 0; i < stale.length; i += 128) await this.state.storage.delete(stale.slice(i, i + 128));
    if ([...all.keys()].some((k) => k.startsWith("c:") && !stale.includes(k))) await this.state.storage.setAlarm(now + CODE_TTL_MS);
  }
}

// ---- helpers ----

/** A JSON-RPC error for every request in the body, so the AI can tell the person what's going on. */
function offline(body) {
  let ids = [];
  try {
    const parsed = JSON.parse(body);
    ids = (Array.isArray(parsed) ? parsed : [parsed]).filter((m) => m && m.id !== undefined).map((m) => m.id);
  } catch {}
  if (ids.length === 0) return new Response(null, { status: 202 });
  const error = (id) => ({
    jsonrpc: "2.0",
    id,
    error: { code: -32001, message: "Hearth is not reachable right now: the tablet is offline or Hearth is closed." },
  });
  const out = ids.length === 1 ? error(ids[0]) : ids.map(error);
  return json(out);
}

function oauthError(error, description) {
  return json({ error, ...(description ? { error_description: description } : {}) }, 400);
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { "content-type": "application/json" } });
}

function cors(res) {
  const out = new Response(res.body, res);
  out.headers.set("access-control-allow-origin", "*");
  out.headers.set("access-control-allow-headers", "authorization, content-type, mcp-protocol-version, mcp-session-id");
  out.headers.set("access-control-allow-methods", "GET, POST, DELETE, OPTIONS");
  out.headers.set("access-control-expose-headers", "www-authenticate, mcp-session-id");
  return out;
}

function esc(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

/** The part of a token after the last dot (the random secret). */
function secretOf(value) {
  const s = String(value || "");
  return s.slice(s.lastIndexOf(".") + 1);
}

function randomToken() {
  return b64url(crypto.getRandomValues(new Uint8Array(32)));
}

async function digest(text) {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
}

async function sha256(text) {
  return [...(await digest(text))].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function b64url(bytes) {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function unb64url(text) {
  const s = atob(text.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((text.length + 3) % 4));
  return Uint8Array.from(s, (c) => c.charCodeAt(0));
}
