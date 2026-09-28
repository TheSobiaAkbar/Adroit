package com.company.admob.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.company.admob.AdError
import com.company.admob.BannerAdHandle
import com.company.admob.BannerOptions
import com.company.admob.BannerPlacement
import com.company.admob.BannerSize
import com.company.admob.CollapsibleBannerPosition
import com.company.admob.BannerState
import com.company.admob.views.internal.ViewsRuntime
import kotlin.math.roundToInt

/**
 * A banner you can declare in XML:
 * ```xml
 * <com.company.admob.views.AdBannerView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:company_admob_placement="home_banner" />
 * ```
 * When attached to a window, it finds its `LifecycleOwner` (`findViewTreeLifecycleOwner()`, which is the Fragment's
 * view lifecycle inside a Fragment), creates the banner and destroys it when that owner is destroyed.
 *
 * Attributes: `company_admob_placement` (the placement key), `company_admob_bannerSize` (anchoredAdaptive,
 * inlineAdaptive, mediumRectangle, standard, collapsibleBottom or collapsibleTop; without it, the placement's
 * configured size decides, so the configuration or Firebase can change it), `company_admob_inlineMaxHeight` (for
 * inlineAdaptive), and
 * `company_admob_collapseWhenEmpty` (GONE while there's no ad; default true).
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

    /** The current banner, or null before it's created (on attach) or after a failed setup. */
    internal val bannerHandle: BannerAdHandle? get() = handle

    init {
        readAttributes(attrs)
    }

    // The styleable carries the enforced company_admob resource prefix, so it can't share the view's name.
    @SuppressLint("CustomViewStyleable")
    private fun readAttributes(attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.CompanyAdmobAdBannerView)
        try {
            collapseWhenEmpty = a.getBoolean(R.styleable.CompanyAdmobAdBannerView_company_admob_collapseWhenEmpty, true)
            val defaultMaxHeightPx = resources.getDimension(R.dimen.company_admob_banner_inline_max_height)
            val maxHeightDp =
                a.getDimension(R.styleable.CompanyAdmobAdBannerView_company_admob_inlineMaxHeight, defaultMaxHeightPx) /
                    resources.displayMetrics.density
            val size = when (a.getInt(R.styleable.CompanyAdmobAdBannerView_company_admob_bannerSize, SIZE_FROM_CONFIG)) {
                SIZE_ANCHORED -> BannerSize.AnchoredAdaptive
                SIZE_INLINE -> BannerSize.InlineAdaptive(maxHeightDp.roundToInt())
                SIZE_MEDIUM_RECTANGLE -> BannerSize.MediumRectangle
                SIZE_STANDARD -> BannerSize.Standard
                SIZE_COLLAPSIBLE_BOTTOM -> BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM)
                SIZE_COLLAPSIBLE_TOP -> BannerSize.Collapsible(CollapsibleBannerPosition.TOP)
                else -> null
            }
            options = BannerOptions(size)
            a.getString(R.styleable.CompanyAdmobAdBannerView_company_admob_placement)?.let { key ->
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
        handle?.destroy()
        handle = null
        dropIncoming()
        if (isAttachedToWindow) createBanner()
    }

    /** Destroys the current banner now, for a container that removes this view for good ([AdSlotView]). */
    internal fun release() {
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
        handle = attachBanner(owner, placement, options, collapseWhenEmpty = false) { state ->
            val invalid = (state as? BannerState.Failed)?.error is AdError.InvalidConfiguration
            visibility = if (invalid || (collapseWhenEmpty && state.hasNoAd())) GONE else VISIBLE
        }
    }

    /** Loads a new banner under the shown one; swaps them once it's loaded, or drops it if it fails. */
    private fun refreshBanner(placement: BannerPlacement) {
        val owner = findViewTreeLifecycleOwner() ?: return
        val next = attachBanner(owner, placement, options, collapseWhenEmpty = false, shimmer = false) { state ->
            val candidate = incoming ?: return@attachBanner
            when (state) {
                BannerState.Loading -> Unit
                BannerState.Loaded -> {
                    val old = handle
                    handle = candidate
                    incoming = null
                    candidate.view.visibility = VISIBLE
                    old?.destroy()
                    ViewsRuntime.debug("AdBannerView(${placement.key}): refreshed")
                }
                is BannerState.Failed, is BannerState.Hidden -> {
                    ViewsRuntime.debug("AdBannerView(${placement.key}): refresh not loaded; keeping the current banner")
                    dropIncoming()
                }
            }
        }
        // Laid out (it needs its width to load) but not seen until it replaces the current banner.
        next.view.visibility = INVISIBLE
        incoming = next
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
