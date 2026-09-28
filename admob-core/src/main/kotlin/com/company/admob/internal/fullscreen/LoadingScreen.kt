package com.company.admob.internal.fullscreen

import android.app.Activity
import android.app.Dialog
import com.company.admob.R
import com.company.admob.internal.logging.AdLog

/** The "Loading ad…" screen shown before a full-screen ad (PROMPT 29B). Main thread only. */
internal fun interface LoadingScreen {
    /** Removes the screen. Safe to call more than once, and after its Activity is gone. */
    fun dismiss()
}

/** Shows [LoadingScreen]s; a seam so engine tests run without windows. Main thread only. */
internal fun interface LoadingScreens {
    /** A screen over [activity], or null when it can't be shown there (finishing, no window). */
    fun show(activity: Activity): LoadingScreen?
}

/**
 * [LoadingScreens] as a full-screen, non-cancelable [Dialog] with the layout `company_admob_loading_ad`: an app
 * replaces the look by declaring its own layout of that name (and the text with the `company_admob_loading_ad`
 * string). It's never left on screen: a failure to show or dismiss is logged, never thrown.
 */
internal class DialogLoadingScreens(private val log: AdLog) : LoadingScreens {
    override fun show(activity: Activity): LoadingScreen? {
        if (activity.isFinishing || activity.isDestroyed) return null
        return try {
            val dialog = Dialog(activity, R.style.CompanyAdMob_LoadingAd)
            dialog.setContentView(R.layout.company_admob_loading_ad)
            dialog.setCancelable(false)
            dialog.show()
            LoadingScreen {
                try {
                    if (dialog.isShowing && !activity.isDestroyed) dialog.dismiss()
                } catch (e: RuntimeException) {
                    log.w(e) { "Couldn't dismiss the loading screen" }
                }
            }
        } catch (e: RuntimeException) {
            log.w(e) { "Couldn't show the loading screen; showing the ad without it" }
            null
        }
    }
}
