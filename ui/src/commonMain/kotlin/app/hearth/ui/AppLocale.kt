package app.hearth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import app.hearth.core.model.Lang

/**
 * Makes the app's own language win over the device's, so the strings follow the setting in Instellingen.
 * Each platform tells its resource lookup which locale to use.
 */
expect object LocalAppLocale {
    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

/** The app's language, for the few places that show the choice itself. */
val LocalLang = androidx.compose.runtime.staticCompositionLocalOf { Lang.NL }

/** Flemish Dutch, or English. */
val Lang.localeTag: String get() = if (this == Lang.NL) "nl-BE" else "en"

/** What the recognizer listens for. */
val Lang.speechTag: String get() = if (this == Lang.NL) "nl-BE" else "en-US"
