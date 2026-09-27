package com.company.admob.internal.fullscreen

import com.company.admob.AdAvailability
import com.company.admob.AdFormat
import com.company.admob.config.RetryPolicy
import com.company.admob.internal.port.LoadedFullScreenAd
import com.company.admob.internal.reliability.Cached
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

/**
 * The cache for one (format, effective ad unit list). Placements that resolve to the same list share a slot, so they
 * share one cached ad, one load and one rotation position. Main thread only, except [availability] and [expiresAtElapsed], which are
 * safe to read from any thread.
 */
internal class FullScreenSlot(
    val format: AdFormat,
    val adUnitIds: List<String>,
    val retryPolicy: RetryPolicy,
    val ttl: Duration,
) {
    /** Retry-scheduler key, for example `INTERSTITIAL:ca-app-pub-…`. */
    val key: String = "$format:" + adUnitIds.joinToString(",")

    /** The ad unit of the latest load request, and so of the cached ad. The first ID until the first load. */
    var adUnitId: String = adUnitIds.first()

    val availability = SlotAvailability()

    var cached: Cached<LoadedFullScreenAd>? = null
        private set

    /** When [cached] expires, or [Long.MAX_VALUE] when nothing is cached. For off-main `isReady` checks. */
    @Volatile
    var expiresAtElapsed: Long = Long.MAX_VALUE
        private set

    var loading: Boolean = false

    /** Consecutive failed loads; reset by a success or an explicit request. */
    var failedAttempts: Int = 0

    /** Bumped whenever in-flight work must be ignored (clear, block); load results carry the value they started with. */
    var generation: Int = 0

    var expiryJob: Job? = null

    fun store(value: Cached<LoadedFullScreenAd>) {
        cached = value
        expiresAtElapsed = value.expiresAtElapsed
    }

    /** Removes and returns the cached ad, cancelling its expiry job. The caller owns the ad afterwards. */
    fun take(): LoadedFullScreenAd? {
        expiryJob?.cancel()
        expiryJob = null
        expiresAtElapsed = Long.MAX_VALUE
        return cached?.value.also { cached = null }
    }
}

/**
 * A slot's availability, mirrored into the per-placement flows attached to it. The engine hands the host those
 * placement flows, which outlive slots: when a remote override moves a placement to another ad unit or disables it,
 * the flow is re-attached (or set to Disabled) instead of being replaced. [value] is written on the main thread and
 * readable from any thread.
 */
internal class SlotAvailability {
    private val mirrors = mutableListOf<MutableStateFlow<AdAvailability>>()

    @Volatile
    var value: AdAvailability = AdAvailability.Unavailable
        set(value) {
            field = value
            mirrors.forEach { it.value = value }
        }

    fun attach(flow: MutableStateFlow<AdAvailability>) {
        if (flow !in mirrors) mirrors += flow
        flow.value = value
    }

    fun detach(flow: MutableStateFlow<AdAvailability>) {
        mirrors -= flow
    }
}
