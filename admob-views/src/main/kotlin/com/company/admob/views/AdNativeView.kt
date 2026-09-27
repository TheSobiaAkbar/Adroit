package com.company.admob.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.company.admob.NativeAdHandle
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdResult
import com.company.admob.NativePlacement
import com.company.admob.views.internal.ViewsRuntime

/**
 * A native ad you can declare in XML:
 * ```xml
 * <com.company.admob.views.AdNativeView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:company_admob_placement="feed_native"
 *     app:company_admob_template="medium" />
 * ```
 * When attached to a window, it loads and renders the ad for its view tree's `LifecycleOwner` (the Fragment's view
 * lifecycle inside a Fragment) and destroys it with that owner. It's GONE if the load fails.
 *
 * Attributes: `company_admob_placement` (the placement key), `company_admob_template` (small, medium or large;
 * without it, the placement's configured style decides, so the configuration or Firebase can change it), or
 * `company_admob_layout`: your own layout, which must use the standard template IDs (see [NativeTemplates]).
 *
 * An invalid key or no `LifecycleOwner` is logged as an error and the view stays GONE; it never crashes. For ads
 * that must survive rotation without reloading, use [NativeAdHolder] from a ViewModel instead. Main thread only.
 */
public class AdNativeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private var placement: NativePlacement? = null
    /** The layout from the attributes or [setPlacement]; null uses the placement's configured style. */
    private var layout: NativeAdLayout? = null

    /** The owner the current ad is bound to; null until attached, and again once that owner is destroyed. */
    private var boundOwner: LifecycleOwner? = null
    private var current: NativeAdHandle? = null

    init {
        readAttributes(attrs)
    }

    // The styleable carries the enforced company_admob resource prefix, so it can't share the view's name.
    @SuppressLint("CustomViewStyleable")
    private fun readAttributes(attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.CompanyAdmobAdNativeView)
        try {
            val customLayout = a.getResourceId(R.styleable.CompanyAdmobAdNativeView_company_admob_layout, 0)
            layout = if (customLayout != 0) {
                NativeTemplates.forLayout(customLayout)
            } else {
                when (a.getInt(R.styleable.CompanyAdmobAdNativeView_company_admob_template, TEMPLATE_FROM_STYLE)) {
                    TEMPLATE_SMALL -> NativeTemplates.Small
                    TEMPLATE_MEDIUM -> NativeTemplates.Medium
                    TEMPLATE_LARGE -> NativeTemplates.Large
                    else -> null
                }
            }
            a.getString(R.styleable.CompanyAdmobAdNativeView_company_admob_placement)?.let { key ->
                placement = try {
                    NativePlacement(key)
                } catch (e: IllegalArgumentException) {
                    ViewsRuntime.error("AdNativeView: ${e.message}")
                    null
                }
            }
        } finally {
            a.recycle()
        }
        if (placement == null) visibility = GONE
    }

    /**
     * Shows [placement] with [layout] instead of the XML attributes (null: the placement's configured style). Loads
     * now if attached, otherwise on attach.
     */
    @JvmOverloads
    public fun setPlacement(placement: NativePlacement, layout: NativeAdLayout? = this.layout) {
        this.placement = placement
        this.layout = layout
        boundOwner = null
        current?.destroy()
        current = null
        if (isAttachedToWindow) loadAd()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val owner = boundOwner
        if (owner == null || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) loadAd()
    }

    private fun loadAd() {
        val placement = placement ?: return
        val owner = findViewTreeLifecycleOwner()
        if (owner == null) {
            ViewsRuntime.error("AdNativeView(${placement.key}): no LifecycleOwner in the view tree; not loading an ad")
            visibility = GONE
            return
        }
        boundOwner = owner
        attachNativeAd(owner, placement, layout) { result ->
            current = (result as? NativeAdResult.Loaded)?.handle
        }
    }

    private companion object {
        private const val TEMPLATE_SMALL = 0
        private const val TEMPLATE_MEDIUM = 1
        private const val TEMPLATE_LARGE = 2

        /** No template attribute: the placement's configured style decides. */
        private const val TEMPLATE_FROM_STYLE = -1
    }
}
