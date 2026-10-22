package app.hearth.core.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Key used to match names typed or spoken in different ways ("  Melk " == "melk"). */
fun normalizeName(name: String): String =
    name.trim().lowercase().replace(Regex("\\s+"), " ")

/** "melk" -> "Melk"; leaves the rest of the text as the person wrote it. */
fun displayName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").replaceFirstChar { it.titlecase() }
