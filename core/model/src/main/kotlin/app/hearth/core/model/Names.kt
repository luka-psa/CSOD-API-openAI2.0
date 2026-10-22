package app.hearth.core.model

import java.util.Locale
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Key used to match names typed or spoken in different ways ("  Melk " == "melk"). */
fun normalizeName(name: String): String =
    name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

/** "melk" -> "Melk"; leaves the rest of the text as the person wrote it. */
fun displayName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").replaceFirstChar { it.titlecase(Locale.ROOT) }
