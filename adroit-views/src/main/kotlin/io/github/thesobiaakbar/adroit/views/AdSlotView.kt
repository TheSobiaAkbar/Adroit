package io.github.thesobiaakbar.adroit.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AdSlots
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.views.internal.ViewsRuntime
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A banner or a native ad, whichever format the placement has now, declared once in XML:
 * ```xml
 * <io.github.thesobiaakbar.adroit.views.AdSlotView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:adroit_placement="HomeBanner" />
 * ```
 * A Firebase value can swap the placement between banner and native (docs/AD_PROFILES.md, "Swapping formats"). This
 * view then replaces its [AdBannerView] with an [AdNativeView] or back, while attached. Size and template come from
 * the placement's style. It's GONE while the placement is neither, or unknown. Main thread only.
 */
public class AdSlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private var key: String? = null
    private var shown: AdFormat? = null
    private var watch: Job? = null

    init {
        readAttributes(attrs)
    }

    // The styleable carries the enforced adroit resource prefix, so it can't share the view's name.
    @SuppressLint("CustomViewStyleable")
    private fun readAttributes(attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.AdroitAdSlotView)
        try {
            key = a.getString(R.styleable.AdroitAdSlotView_adroit_placement)
        } finally {
            a.recycle()
        }
        if (key == null) visibility = GONE
    }

    /** Shows [definition]'s ad instead of the XML attribute's. */
    public fun setDefinition(definition: AdDefinition) {
        setKey(definition.name)
    }

    /** Shows the ad with [key] instead of the XML attribute's. Takes effect now if attached, otherwise on attach. */
    public fun setKey(key: String) {
        this.key = key
        reset()
        if (isAttachedToWindow) startWatching()
    }

    /**
     * Asks the banner or native ad shown now for an ad: see [AdBannerView.load] and [AdNativeView.load]. It never
     * requests a second one while the slot has an ad or one is loading; with [refresh], a shown ad is replaced once a
     * new one has loaded, and stays if it fails. Main thread only.
     */
    @JvmOverloads
    public fun load(refresh: Boolean = false) {
        for (i in 0 until childCount) {
            when (val child = getChildAt(i)) {
                is AdBannerView -> child.load(refresh)
                is AdNativeView -> child.load(refresh)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startWatching()
    }

    override fun onDetachedFromWindow() {
        // The child views release their own ads with their lifecycle owner; only the format watch ends here.
        watch?.cancel()
        watch = null
        super.onDetachedFromWindow()
    }

    private fun startWatching() {
        val key = key ?: return
        val owner = findViewTreeLifecycleOwner()
        if (owner == null) {
            ViewsRuntime.error("AdSlotView($key): no LifecycleOwner in the view tree; not showing an ad")
            visibility = GONE
            return
        }
        watch?.cancel()
        watch = owner.lifecycleScope.launch {
            AdSlots.formatChanges(key).collect { format -> render(key, format) }
        }
    }

    private fun render(key: String, format: AdFormat?) {
        if (format == shown && childCount > 0) return
        reset()
        shown = format
        val child = when (format) {
            AdFormat.BANNER -> AdBannerView(context).apply { setPlacement(BannerPlacement(key)) }
            AdFormat.NATIVE -> AdNativeView(context).apply { setPlacement(NativePlacement(key)) }
            else -> null
        }
        if (child == null) {
            if (format != null) ViewsRuntime.error("AdSlotView($key): $format isn't a banner or native ad")
            visibility = GONE
            return
        }
        visibility = VISIBLE
        addView(child, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Removes the current child and destroys its ad, so a swapped-out banner stops refreshing. */
    private fun reset() {
        shown = null
        for (i in 0 until childCount) {
            when (val child = getChildAt(i)) {
                is AdBannerView -> child.release()
                is AdNativeView -> child.release()
            }
        }
        removeAllViews()
    }
}
