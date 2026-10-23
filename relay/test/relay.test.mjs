// Runs the real Worker in workerd (Miniflare): a fake tablet connects, an MCP client posts through it.
// Usage: node --test relay/test/relay.test.mjs  (needs `npm install miniflare`)
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { Miniflare } from "miniflare";

const script = readFileSync(new URL("../src/worker.js", import.meta.url), "utf8");
const secret = "s".repeat(40);
const key = "k".repeat(40);

async function relay() {
  return new Miniflare({
    modules: true,
    script,
    compatibilityDate: "2025-09-01",
    durableObjects: { HOUSEHOLD: { className: "Household", useSQLite: true } },
  });
}

const rpc = (id, method) => JSON.stringify({ jsonrpc: "2.0", id, method });

test("offline tablet gives a clear JSON-RPC error", async () => {
  const mf = await relay();
  const res = await mf.dispatchFetch(`http://relay/mcp/${secret}`, { method: "POST", body: rpc(1, "tools/list") });
  const json = await res.json();
  assert.equal(json.id, 1);
  assert.match(json.error.message, /not reachable/);
  await mf.dispose();
});

test("requests go to the tablet and its answer comes back; strangers can't take over", async () => {
  const mf = await relay();
  const res = await mf.dispatchFetch(`http://relay/connect/${secret}?key=${key}`, { headers: { Upgrade: "websocket" } });
  assert.equal(res.status, 101);
  const ws = res.webSocket;
  ws.accept();
  ws.addEventListener("message", (e) => {
    const msg = JSON.parse(e.data);
    if (msg.pong) return;
    const req = JSON.parse(msg.body);
    const body = req.id === undefined ? "" : JSON.stringify({ jsonrpc: "2.0", id: req.id, result: { echo: req.method } });
    ws.send(JSON.stringify({ id: msg.id, body }));
  });

  const answer = await mf.dispatchFetch(`http://relay/mcp/${secret}`, { method: "POST", body: rpc(7, "tools/list") });
  assert.deepEqual(await answer.json(), { jsonrpc: "2.0", id: 7, result: { echo: "tools/list" } });

  const note = await mf.dispatchFetch(`http://relay/mcp/${secret}`, { method: "POST", body: JSON.stringify({ jsonrpc: "2.0", method: "notifications/initialized" }) });
  assert.equal(note.status, 202);

  const stranger = await mf.dispatchFetch(`http://relay/connect/${secret}?key=${"x".repeat(40)}`, { headers: { Upgrade: "websocket" } });
  assert.equal(stranger.status, 403);

  const bad = await mf.dispatchFetch(`http://relay/mcp/short`, { method: "POST", body: rpc(1, "x") });
  assert.equal(bad.status, 404);
  ws.close();
  await mf.dispose();
});
