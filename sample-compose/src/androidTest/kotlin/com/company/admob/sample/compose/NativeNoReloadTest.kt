package com.company.admob.sample.compose

import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.company.admob.sample.compose.ui.Destination
import com.company.admob.sample.compose.ui.screens.LOAD_COUNTER_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * On the Native screen, three rotations re-render the same ads: no new native ad request. Only requests are compared:
 * a request already in flight when the rotations start may still complete during them.
 */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class NativeNoReloadTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AppOpenDisabledRule()).around(compose)

    private val device = uiDevice()

    @Test
    fun rotationDoesNotReloadNativeAds() {
        compose.navigateTo(Destination.Native)
        // Loads (and pool refills) triggered by opening the screen finish first; offline, the count stays at 0.
        val before = awaitStableCounter()
        try {
            repeat(3) { compose.rotateAndBack(device) }
        } finally {
            device.unfreezeRotation()
        }
        SystemClock.sleep(STABLE_MILLIS)
        compose.waitForIdle()
        assertEquals("native ad requests, before and after rotating", requests(before), requests(counter()))
    }

    private fun counter(): String = compose.onNodeWithTag(LOAD_COUNTER_TAG).text()

    /** "Native loads: 31 requested, 30 loaded" → 31. */
    private fun requests(counter: String): Int =
        checkNotNull(Regex("""(\d+) requested""").find(counter)) { counter }.groupValues[1].toInt()

    /** The counter once it hasn't changed for [STABLE_MILLIS], within 30 s. */
    private fun awaitStableCounter(): String {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        var last = counter()
        var stableSince = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_MILLIS)
            compose.waitForIdle()
            val current = counter()
            if (current != last) {
                last = current
                stableSince = SystemClock.elapsedRealtime()
            } else if (SystemClock.elapsedRealtime() - stableSince >= STABLE_MILLIS) {
                return current
            }
        }
        return last
    }

    private companion object {
        const val STABLE_MILLIS = 5_000L
        const val POLL_MILLIS = 250L
    }
}
