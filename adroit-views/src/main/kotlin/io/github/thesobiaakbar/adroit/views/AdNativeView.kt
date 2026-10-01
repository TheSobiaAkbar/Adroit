package io.github.thesobiaakbar.adroit.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import io.github.thesobiaakbar.adroit.NativeAdHandle
import io.github.thesobiaakbar.adroit.NativeAdLayout
import io.github.thesobiaakbar.adroit.NativeAdResult
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.views.internal.ViewAdRetainer
import io.github.thesobiaakbar.adroit.views.internal.ViewsRuntime

/**
 * A native ad you can declare in XML:
 * ```xml
 * <io.github.thesobiaakbar.adroit.views.AdNativeView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:adroit_placement="feed_native"
 *     app:adroit_template="large_button_bottom" />
 * ```
 * When attached to a window, it loads and renders the ad. The ad is kept by its screen: a ViewModel of the view tree's
 * `ViewModelStoreOwner` (the Fragment inside a Fragment, otherwise the Activity), one per placement and view id. When
 * the screen's views are created again (coming back to a Fragment, rotation), the new view shows the same ad with no
 * new request; it's destroyed when the screen is cleared. Only [load] with `refresh = true` asks for another. Give
 * each view of the same placement on a screen its own `android:id`. Without a `ViewModelStoreOwner`, the ad is
 * destroyed with the view tree's `LifecycleOwner` instead. It's GONE if the load fails.
 *
 * Attributes: `adroit_placement` (the placement key), `adroit_template` (small, medium or large;
 * without it, the placement's configured style decides, so the configuration or Firebase can change it), or
 * `adroit_layout`: your own layout, which must use the standard template IDs (see [NativeTemplates]).
 *
 * An invalid key or no `LifecycleOwner` is logged as an error and the view stays GONE; it never crashes. Main thread
 * only.
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

    /**
     * Names this view's ad on its screen, which keeps one ad per placement and slot. Defaults to the view's
     * `android:id`: set it for views created in code, or to tell apart views of the same placement without ids.
     * Set it before the view is attached.
     */
    public var slotKey: String? = null

    /**
     * `app:adroit_refresh`. False (the default): the view keeps its screen's ad, and only `load(refresh = true)` asks
     * for another. True: each time the view is created again on its screen (coming back to a tab, rotation), it asks
     * for a new ad; the shown one stays until the new one replaces it.
     */
    public var refreshOnShow: Boolean = false

    /** This view's slot name for [placementKey] in its screen's [ViewAdRetainer]. */
    private fun slotName(placementKey: String) = ViewAdRetainer.slot(placementKey, slotKey ?: "id:$id")

    /** The screen's retainer and this view's slot in it, once attached to a screen that keeps ads. */
    private var retained: Pair<ViewAdRetainer, String>? = null

    init {
        readAttributes(attrs)
    }

    // The styleable carries the enforced adroit resource prefix, so it can't share the view's name.
    @SuppressLint("CustomViewStyleable")
    private fun readAttributes(attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.AdroitAdNativeView)
        try {
            val customLayout = a.getResourceId(R.styleable.AdroitAdNativeView_adroit_layout, 0)
            layout = if (customLayout != 0) {
                NativeTemplates.forLayout(customLayout)
            } else {
                // The enum's values are NativeStyle's ordinals (adroit_attrs.xml).
                a.getInt(R.styleable.AdroitAdNativeView_adroit_template, TEMPLATE_FROM_STYLE)
                    .takeIf { it in NativeStyle.entries.indices }
                    ?.let { NativeTemplates.forStyle(NativeStyle.entries[it]) }
            }
            refreshOnShow = a.getBoolean(R.styleable.AdroitAdNativeView_adroit_refresh, false)
            a.getString(R.styleable.AdroitAdNativeView_adroit_placement)?.let { key ->
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
        dropRetained()
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
        dropRetained()
        generation++
        loading = false
        boundOwner = null
        current?.destroy()
        current = null
        if (isAttachedToWindow && !bindRetained()) load()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (bindRetained()) return
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
        retained?.let { (retainer, slot) ->
            retainer.natives[slot]?.load(refresh)
            return
        }
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

    /**
     * Shows this view's slot of its screen's [ViewAdRetainer]: the ad it keeps, or a new one it loads. False when
     * there's no placement, `ViewModelStoreOwner` or `LifecycleOwner`.
     */
    private fun bindRetained(): Boolean {
        val placement = placement ?: return false
        val retainer = ViewAdRetainer.of(this) ?: return false
        val owner = findViewTreeLifecycleOwner() ?: return false
        val slot = slotName(placement.key)
        retained = retainer to slot
        val kept = retainer.natives[slot]
        val holder = kept ?: NativeAdHolder(placement).also { retainer.natives[slot] = it }
        holder.bindTo(this, owner, layout)
        // Shown again with the ad its screen kept: a new one only when asked for.
        if (kept != null && refreshOnShow) holder.load(refresh = true)
        return true
    }

    /** Destroys the ad this view's slot keeps, for a new placement or a removed view. */
    private fun dropRetained() {
        val (retainer, slot) = retained ?: return
        retained = null
        retainer.natives.remove(slot)?.clear()
    }

    private companion object {
        /** No template attribute: the placement's configured style decides. */
        private const val TEMPLATE_FROM_STYLE = -1
    }
}
