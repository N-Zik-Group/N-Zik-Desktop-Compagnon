package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The window icon (`Window(icon = painterResource(Res.drawable.app_icon))` in `Main.kt`) is a
 * committed binary. A corrupt or empty asset would only surface when a human launches the app, so
 * this smoke-checks that the committed file is a non-empty, valid WEBP (RIFF....WEBP).
 */
class AppIconDrawableTest {

    @Test
    fun `window icon drawable is a non-empty valid webp`() {
        val file = File("src/main/composeResources/drawable/app_icon.webp")
        assertTrue(file.isFile, "app_icon.webp not found from ${File(".").absolutePath}")
        val bytes = file.readBytes()
        assertTrue(bytes.size >= 32, "app_icon.webp suspiciously small (${bytes.size} bytes)")
        val header = String(bytes, 0, 4, Charsets.US_ASCII)
        val marker = String(bytes, 8, 4, Charsets.US_ASCII)
        assertTrue(header == "RIFF" && marker == "WEBP", "app_icon.webp is not a WEBP (RIFF....WEBP) file")
    }
}
