// Hearth relay: gives the family's tablet a fixed HTTPS address for MCP.
//
//   ChatGPT / Claude  --POST /mcp/<secret>-->  this Worker  --WebSocket-->  the tablet (Hearth)
//
// The tablet keeps one outgoing WebSocket open to /connect/<secret>?key=<tablet key>. Each MCP request is
// forwarded over it and the tablet's answer is returned. Nothing is stored except a hash of the tablet key,
// so only the first tablet that connected can serve this household.

const SECRET = /^[A-Za-z0-9_-]{32,128}$/;
const TIMEOUT_MS = 25_000;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const [, kind, secret] = url.pathname.split("/");
    if (!kind) return new Response("Hearth relay", { headers: { "content-type": "text/plain" } });
    if ((kind !== "mcp" && kind !== "connect") || !secret || !SECRET.test(secret)) {
      return new Response("Not found", { status: 404 });
    }
    const id = env.HOUSEHOLD.idFromName(await sha256(secret));
    return env.HOUSEHOLD.get(id).fetch(request);
  },
};

export class Household {
  constructor(state) {
    this.state = state;
    this.waiting = new Map();
  }

  async fetch(request) {
    const url = new URL(request.url);
    return url.pathname.startsWith("/connect/") ? this.connect(request, url) : this.mcp(request);
  }

  /** The tablet connects. The first tablet's key is remembered; others are turned away. */
  async connect(request, url) {
    if (request.headers.get("Upgrade") !== "websocket") return new Response("Expected a WebSocket", { status: 426 });
    const key = url.searchParams.get("key") || "";
    if (!SECRET.test(key)) return new Response("Missing tablet key", { status: 401 });
    const hash = await sha256(key);
    const known = await this.state.storage.get("tabletKey");
    if (known && known !== hash) return new Response("This household belongs to another tablet", { status: 403 });
    if (!known) await this.state.storage.put("tabletKey", hash);

    for (const old of this.state.getWebSockets()) old.close(4000, "replaced by a new connection");
    const [client, server] = Object.values(new WebSocketPair());
    this.state.acceptWebSocket(server);
    return new Response(null, { status: 101, webSocket: client });
  }

  /** An MCP request from ChatGPT or Claude (streamable HTTP, JSON responses). */
  async mcp(request) {
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
      tablet.send(JSON.stringify({ id, body }));
    } catch {
      this.waiting.delete(id);
      return offline(body);
    }
    const reply = await answer;
    if (reply === undefined) return offline(body);
    // Notifications get no answer.
    if (!reply) return new Response(null, { status: 202 });
    return new Response(reply, { headers: { "content-type": "application/json" } });
  }

  webSocketMessage(ws, message) {
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
  return new Response(JSON.stringify(out), { status: 200, headers: { "content-type": "application/json" } });
}

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
