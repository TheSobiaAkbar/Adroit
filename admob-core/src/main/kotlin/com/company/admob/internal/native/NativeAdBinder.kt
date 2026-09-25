package com.company.admob.internal.native

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.company.admob.NativeAdAssets
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdRenderer
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.NativeAssetViews
import com.company.admob.internal.port.NativeRenderPort
import java.util.Locale

/**
 * Renders a native ad into the host's own layout (architecture §17): the host layout is inflated inside the SDK's ad
 * view, its asset views are found by ID and filled, a media view goes into the media container, and the views are
 * registered with the SDK through [port]. Hosts never reference Google classes. Main thread only.
 */
internal class NativeAdBinder(private val port: NativeRenderPort, private val log: AdLog) {
    /** (layout, view ID) pairs already warned about, so a missing optional view is logged once. */
    private val warnedMissing = mutableSetOf<Pair<Int, Int>>()

    fun render(handle: NativeAdHandleImpl, container: ViewGroup, layout: NativeAdLayout, renderer: NativeAdRenderer?): View {
        check(!handle.isDestroyed) { "render(): the native ad for ${handle.placement.key} was already released" }
        val context = container.context
        val adView = port.createAdView(context)
        adView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        LayoutInflater.from(context).inflate(layout.layoutRes, adView, true)

        val headline = requireNotNull(adView.findViewById<View>(layout.headlineId)) {
            "The native ad layout ${resourceName(adView, layout.layoutRes)} has no headline view " +
                "${resourceName(adView, layout.headlineId)}; headlineId is required"
        }
        // A handle shows in one place at a time: detach it from its previous view before registering it again.
        handle.detachView()
        val assets = handle.assets
        val views = NativeAssetViews(
            headline = headline.also { setText(it, assets.headline, hideWhenNull = false) },
            body = optional(adView, layout, layout.bodyId)?.also { setText(it, assets.body) },
            callToAction = optional(adView, layout, layout.callToActionId)?.also { setText(it, assets.callToAction) },
            icon = optional(adView, layout, layout.iconId)?.also { setIcon(it, assets) },
            advertiser = optional(adView, layout, layout.advertiserId)?.also { setText(it, assets.advertiser) },
            starRating = optional(adView, layout, layout.starRatingId)?.also { setRating(it, assets.starRating) },
            price = optional(adView, layout, layout.priceId)?.also { setText(it, assets.price) },
            store = optional(adView, layout, layout.storeId)?.also { setText(it, assets.store) },
        )
        optional(adView, layout, layout.attributionId)
        val mediaView = (optional(adView, layout, layout.mediaContainerId) as? ViewGroup)?.let { mediaContainer ->
            port.createMediaView(context).also {
                mediaContainer.removeAllViews()
                mediaContainer.addView(
                    it,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        }
        port.register(adView, handle.ad, views, mediaView)
        renderer?.onBind(adView, assets)

        container.removeAllViews()
        container.addView(adView)
        handle.attachView(adView, port::release)
        return adView
    }

    private fun optional(adView: ViewGroup, layout: NativeAdLayout, id: Int): View? {
        if (id == 0) return null
        val view = adView.findViewById<View>(id)
        if (view == null && warnedMissing.add(layout.layoutRes to id)) {
            log.w { "Native ad layout ${resourceName(adView, layout.layoutRes)} has no view ${resourceName(adView, id)}" }
        }
        return view
    }

    private fun setText(view: View, text: String?, hideWhenNull: Boolean = true) {
        if (text == null && hideWhenNull) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        (view as? TextView)?.text = text
    }

    private fun setIcon(view: View, assets: NativeAdAssets) {
        val icon = assets.icon
        view.visibility = if (icon == null) View.GONE else View.VISIBLE
        (view as? ImageView)?.setImageDrawable(icon)
    }

    /** A [RatingBar] gets the stars; any other [TextView] gets the value as text, for example "4.5". */
    private fun setRating(view: View, rating: Double?) {
        if (rating == null) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        when (view) {
            is RatingBar -> view.rating = rating.toFloat()
            is TextView -> view.text = String.format(Locale.getDefault(), "%.1f", rating)
        }
    }

    private fun resourceName(view: View, id: Int): String =
        runCatching { view.resources.getResourceName(id) }.getOrDefault("0x" + Integer.toHexString(id))
}
