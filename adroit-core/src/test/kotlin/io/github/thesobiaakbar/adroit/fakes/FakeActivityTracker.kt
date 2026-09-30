package io.github.thesobiaakbar.adroit.fakes

import android.app.Activity
import io.github.thesobiaakbar.adroit.internal.port.ActivityTracker

/** An [ActivityTracker] the test drives directly. */
internal class FakeActivityTracker(
    override var resumedActivity: Activity? = null,
    override var startedActivityCount: Int = 1,
) : ActivityTracker {
    /** Activities for which [isValidForShow] returns false, for example finishing ones. */
    val invalidActivities = mutableSetOf<Activity>()

    override fun isValidForShow(activity: Activity): Boolean = activity !in invalidActivities
}
