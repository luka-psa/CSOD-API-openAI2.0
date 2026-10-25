package app.hearth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale

// index.html makes navigator.languages answer window.__customLocale when it is set.
@OptIn(ExperimentalWasmJsInterop::class)
private fun setCustomLocale(tag: String?): Unit = js("{ window.__customLocale = tag; }")

actual object LocalAppLocale {
    private val chosen = staticCompositionLocalOf { Locale.current.toLanguageTag() }

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        setCustomLocale(value)
        return chosen.provides(value ?: Locale.current.toLanguageTag())
    }
}
