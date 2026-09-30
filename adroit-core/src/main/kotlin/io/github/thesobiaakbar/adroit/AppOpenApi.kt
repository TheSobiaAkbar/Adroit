package io.github.thesobiaakbar.adroit

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * App open ads. Shown automatically when the app returns to the foreground, subject to the configured rules
 * (launch count, cooldown, excluded screens, other ads). Obtain it from [AdsClient.appOpen].
 */
public interface AppOpenApi {
    /**
     * Shows an app open ad from a loading/splash screen during a cold start, if one becomes available within
     * [maxWait]. [onResult] is called exactly once, on the main thread: after the ad is dismissed, or with
     * [FullScreenResult.NotShown] if no ad could be shown in time. Continue to your main screen in both cases.
     *
     * If the content becomes ready before the ad, don't keep the user waiting: keep [maxWait] short. An ad that
     * loads too late is kept for a later foreground. If [activity] is paused or destroyed during the wait (for
     * example on rotation), the result is `NotShown(ActivityUnavailable)`, and the recreated screen may call again.
     * A call made while another is still waiting or showing completes at once with `NotShown(AlreadyShowing)`.
     * The configured excluded Activities don't apply here: this [activity] is the one you chose to show over.
     */
    public fun showOnColdStart(
        activity: Activity,
        maxWait: Duration = 4.seconds,
        onResult: (FullScreenResult) -> Unit,
    )

    /**
     * [showOnColdStart] with the wait in milliseconds, for Java callers, who can't pass a [Duration] (ADR-013).
     * Kotlin code should use the [Duration] form.
     */
    public fun showOnColdStart(activity: Activity, maxWaitMillis: Long, onResult: (FullScreenResult) -> Unit) {
        showOnColdStart(activity, maxWaitMillis.milliseconds, onResult)
    }

    /**
     * Skips the next foreground event. Call it before leaving the app on purpose, for example for a purchase flow,
     * share sheet, picker, or sign-in, so returning doesn't show an app open ad.
     */
    public fun skipNextForeground()

    /** Suppresses app open ads while [owner] is at least STARTED. Released automatically when it's destroyed. */
    public fun suppressWhile(owner: LifecycleOwner)

    /** Turns automatic app open ads on or off at runtime. */
    public fun setEnabled(enabled: Boolean)

    /** Availability of a cached app open ad. */
    public val availability: StateFlow<AdAvailability>
}
