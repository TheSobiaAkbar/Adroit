package com.company.admob.sample.mixed

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.test.rule.GrantPermissionRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.company.admob.AdFormat
import com.company.admob.CompanyAdMob
import com.company.admob.events.AdEvent
import com.company.admob.sample.mixed.feed.FEED_TAG
import com.company.admob.sample.mixed.feed.feedRows
import com.company.admob.sample.mixed.styles.NativeStylesActivity
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
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * The mixed app's screens, whichever toolkit draws them: XML Fragments, the Compose feed in its Fragment, the Compose
 * Details screen embedding XML, and the native styles. Each survives rotation, the Compose feed keeps its native ads
 * and banners (no new request) across scrolling and rotation, and LeakCanary finds nothing retained afterwards. Needs
 * network for the ads, but passes when none load.
 */
@RunWith(AndroidJUnit4::class)
class MixedAppTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    // The notification permission (Android 13+): without it, LeakCanary opens its permission screen over the app as
    // soon as it counts retained objects (old screens not collected yet), and the Compose rule loses the app.
    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(
            if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else RuleChain.emptyRuleChain(),
        )
        .around(DetectLeaksAfterTestSuccess())
        .around(AppOpenDisabledRule())
        .around(compose)

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** Native and banner ad requests. */
    private val adRequests = AtomicInteger()
    private val scope = CoroutineScope(Dispatchers.Main)

    @Before
    fun countAdRequests() {
        scope.launch {
            CompanyAdMob.events
                .filter { it is AdEvent.LoadRequested && (it.format == AdFormat.NATIVE || it.format == AdFormat.BANNER) }
                .collect { adRequests.incrementAndGet() }
        }
    }

    @After
    fun stopCounting() = scope.cancel()

    @Test
    fun everyScreenSurvivesRotation() {
        try {
            listOf(R.id.nav_home, R.id.nav_feed, R.id.nav_settings).forEach { tab ->
                onView(withId(tab)).perform(click())
                rotateAndBack()
            }
            onView(withId(R.id.nav_home)).perform(click())
            // The other Activities, each rotated and closed.
            ActivityScenario.launch(DetailsActivity::class.java).use { rotateAndBack() }
            ActivityScenario.launch(NativeStylesActivity::class.java).use { rotateAndBack() }
        } finally {
            device.unfreezeRotation()
        }
    }

    @Test
    fun theComposeFeedKeepsItsAds() {
        onView(withId(R.id.nav_feed)).perform(click())
        compose.waitForIdle()
        scrollDownAndUp() // every ad slot composed once: each loads its ad
        val before = awaitStableCount()
        try {
            repeat(2) { scrollDownAndUp() }
            repeat(3) { rotateAndBack() }
        } finally {
            device.unfreezeRotation()
        }
        SystemClock.sleep(STABLE_MILLIS)
        compose.waitForIdle()
        assertEquals("ad requests, before and after scrolling and rotating", before, adRequests.get())
        onView(withId(R.id.nav_home)).perform(click())
    }

    private fun rotateAndBack() {
        device.setOrientationLeft()
        device.waitForIdle()
        compose.waitForIdle()
        device.setOrientationNatural()
        device.waitForIdle()
        compose.waitForIdle()
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
        var last = adRequests.get()
        var stableSince = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_MILLIS)
            val current = adRequests.get()
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

/**
 * Turns automatic app open ads off for the test, before its Activity starts: every test after the first brings the
 * process back to the foreground, and an app open ad would cover the app.
 */
class AppOpenDisabledRule : ExternalResource() {
    override fun before() = setEnabled(false)

    override fun after() = setEnabled(true)

    private fun setEnabled(enabled: Boolean) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { CompanyAdMob.client.appOpen.setEnabled(enabled) }
    }
}
