@file:Suppress("unused")

package io.github.thesobiaakbar.adroit.docs

import android.app.Application
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import io.github.thesobiaakbar.adroit.NativeAdLayout
import io.github.thesobiaakbar.adroit.NativeAdRenderer
import io.github.thesobiaakbar.adroit.NativeAdResult
import io.github.thesobiaakbar.adroit.compose.AdNative
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.config.AdProfile
import io.github.thesobiaakbar.adroit.compose.rememberNativeAd
import io.github.thesobiaakbar.adroit.views.NativeAdHolder
import io.github.thesobiaakbar.adroit.views.NativeTemplates
import io.github.thesobiaakbar.adroit.views.attachNativeAd

// region compose-native
@Composable
fun ArticleFooterAd() {
    // Kept by the screen's ViewModelStoreOwner: recomposition and rotation re-render the same ad.
    AdNative(
        placement = Placements.FeedNative,
        modifier = Modifier.fillMaxWidth(),
        layout = NativeTemplates.LargeButtonBottom,
    )
}
// endregion

// region compose-feed
@Composable
fun Feed(articles: List<String>) {
    LazyColumn {
        articles.forEachIndexed { index, article ->
            item(key = article) { Text(article) }
            if (index % 6 == 5) {
                val slot = "ad-${index / 6}"
                // A distinct, stable slotKey per ad item: scrolling away and back doesn't reload it.
                item(key = slot) { AdNative(Placements.FeedNative, layout = NativeTemplates.SmallButtonEnd, slotKey = slot) }
            }
        }
    }
}
// endregion

// region compose-remember
@Composable
fun SponsoredHeadline() {
    val result by rememberNativeAd(Placements.FeedNative)
    when (val ad = result) {
        null -> Text("Loading…")
        is NativeAdResult.Loaded -> Text("Sponsored: ${ad.handle.assets.headline}")
        is NativeAdResult.Failed -> Unit // show nothing
    }
}
// endregion

// region views-attach
class ArticleFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<FrameLayout>(R.id.banner_container)
            .attachNativeAd(viewLifecycleOwner, Placements.FeedNative, NativeTemplates.LargeCompact)
    }
}
// endregion

// region views-holder
/** RecyclerView: one holder per ad position, kept in the ViewModel so scrolling and rotation don't reload. */
class FeedViewModel : ViewModel() {
    private val holders = mutableMapOf<Int, NativeAdHolder>()

    fun adHolder(slot: Int): NativeAdHolder = holders.getOrPut(slot) { NativeAdHolder(Placements.FeedNative) }

    override fun onCleared() {
        holders.values.forEach(NativeAdHolder::clear) // destroys the ads
    }
}

// In the adapter's onBindViewHolder, for an ad row:
fun bindAdRow(viewModel: FeedViewModel, slot: Int, container: ViewGroup, viewLifecycleOwner: LifecycleOwner) {
    viewModel.adHolder(slot).bindTo(container, viewLifecycleOwner, NativeTemplates.SmallButtonEnd)
}
// endregion

// region custom-layout
/** Your own layout: map its view IDs. Keep an "Ad" attribution label and room for the AdChoices icon. */
val ArticleAdLayout = NativeAdLayout(
    layoutRes = R.layout.snippet_native_ad,
    headlineId = R.id.ad_headline,
    bodyId = R.id.ad_body,
    callToActionId = R.id.ad_cta,
    iconId = R.id.ad_icon,
    attributionId = R.id.ad_attribution,
)

/** Styling after the standard binding. Never set click listeners: the SDK handles clicks. */
val ArticleAdRenderer = NativeAdRenderer { root, assets ->
    root.findViewById<TextView>(R.id.ad_headline).setTypeface(null, Typeface.BOLD)
    root.findViewById<TextView>(R.id.ad_body).visibility = if (assets.body == null) View.GONE else View.VISIBLE
}

@Composable
fun CustomNativeAd() {
    AdNative(Placements.FeedNative, layout = ArticleAdLayout, renderer = ArticleAdRenderer)
}
// endregion

// region native-register
class PromoAdsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Your own layout under a name: ad profiles and Firebase can now pick it with "style": "promo".
        NativeTemplates.register("promo", ArticleAdLayout)
        // Or, for a layout using the standard IDs: NativeTemplates.register("promo", R.layout.my_native_ad)
    }
}

/** An ad shown in the registered layout by default; Firebase can still switch it to any style or layout. */
enum class PromoAds(override val defaults: AdProfile) : AdDefinition {
    PromoNative(AdProfile.native(layout = "promo")),
}
// endregion
