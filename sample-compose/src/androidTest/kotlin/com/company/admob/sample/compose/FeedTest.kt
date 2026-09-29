package com.company.admob.sample.compose

import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.company.admob.AdFormat
import com.company.admob.CompanyAdMob
import com.company.admob.events.AdEvent
import com.company.admob.sample.compose.feed.FEED_TAG
import com.company.admob.sample.compose.feed.feedRows
import com.company.admob.sample.compose.ui.Destination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * The feed's native ads: scrolling to the end and back, and rotating, show the same ads (no new native ad request),
 * and leaving the screen leaks nothing. Only requests are compared: a request already in flight when the scrolling
 * starts may still complete during it.
 */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class FeedTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(DetectLeaksAfterTestSuccess())
        .around(AppOpenDisabledRule())
        .around(compose)

    private val device = uiDevice()
    private val nativeRequests = AtomicInteger()
    private val scope = CoroutineScope(Dispatchers.Main)

    @Before
    fun countNativeRequests() {
        scope.launch {
            CompanyAdMob.events
                .filter { it is AdEvent.LoadRequested && it.format == AdFormat.NATIVE }
                .collect { nativeRequests.incrementAndGet() }
        }
    }

    @After
    fun stopCounting() = scope.cancel()

    @Test
    fun scrollingAndRotatingKeepTheSameAds() {
        compose.navigateTo(Destination.Feed)
        // Every ad slot composed once: each loads its ad.
        scrollDownAndUp()
        val before = awaitStableCount()
        try {
            repeat(2) { scrollDownAndUp() }
            repeat(3) { compose.rotateAndBack(device) }
        } finally {
            device.unfreezeRotation()
        }
        SystemClock.sleep(STABLE_MILLIS)
        compose.waitForIdle()
        assertEquals("native ad requests, before and after scrolling and rotating", before, nativeRequests.get())
        compose.navigateTo(Destination.Home)
    }

    private fun scrollDownAndUp() {
        val down = (0..feedRows.lastIndex step 3) + feedRows.lastIndex
        (down + down.reversed()).forEach { row ->
            compose.onNodeWithTag(FEED_TAG).performScrollToIndex(row)
            compose.waitForIdle()
        }
    }

    /** The request count once it hasn't changed for [STABLE_MILLIS], within 30 s. */
    private fun awaitStableCount(): Int {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        var last = nativeRequests.get()
        var stableSince = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_MILLIS)
            val current = nativeRequests.get()
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
