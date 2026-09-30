package io.github.thesobiaakbar.adroit.sample.xml.feed

import androidx.lifecycle.ViewModel
import io.github.thesobiaakbar.adroit.config.nativePlacement
import io.github.thesobiaakbar.adroit.sample.xml.ads.SampleAds
import io.github.thesobiaakbar.adroit.views.NativeAdHolder

/** A row of the feed. */
sealed interface FeedRow {
    data class Article(val index: Int) : FeedRow

    /** A native ad slot; [slot] identifies its [NativeAdHolder]. */
    data class Ad(val slot: Int) : FeedRow
}

/**
 * The feed: 30 articles with a native ad after every 7 (at every 8th position). One [NativeAdHolder] per ad
 * position, owned here, so each position keeps its ad across rotation and scrolling, without reloading, and the ads
 * are destroyed only when the screen is gone for good ([onCleared]).
 */
class FeedViewModel : ViewModel() {
    val rows: List<FeedRow> = buildList {
        var article = 0
        var position = 0
        while (article < ARTICLES) {
            if (position % AD_EVERY == AD_EVERY - 1) add(FeedRow.Ad(slot = position)) else add(FeedRow.Article(article++))
            position++
        }
    }

    private val holders = mutableMapOf<Int, NativeAdHolder>()

    fun adHolder(slot: Int): NativeAdHolder = holders.getOrPut(slot) { NativeAdHolder(SampleAds.FeedNative.nativePlacement()) }

    override fun onCleared() {
        holders.values.forEach(NativeAdHolder::clear)
        holders.clear()
    }

    private companion object {
        const val ARTICLES = 30
        const val AD_EVERY = 8
    }
}
