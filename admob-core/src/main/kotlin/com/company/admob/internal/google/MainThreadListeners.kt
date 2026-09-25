package com.company.admob.internal.google

import com.company.admob.AdError
import com.company.admob.Reward
import com.company.admob.events.AdRevenue
import com.company.admob.events.AdSourceInfo
import com.company.admob.internal.port.BannerListener
import com.company.admob.internal.port.FullScreenAdListener
import com.company.admob.internal.port.NativeAdListener
import com.company.admob.internal.threading.postToMain
import java.util.concurrent.atomic.AtomicBoolean

// Google delivers load and event callbacks on background threads. The adapter routes every one of them through
// these wrappers, which post each call to the main thread (ADR-006). They take no Google types, so they are tested
// on their own (MainThreadPostingTest).

/**
 * Posts every call to [delegate] on the main thread. [onDismissed] and [onFailedToShow] end the show: only the first
 * of them is delivered, and nothing is delivered after it.
 */
internal class MainThreadFullScreenAdListener(
    delegate: FullScreenAdListener,
    private val post: (() -> Unit) -> Unit = ::postToMain,
) : FullScreenAdListener {
    @Volatile
    private var delegate: FullScreenAdListener? = delegate
    private val ended = AtomicBoolean(false)

    override fun onShown() = deliver { onShown() }

    override fun onImpression() = deliver { onImpression() }

    override fun onClicked() = deliver { onClicked() }

    override fun onPaid(revenue: AdRevenue) = deliver { onPaid(revenue) }

    override fun onRewardEarned(reward: Reward) = deliver { onRewardEarned(reward) }

    override fun onDismissed() = end { onDismissed() }

    override fun onFailedToShow(error: AdError) = end { onFailedToShow(error) }

    /** Stops all further delivery. */
    fun clear() {
        ended.set(true)
        delegate = null
    }

    private fun deliver(call: FullScreenAdListener.() -> Unit) {
        if (ended.get()) return
        val target = delegate ?: return
        post { target.call() }
    }

    private fun end(call: FullScreenAdListener.() -> Unit) {
        if (!ended.compareAndSet(false, true)) return
        val target = delegate ?: return
        delegate = null
        post { target.call() }
    }
}

/** Posts every call to [delegate] on the main thread until [clear] is called. */
internal class MainThreadNativeAdListener(
    delegate: NativeAdListener,
    private val post: (() -> Unit) -> Unit = ::postToMain,
) : NativeAdListener {
    @Volatile
    private var delegate: NativeAdListener? = delegate

    override fun onImpression() = deliver { onImpression() }

    override fun onClicked() = deliver { onClicked() }

    override fun onPaid(revenue: AdRevenue) = deliver { onPaid(revenue) }

    fun clear() {
        delegate = null
    }

    private fun deliver(call: NativeAdListener.() -> Unit) {
        val target = delegate ?: return
        post { if (delegate === target) target.call() }
    }
}

/** Posts every call to [delegate] on the main thread until [clear] is called. */
internal class MainThreadBannerListener(
    delegate: BannerListener,
    private val post: (() -> Unit) -> Unit = ::postToMain,
) : BannerListener {
    @Volatile
    private var delegate: BannerListener? = delegate

    override fun onLoaded(source: AdSourceInfo?, isRefresh: Boolean) = deliver { onLoaded(source, isRefresh) }

    override fun onFailedToLoad(error: AdError, retryable: Boolean, isRefresh: Boolean) =
        deliver { onFailedToLoad(error, retryable, isRefresh) }

    override fun onImpression() = deliver { onImpression() }

    override fun onClicked() = deliver { onClicked() }

    override fun onPaid(revenue: AdRevenue) = deliver { onPaid(revenue) }

    fun clear() {
        delegate = null
    }

    private fun deliver(call: BannerListener.() -> Unit) {
        val target = delegate ?: return
        // Re-checked on main, so nothing reaches a banner destroyed while the post was in flight.
        post { if (delegate === target) target.call() }
    }
}
