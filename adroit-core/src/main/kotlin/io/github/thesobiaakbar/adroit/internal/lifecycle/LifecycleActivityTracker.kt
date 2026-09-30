package io.github.thesobiaakbar.adroit.internal.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.github.thesobiaakbar.adroit.internal.port.ActivityTracker

/**
 * [ActivityTracker] over `Application.ActivityLifecycleCallbacks`. Callbacks arrive on the main thread, and every
 * reader is main-thread confined (ADR-006).
 *
 * [resumedActivity] is a strong reference on purpose: it's set in `onActivityResumed` and cleared in
 * `onActivityPaused` and `onActivityDestroyed`. Every Activity is paused before it's destroyed, so the reference
 * never outlives the Activity's resumed period, and a `WeakReference` would add nothing but GC-dependent behavior.
 */
internal class LifecycleActivityTracker : ActivityTracker, Application.ActivityLifecycleCallbacks {
    override var resumedActivity: Activity? = null
        private set

    override var startedActivityCount: Int = 0
        private set

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    fun unregister(application: Application) {
        application.unregisterActivityLifecycleCallbacks(this)
        resumedActivity = null
        startedActivityCount = 0
    }

    /** Not finishing, not destroyed, and resumed: `Lifecycle` ≥ RESUMED for a `LifecycleOwner`, else tracked here. */
    override fun isValidForShow(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        return lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: (resumedActivity === activity)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity === activity) resumedActivity = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (resumedActivity === activity) resumedActivity = null
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
    }

    override fun onActivityStopped(activity: Activity) {
        // Registration can happen after an Activity started; never go negative.
        if (startedActivityCount > 0) startedActivityCount--
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
