@file:OptIn(ExperimentalWasmJsInterop::class, kotlin.io.encoding.ExperimentalEncodingApi::class)

package app.hearth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.io.encoding.Base64

/** A file input (with the camera on phones and tablets), shrunk on a canvas to a small JPEG. */
@Composable
actual fun rememberPhotoPicker(onPhoto: (ByteArray) -> Unit): PhotoPicker {
    val deliver by rememberUpdatedState(onPhoto)
    return remember {
        val handle: (String) -> Unit = { base64 -> runCatching { Base64.decode(base64) }.getOrNull()?.let { deliver(it) } }
        PhotoPicker(
            takePhoto = { pickPhoto(true, PHOTO_MAX_PX, handle) },
            choosePhoto = { pickPhoto(false, PHOTO_MAX_PX, handle) },
        )
    }
}

private fun pickPhoto(camera: Boolean, maxPx: Int, onBase64: (String) -> Unit): Unit = js(
    """{
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        if (camera) input.setAttribute('capture', 'environment');
        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file) return;
            const img = new Image();
            img.onload = () => {
                const scale = Math.min(1, maxPx / Math.max(img.width, img.height));
                const canvas = document.createElement('canvas');
                canvas.width = Math.round(img.width * scale);
                canvas.height = Math.round(img.height * scale);
                canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
                const url = canvas.toDataURL('image/jpeg', 0.8);
                URL.revokeObjectURL(img.src);
                onBase64(url.substring(url.indexOf(',') + 1));
            };
            img.src = URL.createObjectURL(file);
        };
        input.click();
    }""",
)
