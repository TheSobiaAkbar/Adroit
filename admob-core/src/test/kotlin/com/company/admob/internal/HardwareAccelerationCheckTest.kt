package com.company.admob.internal

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import com.company.admob.internal.logging.AdLog
import com.company.admob.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class HardwareAccelerationCheckTest {
    private val warnings = mutableListOf<String>()
    private val check = HardwareAccelerationCheck(
        AdLog({ level, _, message, _ -> if (level == LogLevel.WARN) warnings += message }, LogLevel.VERBOSE),
    )

    /** An Activity in onCreate: its window isn't added yet, so the window flag isn't set whatever the manifest says. */
    private fun createdActivity(manifestAccelerated: Boolean): Activity {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        shadowOf(activity.packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                name = Activity::class.java.name
                packageName = activity.packageName
                flags = if (manifestAccelerated) ActivityInfo.FLAG_HARDWARE_ACCELERATED else 0
            },
        )
        return controller.create().get()
    }

    @Test
    fun `accelerated in the manifest - no warning while the window flag isn't applied yet`() {
        val activity = createdActivity(manifestAccelerated = true)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        check.warnIfDisabled(activity)
        assertTrue(warnings.toString(), warnings.isEmpty())
    }

    @Test
    fun `not accelerated anywhere - one warning per process`() {
        val activity = createdActivity(manifestAccelerated = false)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        check.warnIfDisabled(activity)
        check.warnIfDisabled(activity)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("isn't hardware accelerated"))
    }

    @Test
    fun `accelerated at runtime through the window flag - no warning`() {
        val activity = createdActivity(manifestAccelerated = false)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        check.warnIfDisabled(activity)
        assertTrue(warnings.toString(), warnings.isEmpty())
    }
}
