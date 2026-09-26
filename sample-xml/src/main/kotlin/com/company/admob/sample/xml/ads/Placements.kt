package com.company.admob.sample.xml.ads

import com.company.admob.AppOpenPlacement
import com.company.admob.BannerPlacement
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement

/** Every place this app shows an ad. Declared once; the keys match XML attributes and remote overrides. */
object Placements {
    val HomeBanner = BannerPlacement("home_banner")
    val DetailsBanner = BannerPlacement("details_banner")
    val LevelCompleteInterstitial = InterstitialPlacement("level_complete")
    val ExtraLifeRewarded = RewardedPlacement("extra_life")
    val BonusRewardedInterstitial = RewardedInterstitialPlacement("bonus_round")
    val AppOpen = AppOpenPlacement("app_open")
    val FeedNative = NativePlacement("feed_native")
    val ArticleNative = NativePlacement("article_native")
}
