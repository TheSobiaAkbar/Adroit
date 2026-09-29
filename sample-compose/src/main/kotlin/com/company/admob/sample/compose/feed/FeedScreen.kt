package com.company.admob.sample.compose.feed

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.company.admob.compose.AdNative
import com.company.admob.config.nativePlacement
import com.company.admob.sample.compose.R
import com.company.admob.sample.compose.ads.SampleAds

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
}

/** 30 articles with a native ad after every 9 (at every 10th position): 3 ad slots, the most a screen keeps. */
val feedRows: List<FeedRow> = buildList {
    var article = 0
    var position = 0
    while (article < 30) {
        if (position % 10 == 9) add(FeedRow.Ad(slot = position)) else add(FeedRow.Article(article++))
        position++
    }
}

const val FEED_TAG = "feed"

/**
 * The list pattern for native ads: each ad row is an [AdNative] with its own `slotKey`. The ads are kept by this
 * destination (its ViewModel store), so scrolling away and back, or rotating, shows the same ad without reloading;
 * they're destroyed when the destination is gone for good. A screen keeps at most 3 native ads, hence 3 slots.
 */
@Composable
fun FeedScreen() {
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
            }
        }
    }
}
