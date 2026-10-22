package io.github.thesobiaakbar.adroit.internal.banner

import android.content.Context
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.GoogleSdkEscapeHatch
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.internal.AdFrame
import io.github.thesobiaakbar.adroit.internal.config.EffectivePlacement
import io.github.thesobiaakbar.adroit.internal.config.LiveConfig
import io.github.thesobiaakbar.adroit.internal.config.bannerAdUnitIds
import io.github.thesobiaakbar.adroit.internal.config.bannerSizeFor
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.init.Readiness
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.port.AdsSdk
import io.github.thesobiaakbar.adroit.internal.port.PreloadedBanner
import io.github.thesobiaakbar.adroit.internal.port.RequestExtras
import io.github.thesobiaakbar.adroit.internal.port.SdkLoadResult
import io.github.thesobiaakbar.adroit.internal.reliability.AdUnitRotation
import io.github.thesobiaakbar.adroit.internal.threading.postToMain
import io.github.thesobiaakbar.adroit.internal.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours

/** A preloaded banner handed to a banner being created, with the ad unit it came from. */
internal class PreloadedBannerAd(val banner: PreloadedBanner, val adUnitId: String)

/**
 * Banners loaded ahead of their view (`AdSlots.load` of a banner): at most one per placement, loaded without a view
 * at the window's width, and handed to the next banner created for that placement ([claim]) if it fits. A banner that
 * finds a preload still loading waits for it instead of requesting another. Like every ad profile load, a failed
 * preload isn't retried: the banner then loads its own. Preloads are dropped when ads become unavailable, and after an
 * hour unshown. Main thread only.
 */
internal class BannerPreloader(
    private val appContext: Context,
    private val config: LiveConfig,
    private val adsSdk: AdsSdk,
    private val readiness: Readiness,
    private val events: EventBus,
    private val clock: Clock,
    private val log: AdLog,
    private val rotation: AdUnitRotation,
    scope: CoroutineScope,
) {
    private class Entry(val spec: BannerSizeSpec, val adUnitId: String) {
        var banner: PreloadedBanner? = null
        var loadedAt = 0L
        val loading: Boolean get() = banner == null

        /** Banners waiting for this load, oldest first; each returns whether it took the ad. */
        val waiters = ArrayDeque<(PreloadedBannerAd?) -> Boolean>()
    }

    private val entries = HashMap<String, Entry>()

    init {
        scope.launch {
            readiness.state.collect { if (it is AdsState.Unavailable) clear("ads unavailable: ${it.reason}") }
        }
    }

    /** Loads [placement]'s banner now, unless one is loaded (and fresh) or loading. */
    fun preload(placement: EffectivePlacement) {
        val key = placement.placement.key
        entries[key]?.let { entry ->
            if (entry.loading || !expired(entry)) {
                log.d { "Banner $key: already ${if (entry.loading) "loading" else "loaded"} ahead of its view" }
                return
            }
            drop(key, "expired unshown")
        }
        if (!placement.enabled) return log.d { "Banner $key: not preloaded, it's disabled" }
        if (!readiness.canLoad()) return log.d { "Banner $key: not preloaded, ads can't load yet" }
        val size = placement.bannerSizeFor(null)
        val metrics = appContext.resources.displayMetrics
        // The width a full-width banner has: the window's, known without a view.
        // As wide as the banner's frame will leave it (AdFrame), so the banner takes it.
        val spec = BannerSizeResolver.resolve(size, 0, metrics.density, metrics.widthPixels - 2 * AdFrame.margin(appContext))
        val current = config.current
        val pick = rotation.next(AdFormat.BANNER, current.bannerAdUnitIds(placement, size))
        val entry = Entry(spec, pick.adUnitId)
        entries[key] = entry
        val startedAt = clock.elapsedRealtimeMillis()
        events.emit(AdEvent.LoadRequested(key, AdFormat.BANNER, pick.adUnitId, clock.currentTimeMillis()))
        log.d { "Preloading banner $key (${current.displayAdUnitId(pick.adUnitId)})${pick.describe} at ${spec.widthDp} dp, ${spec.size}" }
        @OptIn(GoogleSdkEscapeHatch::class)
        val extras = RequestExtras(
            placement.placement,
            current.source.advanced.googleRequestCustomizer,
            (spec.size as? BannerSize.Collapsible)?.position,
        )
        adsSdk.preloadBanner(appContext, pick.adUnitId, spec.size, spec.widthDp, extras) { result ->
            postToMain { onResult(key, entry, result, startedAt) }
        }
    }

    private fun onResult(key: String, entry: Entry, result: SdkLoadResult<PreloadedBanner>, startedAt: Long) {
        if (entries[key] !== entry) {
            (result as? SdkLoadResult.Success)?.ad?.destroy()
            return
        }
        val now = clock.currentTimeMillis()
        when (result) {
            is SdkLoadResult.Success -> {
                entry.banner = result.ad
                entry.loadedAt = clock.elapsedRealtimeMillis()
                val latency = entry.loadedAt - startedAt
                events.emit(AdEvent.Loaded(key, AdFormat.BANNER, entry.adUnitId, now, latency, result.source))
                log.i { "Banner $key loaded ahead of its view in $latency ms" }
                while (entry.waiters.isNotEmpty()) {
                    if (entry.waiters.removeFirst()(PreloadedBannerAd(result.ad, entry.adUnitId))) {
                        entries.remove(key)
                        break
                    }
                }
            }
            is SdkLoadResult.Failure -> {
                entries.remove(key)
                events.emit(AdEvent.LoadFailed(key, AdFormat.BANNER, entry.adUnitId, now, result.error, 1, willRetry = false))
                log.w { "Banner $key failed to load ahead of its view: ${result.error.code}; its view loads its own" }
                while (entry.waiters.isNotEmpty()) entry.waiters.removeFirst()(null)
            }
        }
    }

    /**
     * Hands [key]'s preloaded banner to a new banner of [spec] whose ad units are [units], through [onReady]: at once
     * if it's loaded, or when its load completes (null if it fails). [onReady] returns whether it took the ad; one it
     * didn't take stays for the next banner. Returns false when there's no preload that fits: the caller loads its own.
     */
    fun claim(key: String, spec: BannerSizeSpec, units: List<String>, onReady: (PreloadedBannerAd?) -> Boolean): Boolean {
        val entry = entries[key] ?: return false
        if (!fits(entry, spec, units)) {
            drop(key, "it doesn't fit this banner (${entry.spec} vs $spec)")
            return false
        }
        val banner = entry.banner
        if (banner == null) {
            log.d { "Banner $key: waiting for its load ahead of the view" }
            entry.waiters += onReady
            return true
        }
        if (!onReady(PreloadedBannerAd(banner, entry.adUnitId))) return false
        entries.remove(key)
        return true
    }

    /** Whether [key] has a preload loaded or loading. */
    fun has(key: String): Boolean = entries.containsKey(key)

    private fun fits(entry: Entry, spec: BannerSizeSpec, units: List<String>): Boolean =
        entry.spec.size == spec.size && entry.adUnitId in units && (entry.loading || !expired(entry)) &&
            (!spec.isAdaptive || abs(entry.spec.widthDp - spec.widthDp) < BannerSizeResolver.RELOAD_THRESHOLD_DP)

    private fun expired(entry: Entry): Boolean =
        !entry.loading && clock.elapsedRealtimeMillis() - entry.loadedAt > MAX_AGE.inWholeMilliseconds

    private fun drop(key: String, reason: String) {
        val entry = entries.remove(key) ?: return
        log.d { "Banner $key: preload dropped, $reason" }
        entry.banner?.destroy()
        while (entry.waiters.isNotEmpty()) entry.waiters.removeFirst()(null)
    }

    private fun clear(reason: String) {
        entries.keys.toList().forEach { drop(it, reason) }
    }

    private companion object {
        /** An unshown preloaded banner is dropped after this long, like the other formats' ads. */
        val MAX_AGE = 1.hours
    }
}
