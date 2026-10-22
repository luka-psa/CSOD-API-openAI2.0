package app.hearth.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hearth.core.data.openHearthDatabase
import app.hearth.core.model.Lang
import app.hearth.di.AppContainer
import app.hearth.ui.HearthScreen
import app.hearth.ui.HomeViewModel
import app.hearth.ui.theme.HearthTheme
import kotlinx.browser.document
import kotlinx.browser.window

/** Browser entry point: same database, tools, parser and screens as the tablet app. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val lang = Lang.fromTag(window.navigator.language)
    val container = AppContainer(openHearthDatabase(), lang, BrowserSettingsStore())
    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!) {
        HearthTheme {
            val viewModel = viewModel { HomeViewModel(container) }
            HearthScreen(viewModel = viewModel, lang = lang, dimmed = false)
        }
    }
}
