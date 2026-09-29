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
import com.company.admob.NativeStyle
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

    /** A request is under way. */
    private var loading = false

    /** Bumped when the view drops its ad ([setPlacement], [release]), so an older request's ad is destroyed. */
    private var generation = 0

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
                // The enum's values are NativeStyle's ordinals (company_admob_attrs.xml).
                a.getInt(R.styleable.CompanyAdmobAdNativeView_company_admob_template, TEMPLATE_FROM_STYLE)
                    .takeIf { it in NativeStyle.entries.indices }
                    ?.let { NativeTemplates.forStyle(NativeStyle.entries[it]) }
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

    /** Destroys the current ad now, for a container that removes this view for good ([AdSlotView]). */
    internal fun release() {
        generation++
        loading = false
        boundOwner = null
        current?.destroy()
        current = null
    }

    /**
     * Shows [placement] with [layout] instead of the XML attributes (null: the placement's configured style). Loads
     * now if attached, otherwise on attach.
     */
    @JvmOverloads
    public fun setPlacement(placement: NativePlacement, layout: NativeAdLayout? = this.layout) {
        this.placement = placement
        this.layout = layout
        generation++
        loading = false
        boundOwner = null
        current?.destroy()
        current = null
        if (isAttachedToWindow) load()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val owner = boundOwner
        if (owner == null || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            // The ad went with its previous owner: this attach is a new screen.
            current = null
            loading = false
            generation++
            load()
        }
    }

    /**
     * Asks for a native ad in this view. It never requests a second one while the view shows an ad or one is
     * loading: the call does nothing then. It requests one when the view has none (the last load failed, or ads were
     * blocked).
     *
     * With [refresh], a shown ad is replaced by a new one: the new ad loads in the background while the current one
     * stays on screen (no shimmer), and is rendered in its place once it arrives. If it fails, the current ad stays.
     * A refresh while an ad is loading does nothing.
     *
     * Before the view is attached, it does nothing: the view loads on attach. Main thread only.
     */
    @JvmOverloads
    public fun load(refresh: Boolean = false) {
        val placement = placement ?: return
        if (!isAttachedToWindow || loading) return
        val shown = current?.takeUnless { it.isDestroyed }
        if (shown != null && !refresh) return
        val owner = findViewTreeLifecycleOwner()
        if (owner == null) {
            ViewsRuntime.error("AdNativeView(${placement.key}): no LifecycleOwner in the view tree; not loading an ad")
            visibility = GONE
            return
        }
        boundOwner = owner
        loading = true
        val loadGeneration = generation
        val resolvedLayout = layout ?: styledLayout(placement)
        if (shown == null) showNativeShimmer(this, placement, resolvedLayout)
        ViewsRuntime.client.native.load(placement) { result ->
            if (loadGeneration != generation || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                (result as? NativeAdResult.Loaded)?.handle?.destroy()
                return@load
            }
            loading = false
            when (result) {
                is NativeAdResult.Loaded -> {
                    if (renderInto(this, result.handle, owner, resolvedLayout, renderer = null) is NativeAdResult.Loaded) {
                        current = result.handle
                        // Only now: the old ad stayed on screen until its replacement was rendered.
                        if (shown != null && shown !== result.handle) shown.destroy()
                    }
                }
                is NativeAdResult.Failed -> if (shown == null) {
                    removeNativeShimmer(this)
                    visibility = GONE
                } else {
                    ViewsRuntime.debug("AdNativeView(${placement.key}): refresh not loaded; keeping the current ad")
                }
            }
        }
    }

    private companion object {
        /** No template attribute: the placement's configured style decides. */
        private const val TEMPLATE_FROM_STYLE = -1
    }
}
