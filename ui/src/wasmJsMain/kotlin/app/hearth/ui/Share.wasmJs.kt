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

@Composable
actual fun rememberCopy(): (String) -> Unit = ::copyText

private fun copyText(text: String): Unit = js(
    """{
        if (navigator.clipboard) { navigator.clipboard.writeText(text).catch(() => {}); }
    }""",
)

@Composable
actual fun rememberSaveFile(): (String, String) -> Unit = ::downloadFile

private fun downloadFile(name: String, text: String): Unit = js(
    """{
        const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
        const a = document.createElement('a');
        a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
    }""",
)
