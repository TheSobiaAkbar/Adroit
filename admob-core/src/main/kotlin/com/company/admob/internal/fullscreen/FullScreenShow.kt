package com.company.admob.internal.fullscreen

import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.FullScreenResult
import com.company.admob.Reward
import com.company.admob.events.AdEvent
import com.company.admob.events.AdRevenue
import com.company.admob.internal.config.EffectivePlacement
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.FullScreenAdListener
import com.company.admob.internal.port.LoadedFullScreenAd
import com.company.admob.internal.reliability.FrequencyGate
import com.company.admob.internal.reliability.FullScreenCoordinator
import com.company.admob.internal.reliability.ShowSession
import com.company.admob.internal.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * One show of a full-screen ad: turns the SDK's listener calls into events, a frequency record on impression, and
 * exactly one result through [session]. The show ends on dismiss, on a show failure, or when the watchdog times out;
 * ending releases the [FullScreenCoordinator] token and the ad, then calls [onEnded] (the engine reloads). Callbacks
 * that arrive after the end are logged and ignored. For reward formats a dismissal waits [dismissGrace] before
 * ending, so a reward reported just after dismissal still counts. Main thread only.
 */
internal class FullScreenShow(
    private val placement: EffectivePlacement,
    /** The ad unit [ad] was loaded from, for events. */
    private val adUnitId: String,
    private val ad: LoadedFullScreenAd,
    private val token: FullScreenCoordinator.Token,
    private val format: AdFormat,
    private val frequency: FrequencyGate,
    private val coordinator: FullScreenCoordinator,
    private val events: EventBus,
    private val clock: Clock,
    private val log: AdLog,
    private val onReward: (Reward) -> Unit,
    private val scope: CoroutineScope,
    private val dismissGrace: Duration,
    private val onEnded: () -> Unit,
) : FullScreenAdListener {
    lateinit var session: ShowSession<FullScreenResult>
    private var clicked = false
    private var dismissing = false
    private val key get() = placement.placement.key

    private fun now() = clock.currentTimeMillis()

    fun timeoutResult(): FullScreenResult = FullScreenResult.NotShown(AdError.Timeout())

    /** The watchdog resolved the session. */
    fun onTimeout() {
        log.w { "$format $key: the SDK reported neither shown nor failed in time" }
        end(AdEvent.NotShown(key, format, adUnitId, now(), AdError.Timeout()))
    }

    override fun onShown() {
        session.markShown()
        events.emit(AdEvent.Shown(key, format, adUnitId, now()))
    }

    override fun onImpression() {
        frequency.recordShown(placement.frequencyPlacement)
        events.emit(AdEvent.Impression(key, format, adUnitId, now()))
    }

    override fun onClicked() {
        clicked = true
        coordinator.markClicked()
        events.emit(AdEvent.Clicked(key, format, adUnitId, now()))
    }

    override fun onPaid(revenue: AdRevenue) = events.emit(AdEvent.Paid(key, format, adUnitId, now(), revenue, ad.source))

    override fun onRewardEarned(reward: Reward) = onReward(reward)

    override fun onDismissed() {
        if (dismissing) return
        dismissing = true
        // A dismissal means the ad showed, even if onShown was lost: the watchdog is no longer needed.
        session.markShown()
        if (dismissGrace.isPositive()) {
            // Keep the ad and its listener for a late reward callback (see FullScreenFormatStrategy.dismissGrace).
            scope.launch {
                delay(dismissGrace)
                finishDismiss()
            }
        } else {
            finishDismiss()
        }
    }

    private fun finishDismiss() {
        if (session.resolve(FullScreenResult.Dismissed(clicked))) {
            end(AdEvent.Dismissed(key, format, adUnitId, now()))
        } else {
            log.d { "$format $key: dismissed after the show had already ended" }
        }
    }

    override fun onFailedToShow(error: AdError) {
        if (session.resolve(FullScreenResult.NotShown(error))) {
            end(AdEvent.NotShown(key, format, adUnitId, now(), error))
        } else {
            log.d { "$format $key: show failure after the show had already ended: ${error.code}" }
        }
    }

    private fun end(event: AdEvent) {
        coordinator.release(token)
        ad.release()
        events.emit(event)
        onEnded()
    }
}
