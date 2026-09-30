package io.github.thesobiaakbar.adroit.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import io.github.thesobiaakbar.adroit.internal.logging.AdLog

/**
 * Video in banner and native ads needs a hardware-accelerated window. Shared by both formats so the warning is
 * logged once per process run. Main thread only.
 *
 * The manifest setting is read from the Activity's [ActivityInfo] (which inherits the application's value): the
 * window's `FLAG_HARDWARE_ACCELERATED` is only applied when the window is added, after `onCreate`, so checking it
 * alone warned for every ad created in `onCreate`. The window flag still counts, for Activities that turn
 * acceleration on at runtime.
 */
internal class HardwareAccelerationCheck(private val log: AdLog) {
    private var warned = false

    fun warnIfDisabled(context: Context) {
        if (warned) return
        val activity = context.findActivity() ?: return
        if (activity.isHardwareAccelerated() != false) return
        warned = true
        log.w { "${activity.javaClass.simpleName} isn't hardware accelerated: video ads won't play" }
    }

    /** False only when neither the manifest nor the window enables acceleration; null when it can't be read. */
    private fun Activity.isHardwareAccelerated(): Boolean? {
        val windowFlags = window?.attributes?.flags ?: return null
        if (windowFlags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0) return true
        val info = try {
            packageManager.getActivityInfo(componentName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return info.flags and ActivityInfo.FLAG_HARDWARE_ACCELERATED != 0
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findActivity()
        else -> null
    }
}
