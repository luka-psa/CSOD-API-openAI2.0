package app.hearth.core.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/** For host tests: a fresh, empty database in memory. */
fun inMemoryHearthDatabase(): HearthDatabase =
    Room.inMemoryDatabaseBuilder<HearthDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

internal actual val LONG_TRANSACTIONS: Boolean = true
