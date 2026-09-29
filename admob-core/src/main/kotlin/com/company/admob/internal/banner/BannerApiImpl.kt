package com.company.admob.internal.banner

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.BannerAdHandle
import com.company.admob.BannerApi
import com.company.admob.BannerOptions
import com.company.admob.BannerPlacement
import com.company.admob.BannerState
import com.company.admob.UnavailableReason
import com.company.admob.internal.HardwareAccelerationCheck
import com.company.admob.internal.config.LiveConfig
import com.company.admob.internal.config.bannerAdUnitIds
import com.company.admob.internal.config.bannerSizeFor
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.init.Readiness
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.AdsSdk
import com.company.admob.internal.reliability.AdUnitRotation
import com.company.admob.internal.reliability.RetryScheduler
import com.company.admob.internal.threading.isMainThread
import com.company.admob.internal.threading.postToMain
import com.company.admob.internal.time.Clock
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
            clock, scope, log, rotation, preloader,
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
