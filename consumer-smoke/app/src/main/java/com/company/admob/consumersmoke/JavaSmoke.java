package com.company.admob.consumersmoke;

import android.app.Activity;

import com.company.admob.AdsClient;
import com.company.admob.BannerOptions;
import com.company.admob.BannerPlacement;
import com.company.admob.CompanyAdMob;
import com.company.admob.InterstitialPlacement;

import kotlin.Unit;

/** A Java caller of the published artifacts (ADR-013): compiling it is the check. */
final class JavaSmoke {
    private JavaSmoke() {
    }

    static void use(Activity activity) {
        AdsClient ads = CompanyAdMob.getClient();
        ads.start(activity, null);
        ads.getInterstitial().show(activity, new InterstitialPlacement("smoke_interstitial"), result -> Unit.INSTANCE);
        ads.getAppOpen().showOnColdStart(activity, 4_000L, result -> Unit.INSTANCE);
        ads.getBanner().create(activity, new BannerPlacement("smoke_banner"), new BannerOptions()).destroy();
    }
}
