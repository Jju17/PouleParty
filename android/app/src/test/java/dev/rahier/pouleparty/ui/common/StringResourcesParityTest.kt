package dev.rahier.pouleparty.ui.common

import dev.rahier.pouleparty.data.ApiErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourcesParityTest {

    private val resDir = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private fun entries(locale: String, tag: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resDir, "$locale/strings.xml"))
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `every string exists in French and Dutch`() {
        val reference = entries("values", "string").keys
        for (locale in listOf("values-fr", "values-nl")) {
            val keys = entries(locale, "string").keys
            assertEquals("missing in $locale", emptySet<String>(), reference - keys)
            assertEquals("extra in $locale", emptySet<String>(), keys - reference)
        }
    }

    @Test
    fun `every plural exists in French and Dutch`() {
        val reference = entries("values", "plurals").keys
        for (locale in listOf("values-fr", "values-nl")) {
            assertEquals(reference, entries(locale, "plurals").keys)
        }
    }

    @Test
    fun `no visible text uses an em dash`() {
        for (locale in listOf("values", "values-fr", "values-nl")) {
            val offenders = (entries(locale, "string") + entries(locale, "plurals")).filterValues { '\u2014' in it }
            assertTrue("em dash in $locale: ${offenders.keys}", offenders.isEmpty())
        }
    }

    @Test
    fun `every server error code has a message`() {
        val names = entries("values", "string").keys
        ApiErrorCode.entries.forEach { code ->
            val expected = "api_error_" + code.name.lowercase()
            assertTrue("no string for $code", expected in names)
        }
    }
}
