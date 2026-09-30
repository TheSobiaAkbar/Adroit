package io.github.thesobiaakbar.adroit.internal.port

import android.app.Activity

/** Knows which Activity is in front without leaking it. Main thread only. Implemented in Prompt 06. */
internal interface ActivityTracker {
    /** The resumed Activity, or null. Cleared on pause and destroy. */
    val resumedActivity: Activity?

    /** Number of started Activities; zero means the app is in the background. */
    val startedActivityCount: Int

    /** Whether a full-screen ad can safely be shown over [activity]: not finishing, not destroyed, and resumed. */
    fun isValidForShow(activity: Activity): Boolean
}
