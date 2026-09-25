package com.company.admob.internal.google

import android.os.Looper
import com.company.admob.AdError
import com.company.admob.Reward
import com.company.admob.events.AdRevenue
import com.company.admob.events.AdSourceInfo
import com.company.admob.events.RevenuePrecision
import com.company.admob.internal.callbacks.OnceCallback
import com.company.admob.internal.port.BannerListener
import com.company.admob.internal.port.FullScreenAdListener
import com.company.admob.internal.port.NativeAdListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.concurrent.thread

/**
 * Google calls back on background threads. The adapter's wrappers must deliver every call on the main looper,
 * posted rather than synchronous.
 */
@RunWith(RobolectricTestRunner::class)
class MainThreadPostingTest {
    private val calls = mutableListOf<String>()
    private val threads = mutableListOf<Boolean>()

    private fun record(name: String) {
        calls += name
        threads += Looper.myLooper() == Looper.getMainLooper()
    }

    private val fullScreen = object : FullScreenAdListener {
        override fun onShown() = record("shown")
        override fun onImpression() = record("impression")
        override fun onClicked() = record("clicked")
        override fun onPaid(revenue: AdRevenue) = record("paid")
        override fun onRewardEarned(reward: Reward) = record("reward")
        override fun onDismissed() = record("dismissed")
        override fun onFailedToShow(error: AdError) = record("failed")
    }

    private val native = object : NativeAdListener {
        override fun onImpression() = record("impression")
        override fun onClicked() = record("clicked")
        override fun onPaid(revenue: AdRevenue) = record("paid")
    }

    private val banner = object : BannerListener {
        override fun onLoaded(source: AdSourceInfo?, isRefresh: Boolean) = record("loaded")
        override fun onFailedToLoad(error: AdError, retryable: Boolean, isRefresh: Boolean) = record("failed")
        override fun onImpression() = record("impression")
        override fun onClicked() = record("clicked")
        override fun onPaid(revenue: AdRevenue) = record("paid")
    }

    private val revenue = AdRevenue(1, "USD", RevenuePrecision.PRECISE)

    private fun onBackgroundThread(block: () -> Unit) {
        var failure: Throwable? = null
        thread { runCatching(block).onFailure { failure = it } }.join()
        failure?.let { throw it }
    }

    private fun runMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `full-screen events from a background thread run on the main looper`() {
        val listener = MainThreadFullScreenAdListener(fullScreen)
        onBackgroundThread {
            listener.onShown()
            listener.onImpression()
            listener.onClicked()
            listener.onPaid(revenue)
            listener.onRewardEarned(Reward("coins", 1))
            listener.onDismissed()
        }
        assertEquals("delivery must be posted, not synchronous", emptyList<String>(), calls)

        runMainLooper()

        assertEquals(listOf("shown", "impression", "clicked", "paid", "reward", "dismissed"), calls)
        assertTrue(threads.all { it })
    }

    @Test
    fun `a show ends once, and nothing is delivered after it`() {
        val listener = MainThreadFullScreenAdListener(fullScreen)
        onBackgroundThread {
            listener.onFailedToShow(AdError.ShowFailed())
            listener.onDismissed()
            listener.onImpression()
        }
        runMainLooper()
        assertEquals(listOf("failed"), calls)
    }

    @Test
    fun `cleared full-screen listener receives nothing`() {
        val listener = MainThreadFullScreenAdListener(fullScreen)
        listener.clear()
        onBackgroundThread { listener.onShown() }
        runMainLooper()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `native events from a background thread run on the main looper`() {
        val listener = MainThreadNativeAdListener(native)
        onBackgroundThread {
            listener.onImpression()
            listener.onClicked()
            listener.onPaid(revenue)
        }
        runMainLooper()
        assertEquals(listOf("impression", "clicked", "paid"), calls)
        assertTrue(threads.all { it })
    }

    @Test
    fun `banner events run on the main looper, and none arrive after clear even if already posted`() {
        val listener = MainThreadBannerListener(banner)
        onBackgroundThread {
            listener.onLoaded(null, isRefresh = false)
            listener.onImpression()
        }
        runMainLooper()
        assertEquals(listOf("loaded", "impression"), calls)
        assertTrue(threads.all { it })

        onBackgroundThread { listener.onClicked() }
        listener.clear() // the banner is destroyed while the post is in flight
        runMainLooper()
        assertEquals(listOf("loaded", "impression"), calls)
    }

    @Test
    fun `load results from a background thread are delivered once on the main looper`() {
        val results = mutableListOf<String>()
        val onMain = mutableListOf<Boolean>()
        val once = OnceCallback<String>({
            results += it
            onMain += Looper.myLooper() == Looper.getMainLooper()
        })
        onBackgroundThread {
            once("first")
            once("second")
        }
        assertEquals(emptyList<String>(), results)
        runMainLooper()
        assertEquals(listOf("first"), results)
        assertEquals(listOf(true), onMain)
    }
}
