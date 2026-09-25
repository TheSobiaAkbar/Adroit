package com.company.admob

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenResultTest {
    @Test
    fun `wasShown is true only for Dismissed`() {
        assertTrue(FullScreenResult.Dismissed(clicked = false).wasShown)
        assertTrue(FullScreenResult.Dismissed(clicked = true).wasShown)
        assertFalse(FullScreenResult.NotShown(AdError.NotReady).wasShown)
    }
}
