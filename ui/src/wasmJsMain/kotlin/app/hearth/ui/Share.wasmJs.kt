@file:OptIn(ExperimentalWasmJsInterop::class)

package app.hearth.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberShare(): (String) -> Unit = ::shareText

private fun shareText(text: String): Unit = js(
    """{
        if (navigator.share) { navigator.share({ text: text }).catch(() => {}); }
        else if (navigator.clipboard) { navigator.clipboard.writeText(text).catch(() => {}); }
    }""",
)
