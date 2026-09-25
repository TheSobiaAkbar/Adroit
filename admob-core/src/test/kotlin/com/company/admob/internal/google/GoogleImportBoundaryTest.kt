package com.company.admob.internal.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Google SDK imports stay inside `internal/google` (architecture §9), and the legacy SDK is never imported. */
class GoogleImportBoundaryTest {
    private val sourceRoot = File("src/main/kotlin")
    private val adapterDir = File(sourceRoot, "com/company/admob/internal/google")

    private fun sources(): List<File> {
        assertTrue("Missing ${sourceRoot.absolutePath}", sourceRoot.isDirectory)
        return sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun violations(files: List<File>, prefixes: List<String>): List<String> = files.flatMap { file ->
        file.readLines().mapIndexedNotNull { index, line ->
            val trimmed = line.trim()
            if (prefixes.any { trimmed.startsWith("import $it") }) "${file.relativeTo(sourceRoot)}:${index + 1}: $trimmed" else null
        }
    }

    @Test
    fun `Next-Gen and UMP imports appear only in the Google adapter`() {
        val outside = sources().filterNot { it.canonicalPath.startsWith(adapterDir.canonicalPath + File.separator) }
        assertEquals(
            emptyList<String>(),
            violations(outside, listOf("com.google.android.libraries.ads", "com.google.android.ump")),
        )
    }

    @Test
    fun `legacy GMA SDK is never imported`() {
        assertEquals(emptyList<String>(), violations(sources(), listOf("com.google.android.gms.ads")))
    }

    @Test
    fun `the adapter exists, so the scan is not vacuous`() {
        assertTrue(File(adapterDir, "NextGenAdsSdk.kt").isFile)
        assertTrue(violations(sources(), listOf("com.google.android.libraries.ads")).isNotEmpty())
    }
}
