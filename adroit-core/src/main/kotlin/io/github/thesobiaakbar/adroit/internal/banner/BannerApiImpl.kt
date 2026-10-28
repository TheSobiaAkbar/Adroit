package io.github.thesobiaakbar.adroit.internal.banner

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.BannerAdHandle
import io.github.thesobiaakbar.adroit.BannerApi
import io.github.thesobiaakbar.adroit.BannerOptions
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.UnavailableReason
import io.github.thesobiaakbar.adroit.internal.HardwareAccelerationCheck
import io.github.thesobiaakbar.adroit.internal.config.LiveConfig
import io.github.thesobiaakbar.adroit.internal.config.bannerAdUnitIds
import io.github.thesobiaakbar.adroit.internal.config.bannerSizeFor
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.init.Readiness
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.port.AdsSdk
import io.github.thesobiaakbar.adroit.internal.reliability.AdUnitRotation
import io.github.thesobiaakbar.adroit.internal.reliability.RetryScheduler
import io.github.thesobiaakbar.adroit.internal.threading.isMainThread
import io.github.thesobiaakbar.adroit.internal.threading.postToMain
import io.github.thesobiaakbar.adroit.internal.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [BannerApi]: creates [BannerAdHandleImpl]s. Keeps no reference to the handles it creates. */
internal class BannerApiImpl(
    private val config: LiveConfig,
    private val adsSdk: AdsSdk,
    private val readiness: Readiness,
    private val retry: RetryScheduler,
    private val events: EventBus,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val log: AdLog,
    private val hardwareAcceleration: HardwareAccelerationCheck = HardwareAccelerationCheck(log),
    private val rotation: AdUnitRotation = AdUnitRotation(),
    private val preloader: BannerPreloader? = null,
    /** The host's ad-free flag: it hides every banner, even before `start()`. */
    private val adFree: StateFlow<Boolean> = MutableStateFlow(false),
) : BannerApi {
    override fun create(activity: Activity, placement: BannerPlacement, options: BannerOptions): BannerAdHandle {
        val onMain = isMainThread()
        if (!onMain) log.e { "banner.create() must be called on the main thread; continuing on it" }
        val current = config.current
        val p = current.placement(placement)
        if (p == null || p.format != AdFormat.BANNER) {
            log.e { "banner.create(): ${placement.key} isn't a configured banner placement" }
            return StaticBannerAdHandle(activity, BannerState.Failed(AdError.InvalidConfiguration("${placement.key} isn't a configured banner placement")))
        }
        if (p.disabledReason == UnavailableReason.DISABLED_BY_CONFIG) {
            log.d { "banner.create(): ${placement.key} is disabled" }
            return StaticBannerAdHandle(activity, BannerState.Hidden(UnavailableReason.DISABLED_BY_CONFIG))
        }
        if (onMain) hardwareAcceleration.warnIfDisabled(activity)
        // A remotely disabled placement still gets a live handle: it's Hidden until the override is lifted.
        val size = p.bannerSizeFor(options.size)
        val handle = BannerAdHandleImpl(
            activity, p, current.bannerAdUnitIds(p, size), size, options, config, adsSdk, readiness, retry, events,
            clock, scope, log, rotation, preloader, adFree,
        )
        if (onMain) handle.start() else postToMain { handle.start() }
        return handle
    }
}

/** A handle that never loads, for an unknown or disabled placement. */
private class StaticBannerAdHandle(activity: Activity, state: BannerState) : BannerAdHandle {
    override val view: View = FrameLayout(activity).apply { visibility = View.GONE }
    override val state: StateFlow<BannerState> = MutableStateFlow(state).asStateFlow()
    override val expectedHeightPx: Int get() = 0

    override fun bindTo(owner: LifecycleOwner) = Unit

    override fun destroy() {
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
