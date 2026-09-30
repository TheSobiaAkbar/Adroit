package io.github.thesobiaakbar.adroit.consumersmoke;

import android.app.Activity;

import io.github.thesobiaakbar.adroit.AdsClient;
import io.github.thesobiaakbar.adroit.BannerOptions;
import io.github.thesobiaakbar.adroit.BannerPlacement;
import io.github.thesobiaakbar.adroit.Adroit;
import io.github.thesobiaakbar.adroit.InterstitialPlacement;

import kotlin.Unit;

/** A Java caller of the published artifacts (ADR-013): compiling it is the check. */
final class JavaSmoke {
    private JavaSmoke() {
    }

    static void use(Activity activity) {
        AdsClient ads = Adroit.getClient();
        ads.start(activity, null);
        ads.getInterstitial().show(activity, new InterstitialPlacement("smoke_interstitial"), result -> Unit.INSTANCE);
        ads.getAppOpen().showOnColdStart(activity, 4_000L, result -> Unit.INSTANCE);
        ads.getBanner().create(activity, new BannerPlacement("smoke_banner"), new BannerOptions()).destroy();
    }
}
