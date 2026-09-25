package com.company.admob.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import com.company.admob.internal.logging.AdLog

/**
 * Video in banner and native ads needs a hardware-accelerated window. Shared by both formats so the warning is
 * logged once per process run. Main thread only.
 */
internal class HardwareAccelerationCheck(private val log: AdLog) {
    private var warned = false

    fun warnIfDisabled(context: Context) {
        if (warned) return
        val activity = context.findActivity() ?: return
        val flags = activity.window?.attributes?.flags ?: return
        if (flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED == 0) {
            warned = true
            log.w { "${activity.javaClass.simpleName} isn't hardware accelerated: video ads won't play" }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findActivity()
        else -> null
    }
}
