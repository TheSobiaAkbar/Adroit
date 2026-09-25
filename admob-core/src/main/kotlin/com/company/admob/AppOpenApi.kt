package com.company.admob

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * App open ads. Shown automatically when the app returns to the foreground, subject to the configured rules
 * (launch count, cooldown, excluded screens, other ads). Obtain it from [AdsClient.appOpen].
 */
public interface AppOpenApi {
    /**
     * Shows an app open ad from a loading/splash screen during a cold start, if one becomes available within
     * [maxWait]. [onComplete] is called exactly once, on the main thread: after the ad is dismissed, or with
     * [FullScreenResult.NotShown] if no ad could be shown in time. Continue to your main screen in both cases.
     */
    public fun showOnColdStart(
        activity: Activity,
        maxWait: Duration = 4.seconds,
        onComplete: (FullScreenResult) -> Unit,
    )

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
