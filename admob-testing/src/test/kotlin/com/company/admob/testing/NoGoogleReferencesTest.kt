package com.company.admob.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * admob-testing must never reference the Google Mobile Ads or UMP SDKs: hosts use it to test without Google. Scans
 * the module's main sources (Gradle runs unit tests from the module directory).
 */
class NoGoogleReferencesTest {
    private val forbidden = listOf(
        "com.google.android.libraries.ads",
        "com.google.android.gms.ads",
        "com.google.android.ump",
        "MobileAds",
    )

    @Test
    fun `main sources don't reference Google's ads or consent SDKs`() {
        val sources = File("src/main").walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "xml") }.toList()
        assertTrue("no sources found under ${File("src/main").absolutePath}", sources.any { it.extension == "kt" })
        val hits = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                forbidden.firstOrNull { it in line }?.let { "${file.path}:${index + 1}: $it" }
            }
        }
        assertEquals(emptyList<String>(), hits)
    }
}
