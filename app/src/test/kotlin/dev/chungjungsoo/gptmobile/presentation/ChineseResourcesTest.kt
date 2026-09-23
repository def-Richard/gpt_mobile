package dev.chungjungsoo.gptmobile.presentation

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class ChineseResourcesTest {
    @Test
    fun chineseResourcesCoverAppStringsAndPreserveFormatArguments() {
        val source = resources("values")
        val chinese = resources("values-zh-rCN")
        assertEquals("Untranslated resources", emptySet<String>(), source.keys - chinese.keys)
        val arguments = Regex("%(?:[0-9]+\\$)?[-#+ 0,(]*[0-9]*(?:\\.[0-9]+)?[a-zA-Z]")
        source.forEach { (name, text) ->
            assertEquals(
                "Format arguments for $name",
                arguments.findAll(text.replace("%%", "")).map { it.value }.toSet(),
                arguments.findAll(chinese.getValue(name).replace("%%", "")).map { it.value }.toSet()
            )
        }
    }

    private fun resources(directory: String): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val builder = factory.newDocumentBuilder()
        return File("src/main/res/$directory").listFiles().orEmpty().filter { it.extension == "xml" }.flatMap { file ->
            val children = builder.parse(file).documentElement.childNodes
            (0 until children.length).mapNotNull { index ->
                val element = children.item(index) as? Element ?: return@mapNotNull null
                if (element.tagName !in setOf("string", "plurals") || element.getAttribute("translatable") == "false") return@mapNotNull null
                element.getAttribute("name") to element.textContent
            }
        }.toMap()
    }
}
