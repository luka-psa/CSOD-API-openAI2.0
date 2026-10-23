// Runs the real Worker in workerd (Miniflare): a fake tablet connects, an AI app links with a pairing
// code (OAuth 2.1 + PKCE) and calls MCP through it.
// Usage: npm test  (in relay/, after npm install)
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createHash, randomBytes } from "node:crypto";
import { Miniflare } from "miniflare";

const script = readFileSync(new URL("../src/worker.js", import.meta.url), "utf8");
const household = "h".repeat(40);
const key = "k".repeat(40);
const R = "http://relay";
const redirect = "https://claude.ai/api/mcp/auth_callback";

async function relay() {
  return new Miniflare({
    modules: true,
    script,
    compatibilityDate: "2025-09-01",
    durableObjects: {
      HOUSEHOLD: { className: "Household", useSQLite: true },
      DIRECTORY: { className: "Directory", useSQLite: true },
    },
  });
}

const rpc = (id, method) => JSON.stringify({ jsonrpc: "2.0", id, method });
const b64url = (buf) => buf.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

/** A fake tablet: echoes MCP requests and collects the relay's other messages. */
async function tablet(mf, k = key) {
  const res = await mf.dispatchFetch(`${R}/connect/${household}?key=${k}`, { headers: { Upgrade: "websocket" } });
  if (res.status !== 101) return { status: res.status };
  const ws = res.webSocket;
  ws.accept();
  const inbox = [];
  const waiters = [];
  ws.addEventListener("message", (e) => {
    const msg = JSON.parse(e.data);
    if (msg.id && msg.body !== undefined) {
      const req = JSON.parse(msg.body);
      const body = req.id === undefined ? "" : JSON.stringify({ jsonrpc: "2.0", id: req.id, result: { echo: req.method, app: msg.app } });
      ws.send(JSON.stringify({ id: msg.id, body }));
      return;
    }
    inbox.push(msg);
    waiters.splice(0).forEach((w) => w());
  });
  const next = async (type) => {
    for (;;) {
      const i = inbox.findIndex((m) => m.type === type);
      if (i >= 0) return inbox.splice(i, 1)[0];
      await new Promise((r) => waiters.push(r));
    }
  };
  return { status: 101, ws, next, send: (m) => ws.send(JSON.stringify(m)) };
}

/** Registers a client and walks the login with [pairing], returning the redirect's query. */
async function login(mf, pairing, verifier = b64url(randomBytes(32))) {
  const reg = await mf.dispatchFetch(`${R}/register`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ client_name: "Claude", redirect_uris: [redirect] }),
  });
  assert.equal(reg.status, 201);
  const { client_id } = await reg.json();
  const challenge = b64url(createHash("sha256").update(verifier).digest());
  const params = { response_type: "code", client_id, redirect_uri: redirect, state: "st8", code_challenge: challenge, code_challenge_method: "S256", resource: `${R}/mcp` };
  const pageRes = await mf.dispatchFetch(`${R}/authorize?${new URLSearchParams(params)}`, { headers: { "accept-language": "nl-BE" } });
  assert.equal(pageRes.status, 200);
  assert.match(await pageRes.text(), /<b>Claude<\/b> wil jullie Hearth gebruiken/);
  const post = await mf.dispatchFetch(`${R}/authorize`, {
    method: "POST",
    redirect: "manual",
    headers: { "content-type": "application/x-www-form-urlencoded", "cf-connecting-ip": "1.2.3.4", "accept-language": "nl-BE" },
    body: new URLSearchParams({ ...params, pairing_code: pairing }),
  });
  return { client_id, verifier, post };
}

async function exchange(mf, form) {
  const res = await mf.dispatchFetch(`${R}/token`, { method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" }, body: new URLSearchParams(form) });
  return { status: res.status, json: await res.json() };
}

const callMcp = (mf, token, body = rpc(7, "tools/list")) =>
  mf.dispatchFetch(`${R}/mcp`, { method: "POST", headers: token ? { authorization: `Bearer ${token}` } : {}, body });

test("discovery: 401 points to the metadata, which points to the login", async () => {
  const mf = await relay();
  const res = await callMcp(mf, null);
  assert.equal(res.status, 401);
  assert.match(res.headers.get("www-authenticate"), /resource_metadata="http:\/\/relay\/\.well-known\/oauth-protected-resource"/);
  const prm = await (await mf.dispatchFetch(`${R}/.well-known/oauth-protected-resource/mcp`)).json();
  assert.deepEqual(prm.authorization_servers, [R]);
  assert.equal(prm.resource, `${R}/mcp`);
  const as = await (await mf.dispatchFetch(`${R}/.well-known/oauth-authorization-server`)).json();
  assert.equal(as.token_endpoint, `${R}/token`);
  assert.deepEqual(as.code_challenge_methods_supported, ["S256"]);
  const bad = await mf.dispatchFetch(`${R}/register`, { method: "POST", body: JSON.stringify({ redirect_uris: ["http://evil.example/cb"] }) });
  assert.equal(bad.status, 400);
  await mf.dispose();
});

test("pair with a code, call MCP, refresh, unlink", async () => {
  const mf = await relay();
  const t = await tablet(mf);
  assert.deepEqual((await t.next("apps")).apps, []);

  t.send({ type: "pair" });
  const { code } = await t.next("code");
  assert.match(code, /^\d{6}$/);

  const wrong = await login(mf, code === "000000" ? "111111" : "000000");
  assert.equal(wrong.post.status, 200);
  assert.match(await wrong.post.text(), /klopt niet/);

  const { client_id, verifier, post } = await login(mf, code.slice(0, 3) + " " + code.slice(3));
  assert.equal(post.status, 302);
  const back = new URL(post.headers.get("location"));
  assert.equal(back.origin + back.pathname, redirect);
  assert.equal(back.searchParams.get("state"), "st8");
  const authCode = back.searchParams.get("code");

  // The code is single use, and the pairing code is spent too.
  const badPkce = await exchange(mf, { grant_type: "authorization_code", code: authCode, client_id, redirect_uri: redirect, code_verifier: "nope".repeat(12) });
  assert.equal(badPkce.json.error, "invalid_grant");
  const again = await login(mf, code);
  assert.equal(again.post.status, 200);

  t.send({ type: "pair" });
  const second = await login(mf, (await t.next("code")).code);
  const authCode2 = new URL(second.post.headers.get("location")).searchParams.get("code");
  const tokens = await exchange(mf, { grant_type: "authorization_code", code: authCode2, client_id: second.client_id, redirect_uri: redirect, code_verifier: second.verifier, resource: `${R}/mcp` });
  assert.equal(tokens.status, 200);
  assert.equal(tokens.json.token_type, "Bearer");
  assert.equal((await t.next("linked")).app, "Claude");
  const apps = (await t.next("apps")).apps;
  assert.equal(apps.length, 1);
  assert.equal(apps[0].name, "Claude");

  const answer = await callMcp(mf, tokens.json.access_token);
  assert.deepEqual(await answer.json(), { jsonrpc: "2.0", id: 7, result: { echo: "tools/list", app: "Claude" } });
  const note = await callMcp(mf, tokens.json.access_token, JSON.stringify({ jsonrpc: "2.0", method: "notifications/initialized" }));
  assert.equal(note.status, 202);
  assert.equal((await callMcp(mf, tokens.json.access_token + "x")).status, 401);

  const refreshed = await exchange(mf, { grant_type: "refresh_token", refresh_token: tokens.json.refresh_token, client_id: second.client_id });
  assert.equal(refreshed.status, 200);
  assert.equal((await callMcp(mf, refreshed.json.access_token)).status, 200);
  assert.equal((await callMcp(mf, tokens.json.access_token)).status, 401);
  const reused = await exchange(mf, { grant_type: "refresh_token", refresh_token: tokens.json.refresh_token, client_id: second.client_id });
  assert.equal(reused.json.error, "invalid_grant");

  t.send({ type: "unlink", app: apps[0].id });
  assert.deepEqual((await t.next("apps")).apps, []);
  assert.equal((await callMcp(mf, refreshed.json.access_token)).status, 401);

  const stranger = await tablet(mf, "x".repeat(40));
  assert.equal(stranger.status, 403);
  t.ws.close();
  await mf.dispose();
});

test("offline tablet gives a clear JSON-RPC error; guessing codes is slowed down", async () => {
  const mf = await relay();
  const t = await tablet(mf);
  t.send({ type: "pair" });
  const { code } = await t.next("code");
  const l = await login(mf, code);
  const authCode = new URL(l.post.headers.get("location")).searchParams.get("code");
  const tokens = await exchange(mf, { grant_type: "authorization_code", code: authCode, client_id: l.client_id, redirect_uri: redirect, code_verifier: l.verifier });
  t.ws.close();
  await new Promise((r) => setTimeout(r, 100));
  const json = await (await callMcp(mf, tokens.json.access_token)).json();
  assert.equal(json.id, 7);
  assert.match(json.error.message, /not reachable/);

  let last;
  for (let i = 0; i < 11; i++) last = (await login(mf, "99999" + (i % 10))).post;
  assert.match(await last.text(), /te veel pogingen/);
  await mf.dispose();
});
