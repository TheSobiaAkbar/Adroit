package com.company.admob.docs;

import com.company.admob.BannerPlacement;
import com.company.admob.InterstitialPlacement;
import com.company.admob.config.AdEnvironment;
import com.company.admob.config.AdMobConfig;
import com.company.admob.config.AgeTreatment;
import com.company.admob.logging.LogLevel;

final class JavaConfiguration {
    private JavaConfiguration() {
    }

    // region java-builder
    static AdMobConfig create() {
        AdMobConfig.Builder builder = new AdMobConfig.Builder()
                .environment(AdEnvironment.PRODUCTION)
                .addBanner(new BannerPlacement("home_banner"), "ca-app-pub-0000000000000000/0000000001")
                .addInterstitial(new InterstitialPlacement("level_complete"), "ca-app-pub-0000000000000000/0000000002");
        builder.getPrivacy().setAgeTreatment(AgeTreatment.UNSPECIFIED);
        builder.getLogging().setLevel(LogLevel.WARN);
        return builder.build();
    }
    // endregion
}
