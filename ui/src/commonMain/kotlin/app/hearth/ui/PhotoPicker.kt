package app.hearth.ui

import androidx.compose.runtime.Composable

/**
 * Take a photo with the tablet's camera, or pick one. [onPhoto] gets a JPEG of at most 800 px on the long
 * side (about 80 KB), straightened, so recipe cards stay light on a low-spec tablet.
 * [takePhoto] is null where there is no camera flow (it then falls back to choosing a file).
 */
class PhotoPicker(val takePhoto: (() -> Unit)?, val choosePhoto: () -> Unit)

@Composable
expect fun rememberPhotoPicker(onPhoto: (ByteArray) -> Unit): PhotoPicker

const val PHOTO_MAX_PX = 800
