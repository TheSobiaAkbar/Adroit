package io.github.thesobiaakbar.adroit.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.BannerAdHandle
import io.github.thesobiaakbar.adroit.BannerOptions
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.CollapsibleBannerPosition
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.views.internal.ViewAdRetainer
import io.github.thesobiaakbar.adroit.views.internal.ViewsRuntime
import kotlin.math.roundToInt

/**
 * A banner you can declare in XML:
 * ```xml
 * <io.github.thesobiaakbar.adroit.views.AdBannerView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:adroit_placement="home_banner" />
 * ```
 * When attached to a window, it creates the banner. The banner is kept by its screen: a ViewModel of the view tree's
 * `ViewModelStoreOwner` (the Fragment inside a Fragment, otherwise the Activity), one per placement and view id. When
 * the screen's views are destroyed (another tab, rotation) the banner goes off screen with its ad, and the new view
 * shows the same ad with no new request; it's destroyed when the screen is cleared. Only [load] with
 * `refresh = true` asks for another. Give each view of the same placement on a screen its own `android:id`. Without
 * a `ViewModelStoreOwner`, the banner is destroyed with the view tree's `LifecycleOwner` instead.
 *
 * Attributes: `adroit_placement` (the placement key), `adroit_bannerSize` (anchoredAdaptive,
 * inlineAdaptive, mediumRectangle, standard, collapsibleBottom or collapsibleTop; without it, the placement's
 * configured size decides, so the configuration or Firebase can change it), `adroit_inlineMaxHeight` (for
 * inlineAdaptive), and
 * `adroit_collapseWhenEmpty` (GONE while there's no ad; default true).
 *
 * An invalid or unknown key, or no `LifecycleOwner`, is logged as an error and the view stays GONE; it never
 * crashes. Banners belong in fixed containers; don't use this view in a `RecyclerView`. Main thread only.
 */
public class AdBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private var placement: BannerPlacement? = null
    private var options: BannerOptions = BannerOptions()
    private var collapseWhenEmpty = true
    private var handle: BannerAdHandle? = null

    /** A banner loading in the background to replace [handle] (`load(refresh = true)`); invisible until it's loaded. */
    private var incoming: BannerAdHandle? = null

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

    /** The screen's retainer and this view's slot in it, once the banner is kept by a screen. */
    private var retained: Pair<ViewAdRetainer, String>? = null

    /** The owner [handle] was attached for; once it's destroyed, a kept banner is off screen, in its retainer. */
    private var bannerOwner: LifecycleOwner? = null

    /** The current banner, or null before it's created (on attach) or after a failed setup. */
    internal val bannerHandle: BannerAdHandle? get() = handle

    init {
        readAttributes(attrs)
    }

    // The styleable carries the enforced adroit resource prefix, so it can't share the view's name.
    @SuppressLint("CustomViewStyleable")
    private fun readAttributes(attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.AdroitAdBannerView)
        try {
            collapseWhenEmpty = a.getBoolean(R.styleable.AdroitAdBannerView_adroit_collapseWhenEmpty, true)
            val defaultMaxHeightPx = resources.getDimension(R.dimen.adroit_banner_inline_max_height)
            val maxHeightDp =
                a.getDimension(R.styleable.AdroitAdBannerView_adroit_inlineMaxHeight, defaultMaxHeightPx) /
                    resources.displayMetrics.density
            val size = when (a.getInt(R.styleable.AdroitAdBannerView_adroit_bannerSize, SIZE_FROM_CONFIG)) {
                SIZE_ANCHORED -> BannerSize.AnchoredAdaptive
                SIZE_INLINE -> BannerSize.InlineAdaptive(maxHeightDp.roundToInt())
                SIZE_MEDIUM_RECTANGLE -> BannerSize.MediumRectangle
                SIZE_STANDARD -> BannerSize.Standard
                SIZE_COLLAPSIBLE_BOTTOM -> BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM)
                SIZE_COLLAPSIBLE_TOP -> BannerSize.Collapsible(CollapsibleBannerPosition.TOP)
                else -> null
            }
            options = BannerOptions(size)
            refreshOnShow = a.getBoolean(R.styleable.AdroitAdBannerView_adroit_refresh, false)
            a.getString(R.styleable.AdroitAdBannerView_adroit_placement)?.let { key ->
                placement = try {
                    BannerPlacement(key)
                } catch (e: IllegalArgumentException) {
                    ViewsRuntime.error("AdBannerView: ${e.message}")
                    null
                }
            }
        } finally {
            a.recycle()
        }
        if (placement == null) visibility = GONE
    }

    /**
     * Shows [placement] with [options] instead of the XML attributes. Replaces the current banner, if any; takes
     * effect now if attached, otherwise on attach.
     */
    @JvmOverloads
    public fun setPlacement(placement: BannerPlacement, options: BannerOptions = this.options) {
        this.placement = placement
        this.options = options
        dropRetained()
        handle?.destroy()
        handle = null
        dropIncoming()
        if (isAttachedToWindow) createBanner()
    }

    /** Destroys the current banner now, for a container that removes this view for good ([AdSlotView]). */
    internal fun release() {
        dropRetained()
        dropIncoming()
        handle?.destroy()
        handle = null
    }

    /**
     * Asks for a banner in this view. It never requests a second one while the view has a banner, loaded or loading:
     * the call does nothing then. It requests one when the view has none (the last load failed, or it was
     * destroyed).
     *
     * With [refresh], a banner that's already shown is replaced by a new one: the new banner loads in the
     * background while the current one stays on screen (no shimmer), and replaces it once loaded. If it fails, the
     * current banner stays. A refresh while a banner is loading, or while another refresh runs, does nothing.
     *
     * Before the view is attached, it does nothing: the view loads on attach. Main thread only.
     */
    @JvmOverloads
    public fun load(refresh: Boolean = false) {
        val placement = placement ?: return
        if (!isAttachedToWindow || incoming != null) return
        val current = handle
        when (val state = current?.state?.value) {
            null, BannerState.Hidden(null) -> Unit
            BannerState.Loading, is BannerState.Hidden -> return
            BannerState.Loaded -> {
                if (refresh) refreshBanner(placement)
                return
            }
            is BannerState.Failed -> ViewsRuntime.debug("AdBannerView(${placement.key}): no ad (${state.error.code}); requesting one")
        }
        current?.destroy()
        handle = null
        createBanner()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // A banner destroyed with its previous owner (Hidden(null)) is replaced when the view is attached again.
        if (handle?.state?.value == BannerState.Hidden(null)) handle = null
        // Kept by its screen and parked with its previous owner: createBanner shows it again.
        if (bannerOwner?.lifecycle?.currentState == Lifecycle.State.DESTROYED) handle = null
        if (handle == null) createBanner()
    }

    private fun createBanner() {
        val placement = placement ?: return
        val owner = findViewTreeLifecycleOwner()
        if (owner == null) {
            ViewsRuntime.error("AdBannerView(${placement.key}): no LifecycleOwner in the view tree; not showing a banner")
            visibility = GONE
            return
        }
        val retainer = ViewAdRetainer.of(this)
        val slot = slotName(placement.key)
        val kept = retainer?.banners?.get(slot)?.takeUnless { it.state.value == BannerState.Hidden(null) }
        // The collector can emit before attachBanner returns: until then, [created] is null and the state is this banner's.
        var created: BannerAdHandle? = null
        created = attachBanner(owner, placement, options, collapseWhenEmpty = false, existing = kept, keep = retainer != null) { state ->
            onBannerState(created, state, placement)
        }
        handle = created
        bannerOwner = owner
        if (retainer != null) {
            retainer.banners[slot] = created
            retained = retainer to slot
        }
        // Shown again with the banner its screen kept: a new one only when asked for.
        if (kept != null && refreshOnShow) load(refresh = true)
    }

    /** Destroys nothing itself: forgets this view's slot in its screen, whose banner the caller destroys. */
    private fun dropRetained() {
        val (retainer, slot) = retained ?: return
        retained = null
        retainer.banners.remove(slot)
    }

    /** Loads a new banner under the shown one; swaps them once it's loaded, or drops it if it fails. */
    private fun refreshBanner(placement: BannerPlacement) {
        val owner = findViewTreeLifecycleOwner() ?: return
        var created: BannerAdHandle? = null
        val keep = retained != null
        created = attachBanner(owner, placement, options, collapseWhenEmpty = false, shimmer = false, keep = keep) { state ->
            // Before attachBanner returns, the state is the new banner's own first one (Loading): nothing to do.
            created?.let { onBannerState(it, state, placement) }
        }
        val next = checkNotNull(created)
        // A refresh doesn't outlive the view it was asked in: a kept banner's screen would never show or destroy it.
        if (keep) {
            owner.lifecycle.addObserver(
                object : LifecycleEventObserver {
                    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                        if (event != Lifecycle.Event.ON_DESTROY) return
                        source.lifecycle.removeObserver(this)
                        if (incoming === next) dropIncoming()
                    }
                },
            )
        }
        // Laid out (it needs its width to load) but not seen until it replaces the current banner.
        next.view.visibility = INVISIBLE
        incoming = next
    }

    /**
     * A state of [from]: the incoming refresh swaps in when loaded or is dropped; the shown banner decides this
     * view's visibility. A banner this view replaced or dropped is ignored, so its destruction (Hidden) doesn't hide
     * the view. [from] is null only for the shown banner's states emitted while it's being created.
     */
    private fun onBannerState(from: BannerAdHandle?, state: BannerState, placement: BannerPlacement) {
        val candidate = incoming
        if (from != null && from === candidate) {
            when (state) {
                BannerState.Loading -> Unit
                BannerState.Loaded -> {
                    val old = handle
                    handle = candidate
                    incoming = null
                    retained?.let { (retainer, slot) -> retainer.banners[slot] = candidate }
                    candidate.view.visibility = VISIBLE
                    old?.destroy()
                    visibility = VISIBLE
                    ViewsRuntime.debug("AdBannerView(${placement.key}): refreshed")
                }
                is BannerState.Failed, is BannerState.Hidden -> {
                    ViewsRuntime.debug("AdBannerView(${placement.key}): refresh not loaded; keeping the current banner")
                    dropIncoming()
                }
            }
            return
        }
        if (from != null && from !== handle) return
        val invalid = (state as? BannerState.Failed)?.error is AdError.InvalidConfiguration
        visibility = if (invalid || (collapseWhenEmpty && state.hasNoAd())) GONE else VISIBLE
    }

    private fun dropIncoming() {
        val next = incoming ?: return
        incoming = null
        next.destroy()
    }

    private companion object {
        /** No size attribute: the placement's configured size decides. */
        private const val SIZE_FROM_CONFIG = -1
        private const val SIZE_ANCHORED = 0
        private const val SIZE_INLINE = 1
        private const val SIZE_MEDIUM_RECTANGLE = 2
        private const val SIZE_STANDARD = 3
        private const val SIZE_COLLAPSIBLE_BOTTOM = 4
        private const val SIZE_COLLAPSIBLE_TOP = 5
    }
}
