// Web worker behind androidx.sqlite's WebWorkerSQLiteDriver (Room on the web).
// Protocol: see androidx.sqlite.driver.web.WebWorkerSQLiteDriver. Based on the AndroidX example worker,
// with two changes:
//  - storage uses the "opfs-sahpool" VFS, which needs no special server headers (COOP/COEP);
//  - if OPFS is unavailable (old browser, private window, second tab), it falls back to memory.
import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

let sqlite3 = null;
let pool = null;

const databases = new Map(); // databaseId -> oo1.DB
const statements = new Map(); // statementId -> oo1.Stmt
let nextDatabaseId = 1;
let nextStatementId = 1;

const reply = (id, data) => postMessage({ id, data });
const fail = (id, error) => postMessage({ id, error: String(error && error.message ? error.message : error) });

function open(id, { fileName }) {
  try {
    const db = pool ? new pool.OpfsSAHPoolDb('/' + fileName) : new sqlite3.oo1.DB(':memory:', 'c');
    const databaseId = nextDatabaseId++;
    databases.set(databaseId, db);
    reply(id, { databaseId });
  } catch (e) {
    fail(id, e);
  }
}

function prepare(id, { databaseId, sql }) {
  const db = databases.get(databaseId);
  if (!db) return fail(id, 'Invalid database ID: ' + databaseId);
  try {
    const stmt = db.prepare(sql);
    const statementId = nextStatementId++;
    statements.set(statementId, stmt);
    const columnNames = [];
    for (let i = 0; i < stmt.columnCount; i++) columnNames.push(sqlite3.capi.sqlite3_column_name(stmt, i));
    reply(id, {
      statementId,
      parameterCount: sqlite3.capi.sqlite3_bind_parameter_count(stmt),
      columnNames,
    });
  } catch (e) {
    fail(id, e);
  }
}

function step(id, { statementId, bindings }) {
  const stmt = statements.get(statementId);
  if (!stmt) return fail(id, 'Invalid statement ID: ' + statementId);
  try {
    const rows = [];
    const columnTypes = [];
    stmt.reset();
    stmt.clearBindings();
    for (let i = 0; i < bindings.length; i++) stmt.bind(i + 1, bindings[i] === undefined ? null : bindings[i]);
    while (stmt.step()) {
      if (!columnTypes.length) {
        for (let i = 0; i < stmt.columnCount; i++) columnTypes.push(sqlite3.capi.sqlite3_column_type(stmt, i));
      }
      const row = stmt.get([]);
      // Large integers come back as BigInt; the driver reads numbers.
      rows.push(row.map((v) => (typeof v === 'bigint' ? Number(v) : v)));
    }
    reply(id, { rows, columnTypes });
  } catch (e) {
    fail(id, e);
  }
}

// One-way: the driver does not wait for an answer.
function close(_id, { statementId, databaseId }) {
  if (statementId != null && statements.has(statementId)) {
    try { statements.get(statementId).finalize(); } catch (_) { /* already gone */ }
    statements.delete(statementId);
  }
  if (databaseId != null && databases.has(databaseId)) {
    try { databases.get(databaseId).close(); } catch (_) { /* already gone */ }
    databases.delete(databaseId);
  }
}

const handlers = { open, prepare, step, close };

function handle(e) {
  const { id, data } = e.data || {};
  if (!data || !data.cmd) return fail(id, "Invalid request, missing 'cmd'.");
  const handler = handlers[data.cmd];
  if (!handler) return fail(id, "Unknown command: '" + data.cmd + "'.");
  handler(id, data);
}

const queue = [];
let ready = false;
let initError = null;

onmessage = (e) => {
  if (initError) return fail(e.data && e.data.id, initError);
  if (ready) handle(e);
  else queue.push(e);
};

(async () => {
  try {
    sqlite3 = await sqlite3InitModule({ print: () => {}, printErr: () => {} });
    try {
      pool = await sqlite3.installOpfsSAHPoolVfs({ name: 'hearth' });
    } catch (e) {
      console.warn('Hearth: browser storage (OPFS) unavailable, data stays in memory for this tab.', e);
    }
    ready = true;
  } catch (e) {
    initError = 'SQLite failed to start: ' + (e && e.message ? e.message : e);
  }
  while (queue.length) {
    const e = queue.shift();
    if (initError) fail(e.data && e.data.id, initError);
    else handle(e);
  }
})();
