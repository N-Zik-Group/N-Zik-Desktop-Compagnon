package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The drawables are copied from the phone's Android resources: Compose's vector parser cannot resolve an
 * Android reference (`@android:color/…`, `?attr/…`), which crashes the composition when the icon shows.
 */
class DrawableResourcesTest {

    @Test
    fun `no vector drawable references an Android resource or theme attribute`() {
        val dir = File("src/main/composeResources/drawable")
        assertTrue(dir.isDirectory, "drawable folder not found from ${File(".").absolutePath}")
        val offenders = dir.listFiles { file -> file.extension == "xml" }.orEmpty()
            .filter { file -> Regex("=\"[@?]").containsMatchIn(file.readText()) }
            .map { it.name }
        assertTrue(offenders.isEmpty(), "Android references in: $offenders")
    }
}
