package io.github.thesobiaakbar.adroit.billing.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A lint-style check: a purchase token reaches a log or a string template only through [redact]. Scans every line of
 * `src/main` that mentions a token (`purchaseToken`, `oldPurchaseToken`, ...).
 */
class TokenLeakScanTest {
    private val redacted = Regex("""redact\([^()]*\)""")
    private val templated = Regex("""(?i)\$\{[^}]*purchasetoken|\$\w*purchasetoken""")

    /** The offending lines of [text], as `line: content`. */
    private fun leaks(text: String): List<String> = text.lines().mapIndexedNotNull { i, raw ->
        val line = redacted.replace(raw, "")
        val leak = line.contains("purchasetoken", ignoreCase = true) &&
            (templated.containsMatchIn(line) || Regex("""\blog\(""").containsMatchIn(line))
        if (leak) "${i + 1}: ${raw.trim()}" else null
    }

    @Test
    fun `no purchase token is logged or put in a string template in src_main`() {
        val sources = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("src/main not found from ${File(".").absolutePath}", sources.size > 10)
        val found = sources.flatMap { file -> leaks(file.readText()).map { "${file.name}:$it" } }
        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun `the scan catches leaks and allows redacted tokens`() {
        val bad = """
            log.log(WARN, "token " + purchase.purchaseToken)
            val s = "Purchase(${'$'}{purchase.purchaseToken})"
            val t = "old=${'$'}oldPurchaseToken"
        """.trimIndent()
        assertEquals(3, leaks(bad).size)
        val good = """
            log.log(WARN, "Acknowledging ${'$'}{redact(purchase.purchaseToken)} failed")
            val r = "Replacement(token#=${'$'}{redact(oldPurchaseToken)})"
            acknowledge(purchase.purchaseToken)
            "PurchaseToVerify(purchaseToken=<hidden>)"
        """.trimIndent()
        assertEquals(emptyList<String>(), leaks(good))
    }
}
