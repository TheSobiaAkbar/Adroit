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
import com.company.admob.BannerState
import com.company.admob.views.internal.ViewsRuntime
import kotlin.math.roundToInt

/**
 * A banner you can declare in XML:
 * ```xml
 * <com.company.admob.views.AdBannerView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:company_admob_placement="home_banner"
 *     app:company_admob_bannerSize="anchoredAdaptive" />
 * ```
 * When attached to a window, it finds its `LifecycleOwner` (`findViewTreeLifecycleOwner()`, which is the Fragment's
 * view lifecycle inside a Fragment), creates the banner and destroys it when that owner is destroyed.
 *
 * Attributes: `company_admob_placement` (the placement key), `company_admob_bannerSize` (anchoredAdaptive,
 * inlineAdaptive, mediumRectangle or standard), `company_admob_inlineMaxHeight` (for inlineAdaptive), and
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
            val size = when (a.getInt(R.styleable.CompanyAdmobAdBannerView_company_admob_bannerSize, SIZE_ANCHORED)) {
                SIZE_INLINE -> BannerSize.InlineAdaptive(maxHeightDp.roundToInt())
                SIZE_MEDIUM_RECTANGLE -> BannerSize.MediumRectangle
                SIZE_STANDARD -> BannerSize.Standard
                else -> BannerSize.AnchoredAdaptive
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
        if (isAttachedToWindow) createBanner()
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

    private companion object {
        private const val SIZE_ANCHORED = 0
        private const val SIZE_INLINE = 1
        private const val SIZE_MEDIUM_RECTANGLE = 2
        private const val SIZE_STANDARD = 3
    }
}
