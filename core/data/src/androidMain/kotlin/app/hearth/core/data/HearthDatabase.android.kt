package app.hearth.core.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver

/** Uses the tablet's built-in SQLite: no extra native library, smaller APK. */
fun openHearthDatabase(context: Context): HearthDatabase =
    Room.databaseBuilder<HearthDatabase>(context.applicationContext, DATABASE_NAME)
        .setDriver(AndroidSQLiteDriver())
        .build()

internal actual val LONG_TRANSACTIONS: Boolean = true
