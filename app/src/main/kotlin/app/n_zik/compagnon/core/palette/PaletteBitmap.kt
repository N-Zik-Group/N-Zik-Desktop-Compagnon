package app.n_zik.compagnon.core.palette

import androidx.compose.ui.graphics.ImageBitmap

/**
 * The ARGB pixels of an image, row-major: what the phone's palette code reads from an Android `Bitmap`
 * (`getPixels`). Built from a cover with [toPaletteBitmap]; tests build it directly.
 */
class PaletteBitmap(val pixels: IntArray, val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0 && pixels.size >= width * height) { "Invalid image" }
    }
}

/** The pixels of a decoded cover (`ImageBitmap.readPixels`). */
fun ImageBitmap.toPaletteBitmap(): PaletteBitmap {
    val buffer = IntArray(width * height)
    readPixels(buffer)
    return PaletteBitmap(buffer, width, height)
}

/** `Palette.from(bitmap)`. */
fun Palette.Companion.from(bitmap: PaletteBitmap): Palette.Builder = from(bitmap.pixels, bitmap.width, bitmap.height)
