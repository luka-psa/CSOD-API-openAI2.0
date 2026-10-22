package app.hearth.core.data

import androidx.room3.Room
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

/** The browser keeps the database in its private file system (OPFS) via a SQLite web worker. */
fun openHearthDatabase(): HearthDatabase =
    Room.databaseBuilder<HearthDatabase>(DATABASE_NAME)
        .setDriver(WebWorkerSQLiteDriver(createSqliteWorker()))
        .build()

@OptIn(ExperimentalWasmJsInterop::class)
// Written as one expression so webpack bundles the worker (and SQLite's WASM) as a worker chunk.
private fun createSqliteWorker(): Worker =
    js("""new Worker(new URL("hearth-sqlite-worker/worker.js", import.meta.url), { type: "module" })""")
