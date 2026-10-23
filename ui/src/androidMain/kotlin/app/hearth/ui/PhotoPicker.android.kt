package app.hearth.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
actual fun rememberPhotoPicker(onPhoto: (ByteArray) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deliver by rememberUpdatedState(onPhoto)
    var pending by remember { mutableStateOf<Uri?>(null) }

    fun handle(uri: Uri?) {
        uri ?: return
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) { runCatching { shrink(context, uri) }.getOrNull() }
            jpeg?.let(deliver)
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) handle(pending) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> handle(uri) }

    return remember {
        PhotoPicker(
            takePhoto = {
                val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                val file = File(dir, "recipe.jpg")
                val uri = FileProvider.getUriForFile(context, context.packageName + ".photos", file)
                pending = uri
                camera.launch(uri)
            },
            choosePhoto = { gallery.launch("image/*") },
        )
    }
}

/** Decodes at a reduced size, turns it upright (EXIF), scales to [PHOTO_MAX_PX] and compresses. */
private fun shrink(context: Context, uri: Uri): ByteArray {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_PX) sample *= 2
    val decoded = resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }!!

    val rotation = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val scale = minOf(1f, PHOTO_MAX_PX.toFloat() / maxOf(decoded.width, decoded.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotation)
    }
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    return ByteArrayOutputStream().use { out ->
        upright.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
        out.toByteArray()
    }
}
