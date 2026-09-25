package com.company.admob.internal.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
class ActivityTrackerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val tracker = LifecycleActivityTracker().also { it.register(app) }

    /** An Activity that is also a [LifecycleOwner], like every AndroidX Activity. */
    class OwnerActivity : Activity(), LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            registry.currentState = Lifecycle.State.CREATED
        }

        override fun onResume() {
            super.onResume()
            registry.currentState = Lifecycle.State.RESUMED
        }

        override fun onPause() {
            registry.currentState = Lifecycle.State.STARTED
            super.onPause()
        }
    }

    @After
    fun tearDown() = tracker.unregister(app)

    @Test
    fun `the resumed reference is set on resume and cleared on pause and destroy`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        assertSame(controller.get(), tracker.resumedActivity)
        assertEquals(1, tracker.startedActivityCount)

        controller.pause()
        assertNull(tracker.resumedActivity)
        controller.resume()
        assertSame(controller.get(), tracker.resumedActivity)

        controller.pause().stop()
        assertEquals(0, tracker.startedActivityCount)
        controller.destroy()
        assertNull(tracker.resumedActivity)
    }

    @Test
    fun `isValidForShow - resumed yes, and paused, finishing or destroyed no`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        assertTrue(tracker.isValidForShow(activity))

        controller.pause()
        assertFalse(tracker.isValidForShow(activity))
        controller.resume()

        activity.finish()
        assertFalse(tracker.isValidForShow(activity))

        controller.pause().stop().destroy()
        assertFalse(tracker.isValidForShow(activity))
    }

    @Test
    fun `isValidForShow uses the Lifecycle of a LifecycleOwner`() {
        val controller = Robolectric.buildActivity(OwnerActivity::class.java).setup()
        val activity = controller.get()
        assertTrue(tracker.isValidForShow(activity))
        controller.pause()
        assertFalse(tracker.isValidForShow(activity))
    }

    @Test
    fun `a closed Activity isn't retained`() {
        var controller: org.robolectric.android.controller.ActivityController<Activity>? =
            Robolectric.buildActivity(Activity::class.java).setup()
        val ref = WeakReference(controller!!.get())
        controller.pause().stop().destroy()
        controller = null

        repeat(10) {
            if (ref.get() == null) return@repeat
            System.gc()
            System.runFinalization()
            Thread.sleep(20)
        }
        assertNull("the tracker must not keep a destroyed Activity", ref.get())
        assertNull(tracker.resumedActivity)
    }
}
