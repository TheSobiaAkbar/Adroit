package com.company.admob.sample.compose.ui.screens

import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.NativeAdAssets
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdRenderer
import com.company.admob.compose.AdNative
import com.company.admob.sample.compose.AppContainer
import com.company.admob.sample.compose.R
import com.company.admob.sample.compose.ads.Placements
import com.company.admob.sample.compose.ui.components.Hint
import com.company.admob.sample.compose.ui.components.ScreenColumn
import com.company.admob.sample.compose.ui.components.Section
import com.company.admob.views.NativeTemplates

/**
 * Native ads are kept by this destination (its NavBackStackEntry), so rotation re-renders the same ads: the load
 * counter at the top doesn't move. Leaving the screen destroys them (Destroyed events). A screen keeps at most 3
 * native ads, so each tab shows at most 3 at a time; switching tabs can replace the previous tab's ads.
 */
@Composable
fun NativeScreen(container: AppContainer) {
    val loads by container.eventLog.nativeLoads.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var recompositions by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Native loads: ${loads.requests} requested, ${loads.loaded} loaded",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag(LOAD_COUNTER_TAG),
            )
            Hint("Counted from LoadRequested/Loaded events, all native placements. Rotation must not add any.")
            // The performance audit's recomposition check (docs/audits/PERFORMANCE_AUDIT.md, measurement 6): each tap
            // recomposes this screen; the native ads must skip, and the counter above must not move.
            TextButton(onClick = { recompositions++ }, Modifier.testTag("recompose")) {
                Text("Recompose screen ($recompositions)")
            }
        }
        PrimaryTabRow(selectedTabIndex = tab) {
            NativeTab.entries.forEachIndexed { index, it ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(it.title) })
            }
        }
        when (NativeTab.entries[tab]) {
            NativeTab.Templates -> TemplatesTab()
            NativeTab.Feed -> FeedTab()
            NativeTab.Custom -> CustomTab()
        }
    }
}

const val LOAD_COUNTER_TAG = "native_load_counter"
const val FEED_TAG = "native_feed"

private enum class NativeTab(val title: String) { Templates("Templates"), Feed("Feed"), Custom("Custom") }

@Composable
private fun TemplatesTab() {
    ScreenColumn {
        Section("Small") {
            AdNative(Placements.TemplatesNative, layout = NativeTemplates.Small, slotKey = "small", placeholder = { Loading() })
        }
        Section("Medium") {
            AdNative(Placements.TemplatesNative, layout = NativeTemplates.Medium, slotKey = "medium", placeholder = { Loading() })
        }
        Section("Large") {
            AdNative(Placements.TemplatesNative, layout = NativeTemplates.Large, slotKey = "large", placeholder = { Loading() })
        }
    }
}

/** A native ad after every 6 articles. Each ad item has a stable key, which is its slot. */
@Composable
private fun FeedTab() {
    val rows = rememberSaveable { feedRows() }
    LazyColumn(
        Modifier.fillMaxSize().testTag(FEED_TAG),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(rows, key = { it }) { key ->
            if (key.startsWith(AD_PREFIX)) {
                AdNative(
                    placement = Placements.FeedNative,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    layout = NativeTemplates.Small,
                    slotKey = key,
                )
            } else {
                Text(key, Modifier.fillMaxWidth().height(72.dp).padding(16.dp))
            }
            HorizontalDivider()
        }
    }
}

private const val AD_PREFIX = "ad-"

private fun feedRows(): List<String> = (1..42).flatMap { article ->
    if (article % 6 == 0) listOf("Article $article", "$AD_PREFIX${article / 6}") else listOf("Article $article")
}

@Composable
private fun CustomTab() {
    ScreenColumn {
        Section("Custom layout and renderer") {
            Hint(
                "The app's own layout (res/layout/playground_native_custom.xml) with its own IDs, described by a " +
                    "NativeAdLayout. The renderer then styles the bound views and shows the rating as text.",
            )
            AdNative(
                placement = Placements.CustomNative,
                layout = CustomNativeLayout,
                renderer = CustomRenderer,
                placeholder = { Loading() },
            )
        }
    }
}

private val CustomNativeLayout = NativeAdLayout(
    layoutRes = R.layout.playground_native_custom,
    headlineId = R.id.custom_native_headline,
    bodyId = R.id.custom_native_body,
    callToActionId = R.id.custom_native_cta,
    iconId = R.id.custom_native_icon,
    attributionId = R.id.custom_native_attribution,
)

/** Runs after the standard binding. Styles views only: the library owns clicks and impressions. */
private val CustomRenderer = NativeAdRenderer { root: View, assets: NativeAdAssets ->
    root.findViewById<TextView>(R.id.custom_native_headline).setTypeface(null, Typeface.BOLD)
    root.findViewById<TextView>(R.id.custom_native_rating).text =
        assets.starRating?.let { root.context.getString(R.string.native_rating, it) }.orEmpty()
}

@Composable
private fun Loading() {
    LinearProgressIndicator(Modifier.fillMaxWidth())
}
