package com.company.admob;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.view.View;

import com.company.admob.config.AdMobConfig;

import kotlin.Unit;

/**
 * The Java-supported surface (ADR-013), compiled with the unit tests: if a change makes one of these calls
 * impossible from Java, the build fails. Never run. Kotlin function-type callbacks return {@code Unit.INSTANCE}.
 */
@SuppressWarnings("unused")
final class JavaFacadeCalls {
    private JavaFacadeCalls() {
    }

    static void setUp(Application application, Context context, int adsFileResId) {
        AdMobConfig.Builder builder = new AdMobConfig.Builder();
        builder.profiles(context, adsFileResId);
        CompanyAdMob.initialize(application, builder.build());
    }

    static void useAds(Activity activity) {
        AdsClient ads = CompanyAdMob.getClient();
        ads.start(activity, state -> Unit.INSTANCE);
        ads.start(activity, null);
        boolean ready = ads.isReady();
        ads.setAdFree(false);
        ads.preload(new InterstitialPlacement("level_complete"), new RewardedPlacement("extra_life"));

        InterstitialPlacement interstitial = new InterstitialPlacement("level_complete");
        ads.getInterstitial().show(activity, interstitial, result -> {
            boolean shown = result.getWasShown();
            return Unit.INSTANCE;
        });
        ads.getInterstitial().showOnSplash(activity, new InterstitialPlacement("splash"), result -> Unit.INSTANCE);
        ads.showInterstitial(activity, interstitial, result -> Unit.INSTANCE);
        boolean interstitialReady = ads.getInterstitial().isReady(interstitial);

        ads.getRewarded().show(activity, new RewardedPlacement("extra_life"), new RewardedShowOptions(), result -> {
            if (result instanceof RewardResult.Earned) {
                String rewardId = ((RewardResult.Earned) result).getRewardId();
            }
            return Unit.INSTANCE;
        });
        ads.showRewarded(activity, new RewardedPlacement("extra_life"), result -> Unit.INSTANCE);
        ads.getRewardedInterstitial().show(
                activity, new RewardedInterstitialPlacement("bonus_round"), new RewardedShowOptions("user-42"),
                result -> Unit.INSTANCE);

        ads.getAppOpen().showOnColdStart(activity, 4_000L, result -> Unit.INSTANCE);
        ads.getAppOpen().skipNextForeground();
        ads.getAppOpen().setEnabled(true);

        BannerAdHandle banner = ads.getBanner().create(activity, new BannerPlacement("home_banner"), new BannerOptions());
        BannerOptions sized = new BannerOptions(new BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM));
        View bannerView = banner.getView();
        banner.destroy();

        ads.getNative().load(new NativePlacement("feed_native"), result -> {
            if (result instanceof NativeAdResult.Loaded) {
                ((NativeAdResult.Loaded) result).getHandle().destroy();
            }
            return Unit.INSTANCE;
        });

        ads.getPrivacy().showPrivacyOptions(activity, error -> Unit.INSTANCE);
        boolean canRequestAds = ads.getPrivacy().getConsentState().getValue().getCanRequestAds();
        ads.getDiagnostics().openAdInspector(activity, error -> Unit.INSTANCE);
        String report = ads.getDiagnostics().snapshot().toString();

        AdError error = new AdError.Internal("detail");
        String code = error.getCode();
    }
}
