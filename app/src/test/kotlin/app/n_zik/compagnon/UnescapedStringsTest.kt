package app.n_zik.compagnon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The build-time string unescape (spec `spec-updater`, user report 2026-10-07 — the Android
 * escaping convention `\'` / `\"` / `\\` is visible in the desktop UI because the Compose
 * desktop resource pipeline does not unescape it, unlike aapt2 on Android):
 *
 *  * (a) the REAL generated files — the test task depends on `unescapeStringsXml`
 *    (wired in `app/build.gradle.kts`), so the assertions below run against the copies the
 *    build actually generated (one `strings.xml` per generated values directory, under
 *    `build/generated/composeResourcesUnescaped`): the French `starting` is unescaped, and no
 *    generated value still carries an escaped apostrophe;
 *  * (b) the pure semantics of the unescape — the single left-to-right pass over the value of
 *    each `<string>` element. The build script carries its own copy of this function (build
 *    scripts cannot call test sources); case (a) pins the build's implementation on the real
 *    Crowdin data, and these cases pin the documented semantics.
 */
class UnescapedStringsTest {

    private companion object {
        /** `build/generated/composeResourcesUnescaped` — the test working dir is the `app` module dir. */
        val generatedRoot = File("build/generated/composeResourcesUnescaped")
    }

    /**
     * The semantic reference of the build script's `unescapeAndroidStrings` (single left-to-right
     * pass over the value of each `<string>` element): `\'` → `'`, `\"` → `"`, `\\` → `\`; a
     * trailing backslash or a backslash before an unhandled character is kept as-is; the XML
     * structure around the values is untouched.
     */
    private fun unescapeAndroidStrings(xml: String): String {
        val stringElement = Regex("(<string name=\"[^\"]+\"[^>]*>)(.*?)(</string>)", RegexOption.DOT_MATCHES_ALL)
        return stringElement.replace(xml) { m ->
            val value = m.groupValues[2]
            val out = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                if (value[i] == '\\' && i + 1 < value.length) {
                    when (value[i + 1]) {
                        '\\' -> { out.append('\\'); i += 2 }
                        '\'' -> { out.append('\''); i += 2 }
                        '"' -> { out.append('"'); i += 2 }
                        else -> { out.append(value[i]); i += 1 }
                    }
                } else {
                    out.append(value[i])
                    i += 1
                }
            }
            m.groupValues[1] + out + m.groupValues[3]
        }
    }

    /** The text value of the `<string name="name">…</string>` element (the whole generated files are flat single-line elements). */
    private fun stringName(xml: String, name: String): String =
        Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)
            ?: error("string '$name' not found in the XML")

    /** Every `<string name="…">value</string>` pair of the document, in document order. */
    private fun stringValues(xml: String): List<Pair<String, String>> =
        Regex("<string name=\"([^\"]+)\">(?<value>.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { m -> m.groupValues[1] to m.groups["value"]!!.value }
            .toList()

    // ------------------------------------------------------------------ (a) the real generated files

    @Test
    fun `the generated fr starting is unescaped`() {
        val fr = generatedRoot.resolve("values-fr/strings.xml")
        assertTrue(fr.isFile, "the generated unescaped fr strings.xml is missing: ${fr.path} (run :app:test — it depends on unescapeStringsXml)")
        // The source carries the Android escaping (C\'est parti…); the generated copy must not
        assertEquals("C'est parti…", stringName(fr.readText(), "starting"))
    }

    @Test
    fun `the generated fr app subtitle is unescaped`() {
        val fr = generatedRoot.resolve("values-fr/strings.xml")
        assertTrue(fr.isFile, "the generated unescaped fr strings.xml is missing: ${fr.path}")
        assertEquals("Guide d'accompagnement pour N-Zik", stringName(fr.readText(), "app_subtitle"))
    }

    @Test
    fun `every values directory is generated and no value still carries an escaped apostrophe`() {
        assertTrue(generatedRoot.isDirectory, "the generated unescaped res dir is missing: ${generatedRoot.path}")
        // `listFiles` takes a two-argument FilenameFilter (dir, name)
        val sources = File("src/main/composeResources")
            .listFiles { _, name -> name.startsWith("values") }!!
            .filter { it.isDirectory }
        val generated = generatedRoot
            .listFiles { _, name -> name.startsWith("values") }!!
            .filter { it.isDirectory }
        assertEquals(sources.size, generated.size, "expected one generated dir per source values* dir")
        sources.forEach { source ->
            val copy = generatedRoot.resolve(source.name).resolve("strings.xml")
            assertTrue(copy.isFile, "missing generated copy: ${copy.path}")
        }
        val offenders = mutableListOf<String>()
        generated
            .map { it.resolve("strings.xml") }
            .forEach { file ->
                stringValues(file.readText())
                    .forEach { (name, value) ->
                        if (value.contains("\\'") || value.contains("\\\"")) {
                            offenders += "${file.parentFile.name}/$name = $value"
                        }
                    }
            }
        assertTrue(offenders.isEmpty(), "generated values still carry Android escaping: ${offenders.take(10)}")
    }

    // ---------------------------------------------------------------------- (b) the pure semantics

    @Test
    fun `an escaped apostrophe becomes a plain apostrophe`() {
        assertEquals(
            "<string name=\"x\">App's</string>",
            unescapeAndroidStrings("<string name=\"x\">App\\'s</string>"),
        )
    }

    @Test
    fun `a double backslash becomes a single backslash`() {
        assertEquals(
            "<string name=\"x\">C:\\dir</string>",
            unescapeAndroidStrings("<string name=\"x\">C:\\\\dir</string>"),
        )
    }

    @Test
    fun `an escaped apostrophe in a word`() {
        assertEquals(
            "<string name=\"x\">don't</string>",
            unescapeAndroidStrings("<string name=\"x\">don\\'t</string>"),
        )
    }

    @Test
    fun `a string without escapes is unchanged`() {
        val xml = """
            |<?xml version="1.0" encoding="utf-8"?>
            |<resources>
            |    <string name="plain">Plain value, 42 %1${'$'}d</string>
            |</resources>
        """.trimMargin()
        assertEquals(xml, unescapeAndroidStrings(xml))
    }

    @Test
    fun `an escaped quote is unescaped too`() {
        assertEquals(
            "<string name=\"x\">He said \"hello\"</string>",
            unescapeAndroidStrings("<string name=\"x\">He said \\\"hello\\\"</string>"),
        )
    }

    @Test
    fun `left-to-right semantics an escaped backslash before a quote keeps the quote escaped`() {
        // Source: backslash, backslash, quote. Single left-to-right pass: the first backslash is
        // unescaped to a single one, and the quote is left alone (it is NOT a \\' pair).
        assertEquals(
            "<string name=\"x\">\\'</string>",
            unescapeAndroidStrings("<string name=\"x\">\\\\'</string>"),
        )
    }

    @Test
    fun `a trailing backslash is kept`() {
        // A single backslash at the end of the value has nothing after it — kept as-is.
        assertEquals(
            "<string name=\"x\">trailing\\</string>",
            unescapeAndroidStrings("<string name=\"x\">trailing\\</string>"),
        )
    }

    @Test
    fun `the xml structure around the values is untouched`() {
        val xml = """
            |<?xml version="1.0" encoding="utf-8"?>
            |<resources>
            |    <string name="a">C\'est</string>
            |    <string name="b">no escape</string>
            |</resources>
        """.trimMargin()
        val unescaped = unescapeAndroidStrings(xml)
        assertTrue(unescaped.contains("<string name=\"a\">C'est</string>"), unescaped)
        assertFalse(unescaped.contains("\\'"), "no escaped apostrophe may survive: $unescaped")
        assertTrue(unescaped.contains("<string name=\"b\">no escape</string>"), unescaped)
    }
}
