package com.company.admob.sample.compose.styles

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.NativeAdLayout
import com.company.admob.NativeStyle
import com.company.admob.compose.AdNative
import com.company.admob.config.nativePlacement
import com.company.admob.sample.compose.R
import com.company.admob.sample.compose.ads.SampleAds
import com.company.admob.views.AdShimmerView
import com.company.admob.views.NativeTemplates

/**
 * Every native style, each with a live test ad: the library's templates, then the sample's own layout registered as
 * "sample_promo" (SampleApplication). "Show the shimmers" swaps each ad for the shimmer its layout gets while loading.
 * The styles use SampleAds.ArticleNative's ad units; each style is its own slot (`slotKey`).
 * The screen keeps every style's ad while it's open: scrolling back shows the same ads.
 */
@Composable
fun NativeStylesScreen() {
    val styles: List<Pair<String, NativeAdLayout>> = remember {
        NativeStyle.entries.map { it.name.lowercase() to NativeTemplates.forStyle(it) } +
            ("sample_promo (the app's own layout)" to checkNotNull(NativeTemplates.registered("sample_promo")))
    }
    var showingShimmers by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Button(onClick = { showingShimmers = !showingShimmers }, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(if (showingShimmers) R.string.native_styles_show_ads else R.string.native_styles_show_shimmers))
            }
        }
        items(styles, key = { it.first }) { (name, layout) ->
            Text(name, Modifier.padding(top = 20.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge)
            if (showingShimmers) {
                Shimmer(layout)
            } else {
                AdNative(SampleAds.ArticleNative.nativePlacement(), Modifier.fillMaxWidth(), layout = layout, slotKey = name)
            }
        }
    }
}

/** The shimmer [layout] gets while its ad loads, shown on its own. */
@OptIn(InternalCompanyAdMobApi::class) // AdShimmerView: shown here only to preview each layout's shimmer.
@Composable
private fun Shimmer(layout: NativeAdLayout) {
    AndroidView(
        factory = { AdShimmerView(it) },
        modifier = Modifier.fillMaxWidth(),
        update = { it.showLayout(layout) },
    )
}
