package com.company.admob.events

import com.company.admob.AdFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdEventTest {
    private val unit = "ca-app-pub-1234567890123456/9876543210"
    private val event = AdEvent.Impression("level_complete", AdFormat.INTERSTITIAL, unit, 42)

    @Test
    fun `toLogString redacts ad unit ids when asked`() {
        val redacted = event.toLogString(redactAdUnitIds = true)
        assertFalse(redacted, redacted.contains(unit))
        assertTrue(redacted, redacted.contains("adUnitId=…3210"))
    }

    @Test
    fun `toLogString keeps ad unit ids otherwise`() {
        assertTrue(event.toLogString(redactAdUnitIds = false).contains(unit))
    }

    @Test
    fun `library-wide events carry no placement`() {
        val warning = AdEvent.ConfigurationWarning("w", 1)
        assertEquals(null, warning.placementKey)
        assertEquals(null, warning.format)
    }
}
