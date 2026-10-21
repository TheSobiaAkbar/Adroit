package io.github.thesobiaakbar.adroit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every public declaration in the library modules has KDoc (00_WORKING_RULES §B.11, PROMPT 27 check 6), where its
 * nullability and threading contract are written down.
 *
 * Explicit API mode makes every public declaration start with `public`, so a line scan finds them: each must follow a
 * KDoc block, with only annotations in between. Overrides inherit their KDoc and don't start with `public`.
 * Properties declared in a primary constructor (lines ending in `,`) are documented by the class's `@property` tags.
 */
class PublicKDocTest {
    @Test
    fun `every public declaration has KDoc`() {
        val undocumented = LIBRARY_MODULES.flatMap { module ->
            val root = File("../$module/src/main/kotlin")
            assertTrue("Missing ${root.absolutePath}", root.isDirectory)
            root.walkTopDown().filter { it.extension == "kt" }.flatMap { undocumentedIn(module, it, root) }.toList()
        }
        assertEquals("Public declarations without KDoc:\n${undocumented.joinToString("\n")}\n", 0, undocumented.size)
    }

    private fun undocumentedIn(module: String, file: File, root: File): List<String> {
        val lines = file.readLines()
        val text = lines.joinToString("\n")
        return lines.indices.filter { i ->
            val line = lines[i].trim()
            line.startsWith("public ") && !line.endsWith(",") && !hasKDoc(lines, i) && !hasPropertyTag(line, text)
        }.map { "$module/${file.relativeTo(root)}:${it + 1}: ${lines[it].trim()}" }
    }

    /** Walks back over annotations (including multi-line ones) and line comments to the end of a KDoc block. */
    private fun hasKDoc(lines: List<String>, declaration: Int): Boolean {
        var i = declaration - 1
        while (i >= 0) {
            val line = lines[i].trim()
            when {
                line.startsWith("@") || line.startsWith("//") -> i--
                line.startsWith(")") -> {
                    while (i >= 0 && !lines[i].trim().startsWith("@")) i--
                    i--
                }
                else -> return line.endsWith("*/")
            }
        }
        return false
    }

    /** A class body property documented by the class's `@property` tag. */
    private fun hasPropertyTag(line: String, text: String): Boolean {
        val name = PROPERTY.find(line)?.groupValues?.get(1) ?: return false
        return text.contains("@property $name ")
    }

    private companion object {
        val LIBRARY_MODULES = listOf(
            "adroit-core", "adroit-views", "adroit-compose", "adroit-testing", "adroit-firebase", "adroit-remote-config",
            "adroit-update",
            "adroit-billing", "adroit-billing-views", "adroit-billing-compose", "adroit-billing-firebase", "adroit-billing-testing",
        )
        val PROPERTY = Regex("""^public (?:val|var) (\w+)""")
    }
}
