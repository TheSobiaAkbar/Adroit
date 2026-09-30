package com.company.admob.sample.mixed.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.company.admob.compose.AdBanner
import com.company.admob.compose.AdNative
import com.company.admob.config.bannerPlacement
import com.company.admob.config.nativePlacement
import com.company.admob.sample.mixed.R
import com.company.admob.sample.mixed.ads.SampleAds

/** A row of the feed. */
sealed interface FeedRow {
    val key: String

    data class Article(val index: Int) : FeedRow {
        override val key: String get() = "article_$index"
    }

    /** A native ad slot; [slot] is its `slotKey`, so the slot keeps its own ad. */
    data class Ad(val slot: Int) : FeedRow {
        override val key: String get() = "ad_$slot"
    }

    /** A banner slot, kept the same way. */
    data class Banner(val slot: Int) : FeedRow {
        override val key: String get() = "banner_$slot"
    }
}

/** 30 articles with an ad at every 8th position, native and banner in turn. */
val feedRows: List<FeedRow> = buildList {
    var article = 0
    var position = 0
    while (article < 30) {
        when {
            position % 8 != 7 -> add(FeedRow.Article(article++))
            position / 8 % 2 == 0 -> add(FeedRow.Ad(slot = position))
            else -> add(FeedRow.Banner(slot = position))
        }
        position++
    }
}

const val FEED_TAG = "feed"

/**
 * A screen already moved to Compose, in the XML app's Fragment navigation: the Fragment's view is a [ComposeView].
 *
 * - `DisposeOnViewTreeLifecycleDestroyed`: the composition ends with the Fragment's view.
 * - The native ads and banners are kept in the Fragment's ViewModel store (the ComposeView's
 *   `LocalViewModelStoreOwner`): the same ads after scrolling and rotation, destroyed when the Fragment is gone for
 *   good.
 */
class FeedFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { MaterialTheme { FeedScreen() } }
        }
}

@Composable
private fun FeedScreen() {
    LazyColumn(Modifier.fillMaxSize().testTag(FEED_TAG)) {
        items(feedRows, key = FeedRow::key, contentType = { it::class }) { row ->
            when (row) {
                is FeedRow.Article -> {
                    Text(stringResource(R.string.feed_article, row.index + 1), Modifier.fillMaxWidth().padding(16.dp))
                    HorizontalDivider()
                }
                // No layout: FeedNative's style (small, in SampleAds) picks the template.
                is FeedRow.Ad -> AdNative(
                    SampleAds.FeedNative.nativePlacement(),
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    slotKey = row.slot,
                )
                is FeedRow.Banner -> AdBanner(
                    SampleAds.FeedBanner.bannerPlacement(),
                    Modifier.padding(vertical = 4.dp),
                    slotKey = row.slot,
                )
            }
        }
    }
}
