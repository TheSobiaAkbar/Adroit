@file:Suppress("unused")

package io.github.thesobiaakbar.adroit.docs

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import io.github.thesobiaakbar.adroit.BannerOptions
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.compose.AdBanner
import io.github.thesobiaakbar.adroit.views.attachBanner

// region fragment-attach
class HomeFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // viewLifecycleOwner: the banner is destroyed in onDestroyView, never leaked across view recreation.
        view.findViewById<FrameLayout>(R.id.banner_container)
            .attachBanner(viewLifecycleOwner, Placements.HomeBanner)
    }
}
// endregion

// region plain-view
/** Without the views module's helpers: create the handle yourself, add its view, and destroy it. */
fun showBanner(activity: Activity, container: FrameLayout, owner: LifecycleOwner) {
    val handle = Adroit.banner.create(
        activity,
        Placements.HomeBanner,
        BannerOptions(size = BannerSize.InlineAdaptive(maxHeightDp = 250), reserveSpace = true),
    )
    container.addView(handle.view)
    handle.bindTo(owner) // destroyed when owner is destroyed; or call handle.destroy() yourself
}
// endregion

// region compose-banner
@Composable
fun HomeBottomBar() {
    var state by remember { mutableStateOf<BannerState?>(null) }
    AdBanner(
        placement = Placements.HomeBanner,
        modifier = Modifier.fillMaxWidth(),
        options = BannerOptions(BannerSize.AnchoredAdaptive),
        placeholder = { Text("Advertisement", Modifier.padding(8.dp)) },
        onStateChange = { state = it },
    )
}
// endregion

// region compose-mrec
@Composable
fun ArticleRectangle() {
    AdBanner(
        placement = Placements.HomeBanner,
        modifier = Modifier.fillMaxWidth().height(250.dp),
        options = BannerOptions(BannerSize.MediumRectangle),
    )
}
// endregion
