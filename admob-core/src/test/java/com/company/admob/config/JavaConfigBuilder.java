package com.company.admob.config;

import com.company.admob.AppOpenPlacement;
import com.company.admob.BannerPlacement;
import com.company.admob.InterstitialPlacement;
import com.company.admob.NativePlacement;
import com.company.admob.RewardedInterstitialPlacement;
import com.company.admob.RewardedPlacement;
import com.company.admob.events.AdEventListener;
import com.company.admob.logging.LogLevel;

/** Proves the configuration is buildable from Java without Kotlin lambdas. Used by AdMobConfigDslTest. */
final class JavaConfigBuilder {
    private JavaConfigBuilder() {
    }

    static AdMobConfig build(AdEventListener listener) {
        AdMobConfig.Builder builder = new AdMobConfig.Builder()
                .environment(AdEnvironment.STAGING)
                .appId("ca-app-pub-1234567890123456~1234567890")
                .addTestDeviceId("33BE2250B43518CCDA7DE426D04EE231")
                .addEventListener(listener)
                .addBanner(new BannerPlacement("home_banner"), "ca-app-pub-1234567890123456/0000000001")
                .addInterstitial(new InterstitialPlacement("level_complete"), "ca-app-pub-1234567890123456/0000000002")
                .addRewarded(new RewardedPlacement("extra_life"), "ca-app-pub-1234567890123456/0000000003")
                .addRewardedInterstitial(
                        new RewardedInterstitialPlacement("bonus_round"), "ca-app-pub-1234567890123456/0000000004")
                .addAppOpen(new AppOpenPlacement("app_open"), "ca-app-pub-1234567890123456/0000000005")
                .addNative(new NativePlacement("article_native"), "ca-app-pub-1234567890123456/0000000006");
        builder.getTesting().setUseDemoAdUnits(false);
        builder.getPrivacy().setAgeTreatment(AgeTreatment.TEEN);
        builder.getPrivacy().setMaxAdContentRating(MaxAdContentRating.T);
        builder.getLogging().setLevel(LogLevel.INFO);
        builder.getAdvanced().setFailFast(false);
        return builder.build();
    }
}
