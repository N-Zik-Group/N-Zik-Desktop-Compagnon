package app.n_zik.compagnon.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * The track artwork as an AWT image — shared by the system media surfaces that hand the artwork to
 * the OS (MPRIS writes it as a `file://` PNG, the Windows SMTC streams the PNG bytes).
 */
internal fun ImageBitmap.toBufferedImage(): BufferedImage {
    val pixelMap = toPixelMap()
    val image = BufferedImage(pixelMap.width, pixelMap.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until pixelMap.height) {
        for (x in 0 until pixelMap.width) {
            // Compose's `Color` has no `toArgb()` — build the 0xAARRGGBB word from its components;
            // `PixelMap` is indexed by (x, y), not a flat index
            val c = pixelMap[x, y]
            val argb = ((c.alpha * 255f).roundToInt() shl 24) or
                ((c.red * 255f).roundToInt() shl 16) or
                ((c.green * 255f).roundToInt() shl 8) or
                (c.blue * 255f).roundToInt()
            image.setRGB(x, y, argb)
        }
    }
    return image
}

/** The artwork as PNG bytes (the Windows SMTC thumbnail stream). */
internal fun ImageBitmap.toPngBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    require(ImageIO.write(toBufferedImage(), "png", out)) { "ImageIO.write returned false" }
    return out.toByteArray()
}
