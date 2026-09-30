package io.github.thesobiaakbar.adroit.docs;

import io.github.thesobiaakbar.adroit.BannerPlacement;
import io.github.thesobiaakbar.adroit.InterstitialPlacement;
import io.github.thesobiaakbar.adroit.config.AdEnvironment;
import io.github.thesobiaakbar.adroit.config.AdMobConfig;
import io.github.thesobiaakbar.adroit.config.AgeTreatment;
import io.github.thesobiaakbar.adroit.logging.LogLevel;

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
