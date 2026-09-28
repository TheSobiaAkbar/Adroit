package com.company.admob

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The public API must not expose Google Mobile Ads or UMP types (docs/architecture/02_ARCHITECTURE.md §31), in any
 * library module. admob-firebase's Firebase types are allowed (ADR-017); `GoogleRequestCustomizer`, the one escape
 * hatch, takes its builder as `Any`, so it adds none.
 */
class NoGoogleTypesInPublicApiTest {
    @Test
    fun `committed api dumps contain no Google SDK types`() {
        LIBRARY_MODULES.forEach { module ->
            val dump = File("../$module/api/$module.api")
            assertTrue("Missing ${dump.absolutePath}; run ./gradlew :$module:apiDump", dump.exists())
            val text = dump.readText()
            listOf("com/google/android/libraries/ads", "com/google/android/ump", "com/google/android/gms/ads").forEach {
                assertFalse("$module's public API exposes $it", text.contains(it))
            }
        }
    }

    private companion object {
        val LIBRARY_MODULES = listOf("admob-core", "admob-views", "admob-compose", "admob-testing", "admob-firebase")
    }
}
