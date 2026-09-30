package io.github.thesobiaakbar.adroit.internal.fullscreen

import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.Reward
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.events.AdRevenue
import io.github.thesobiaakbar.adroit.internal.config.EffectivePlacement
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.port.FullScreenAdListener
import io.github.thesobiaakbar.adroit.internal.port.LoadedFullScreenAd
import io.github.thesobiaakbar.adroit.internal.reliability.FrequencyGate
import io.github.thesobiaakbar.adroit.internal.reliability.FullScreenCoordinator
import io.github.thesobiaakbar.adroit.internal.reliability.ShowSession
import io.github.thesobiaakbar.adroit.internal.time.Clock
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
    /** The ad opened, failed to, or the show ended: the loading screen can go (PROMPT 29B). May run more than once. */
    private val onOpened: () -> Unit = {},
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
        onOpened()
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
        onOpened()
        if (session.resolve(FullScreenResult.NotShown(error))) {
            end(AdEvent.NotShown(key, format, adUnitId, now(), error))
        } else {
            log.d { "$format $key: show failure after the show had already ended: ${error.code}" }
        }
    }

    private fun end(event: AdEvent) {
        onOpened()
        coordinator.release(token)
        ad.release()
        events.emit(event)
        onEnded()
    }
}
