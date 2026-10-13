package app.n_zik.compagnon

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * The installer and the launcher both start the app via `mainClass = "app.n_zik.compagnon.MainKt"`
 * (story 13 packaging, `compose.desktop.application.mainClass`). This checks that entry point still
 * resolves to an existing class with a `main` method, so a rename or move fails the build instead of
 * surfacing only when someone runs the install.
 */
class MainClassResolutionTest {

    @Test
    fun `installer main class resolves to an existing entry point`() {
        val mainClass = "app.n_zik.compagnon.MainKt"
        val clazz = Class.forName(mainClass)
        val main = clazz.declaredMethods.firstOrNull { it.name == "main" && it.parameterTypes.size == 1 }
        assertNotNull(main, "no main(String[]) entry point in $mainClass")
    }
}
