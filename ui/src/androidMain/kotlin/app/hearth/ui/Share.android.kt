package app.hearth.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberShare(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
actual fun rememberCopy(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Hearth", text))
        }
    }
}

/** Written to Hearth's own cache, then shared as a file (save it to Drive, mail it, …). */
@Composable
actual fun rememberSaveFile(): (String, String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { name, text ->
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, name).apply { writeText(text) }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".photos", file)
            val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
}
