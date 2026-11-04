package app.n_zik.compagnon

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Checks the build's generated catalogs and its Android escape semantics. */
class UnescapedStringsTest {

    private companion object {
        /** `build/generated/composeResourcesUnescaped` — the test working dir is the `app` module dir. */
        val generatedRoot = File("build/generated/composeResourcesUnescaped")
    }

    // Keep the build-script copy covered: build scripts cannot call test-source helpers.
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
            .map { m -> m.groupValues[1] to requireNotNull(m.groups["value"]).value }
            .toList()

    // (a) the real generated files

    @Test
    fun `the generated fr updates starting status is unescaped`() {
        val fr = generatedRoot.resolve("values-fr/strings_maintenance.xml")
        assertTrue(fr.isFile, "the generated unescaped French string catalog is missing: ${fr.path} (run :ComposeN-Zik:test — it depends on unescapeStringsXml)")
        // Crowdin values retain Android escaping in source, so the build must unescape them.
        assertEquals("C'est parti…", stringName(fr.readText(), "updates_starting_status"))
    }

    @Test
    fun `the generated fr app subtitle is unescaped`() {
        val fr = generatedRoot.resolve("values-fr/strings_common.xml")
        assertTrue(fr.isFile, "the generated unescaped French string catalog is missing: ${fr.path}")
        assertEquals("L'application compagnon de N-Zik", stringName(fr.readText(), "app_subtitle"))
    }

    @Test
    fun `every values directory is generated and no value still carries an escaped apostrophe`() {
        assertTrue(generatedRoot.isDirectory, "the generated unescaped res dir is missing: ${generatedRoot.path}")
        // `listFiles` takes a two-argument FilenameFilter (dir, name)
        val localization = File("src/main/localization")
        val translations = requireNotNull(localization.resolve("translations").listFiles())
            .filter { it.isDirectory }
        val sources = translations.associateBy { "values-${it.name}" } +
            ("values" to localization.resolve("source"))
        val generated = requireNotNull(generatedRoot
            .listFiles { _, name -> name.startsWith("values") })
            .filter { it.isDirectory }
        assertEquals(sources.size, generated.size, "expected one generated dir per source values* dir")
        val offenders = mutableListOf<String>()
        sources.forEach { (qualifier, source) ->
            val catalogs = requireNotNull(source.listFiles { _, name ->
                name.startsWith("strings_") && name.endsWith(".xml")
            })
            val generatedDir = generatedRoot.resolve(qualifier)
            assertEquals(catalogs.map { it.name }.toSet(), generatedDir.list()?.toSet())
            catalogs.forEach { catalog ->
                val copy = generatedDir.resolve(catalog.name)
                assertTrue(copy.isFile, "missing generated copy: ${copy.path}")
                stringValues(copy.readText()).forEach { (name, value) ->
                    if (value.contains("\\'") || value.contains("\\\"")) {
                        offenders += "${copy.parentFile.name}/${copy.name}/$name = $value"
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "generated values still carry Android escaping: ${offenders.take(10)}")
    }

    // (b) the pure semantics

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
