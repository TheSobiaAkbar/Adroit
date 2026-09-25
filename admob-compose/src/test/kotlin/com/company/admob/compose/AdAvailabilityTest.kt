package com.company.admob.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdPlacement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdAvailabilityTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val client = TestAdsClient()

    @Before
    fun setUp() {
        client.install()
    }

    @After
    fun tearDown() {
        TestAdsClient.uninstall()
    }

    @Test
    fun `reflects the placement's availability as it changes`() {
        val seen = mutableListOf<AdAvailability>()
        rule.setContent {
            val availability by rememberAdAvailability(LEVEL_END)
            if (seen.lastOrNull() != availability) seen += availability
        }
        rule.waitForIdle()
        for (next in listOf(AdAvailability.Loading, AdAvailability.Ready, AdAvailability.Failed(AdError.NoFill()))) {
            client.interstitial.availabilityFlow.value = next
            rule.waitForIdle()
        }
        assertEquals(
            listOf(
                AdAvailability.Unavailable,
                AdAvailability.Loading,
                AdAvailability.Ready,
                AdAvailability.Failed(AdError.NoFill()),
            ),
            seen,
        )
    }

    @Test
    fun `banner and native placements are Unavailable, and a new placement is followed`() {
        var placement: AdPlacement by mutableStateOf(HOME)
        var current: AdAvailability? = null
        rule.setContent { current = rememberAdAvailability(placement).value }
        rule.waitForIdle()
        assertEquals(AdAvailability.Unavailable, current)
        placement = FEED
        rule.waitForIdle()
        assertEquals(AdAvailability.Unavailable, current)
        client.interstitial.availabilityFlow.value = AdAvailability.Ready
        placement = LEVEL_END
        rule.waitForIdle()
        assertEquals(AdAvailability.Ready, current)
    }
}
