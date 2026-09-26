plugins {
    alias(libs.plugins.companyadmob.android.sample.compose)
}

/**
 * `-PwithMediation=true` adds one AdMob Mediation adapter (AppLovin) and the global exclusion of the legacy Google
 * Mobile Ads SDK that every adapter still depends on. Off by default: then neither is present. See MEDIATION_DEMO.md.
 *
 * `-PmediationSkipExclusion=true` (with `withMediation`) leaves the exclusion out, only to reproduce the
 * duplicate-class failure documented in MEDIATION_DEMO.md. Never use it otherwise.
 */
val withMediation = providers.gradleProperty("withMediation").map(String::toBoolean).getOrElse(false)
val skipExclusion = providers.gradleProperty("mediationSkipExclusion").map(String::toBoolean).getOrElse(false)

if (withMediation && !skipExclusion) {
    // Required by the Next-Gen SDK for any mediation adapter: it already contains the classes adapters need, and
    // the legacy artifacts would duplicate them. Applies to every configuration, so no classpath can resolve them.
    configurations.configureEach {
        exclude(group = "com.google.android.gms", module = "play-services-ads")
        exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
    }
}

android {
    namespace = "com.company.admob.sample.mixed"
    defaultConfig {
        applicationId = "com.company.admob.sample.mixed"
        buildConfigField("boolean", "WITH_MEDIATION", withMediation.toString())
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(project(":admob-views"))
    implementation(project(":admob-compose"))

    // Sample-only UI libraries. The CompanyAdMob libraries themselves never depend on these.
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // A host dependency only: the CompanyAdMob libraries contain no mediation code.
    if (withMediation) implementation(libs.applovin.mediation.adapter)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.androidx.espresso.core)
}
