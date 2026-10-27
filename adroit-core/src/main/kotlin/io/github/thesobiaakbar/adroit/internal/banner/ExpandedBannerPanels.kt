package io.github.thesobiaakbar.adroit.internal.banner

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.webkit.WebView
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * A collapsible banner's expanded panel is a window of its own (a popup the Google SDK opens over the Activity), so
 * every screen drawn in the Activity's window is under it. While the banner's screen is away the panel is hidden, not
 * dismissed: the ad stays expanded, and the panel shows again when the screen comes back with the same ad.
 */
internal object ExpandedBannerPanels {
    /**
     * The panels shown again by [show], each with the banner it belongs to. Two screens' panels look alike, and a
     * screen that comes back is shown before the one it replaces leaves: without this, the one leaving would hide the
     * panel just shown again.
     */
    private val owners = WeakHashMap<View, Any>()

    /**
     * Hides [activity]'s ad panels (popups holding web content) that are showing and aren't another banner's, and returns
     * them for [show], weakly held: a parked banner keeps no View or Activity. [owner] is the banner leaving.
     */
    fun hide(activity: Activity, owner: Any, log: AdLog): List<WeakReference<View>> {
        val token = activity.window?.decorView?.windowToken ?: return emptyList()
        val panels = roots(log).filter { root ->
            val params = root.layoutParams as? WindowManager.LayoutParams
            params != null &&
                params.type in WindowManager.LayoutParams.FIRST_SUB_WINDOW..WindowManager.LayoutParams.LAST_SUB_WINDOW &&
                params.token === token &&
                root.isAttachedToWindow &&
                root.visibility == View.VISIBLE &&
                owners[root].let { it == null || it === owner } &&
                root.holdsWebView()
        }
        panels.forEach {
            it.visibility = View.INVISIBLE
            owners.remove(it)
        }
        if (panels.isNotEmpty()) log.d { "Expanded banner panel hidden while its screen is away" }
        return panels.map(::WeakReference)
    }

    /** Shows [panels] hidden by [hide] again, those still open, as [owner]'s. */
    fun show(panels: List<WeakReference<View>>, owner: Any, log: AdLog) {
        val open = panels.mapNotNull { it.get() }.filter { it.isAttachedToWindow }
        open.forEach {
            it.visibility = View.VISIBLE
            owners[it] = owner
        }
        if (open.isNotEmpty()) log.d { "Expanded banner panel shown again" }
    }

    /** [owner] is destroyed: its panels are no one's. */
    fun forget(owner: Any) {
        owners.values.removeAll { it === owner }
    }

    /** The root view of every window of this process. */
    private fun roots(log: AdLog): List<View> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        WindowInspector.getGlobalWindowViews()
    } else {
        try {
            legacyRoots()
        } catch (e: Exception) {
            // ponytail: before Android 10 the windows are read by reflection; if that's blocked, the panel stays up.
            log.w(e) { "Reading the app's windows failed; an expanded banner may stay over the next screen" }
            emptyList()
        }
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun legacyRoots(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance)
        return (views as List<*>).filterIsInstance<View>()
    }

    private fun View.holdsWebView(): Boolean =
        this is WebView || (this is ViewGroup && (0 until childCount).any { getChildAt(it).holdsWebView() })
}
