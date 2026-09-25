package com.company.admob

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The public API must not expose Google SDK types (docs/architecture/02_ARCHITECTURE.md §31). */
class NoGoogleTypesInPublicApiTest {
    @Test
    fun `committed api dump contains no Google SDK types`() {
        val dump = File("api/admob-core.api")
        assertTrue("Missing ${dump.absolutePath}; run ./gradlew :admob-core:apiDump", dump.exists())
        val text = dump.readText()
        listOf("com/google/android/libraries/ads", "com/google/android/ump", "com/google/android/gms/ads").forEach {
            assertFalse("Public API exposes $it", text.contains(it))
        }
    }
}
