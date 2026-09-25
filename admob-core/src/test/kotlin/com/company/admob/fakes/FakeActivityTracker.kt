package com.company.admob.fakes

import android.app.Activity
import com.company.admob.internal.port.ActivityTracker

/** An [ActivityTracker] the test drives directly. */
internal class FakeActivityTracker(
    override var resumedActivity: Activity? = null,
    override var startedActivityCount: Int = 1,
) : ActivityTracker {
    /** Activities for which [isValidForShow] returns false, for example finishing ones. */
    val invalidActivities = mutableSetOf<Activity>()

    override fun isValidForShow(activity: Activity): Boolean = activity !in invalidActivities
}
