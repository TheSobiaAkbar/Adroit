import java.util.Properties

plugins {
    alias(libs.plugins.companyadmob.android.sample.compose)
    // Reads google-services.json (Firebase project sample-ads-plugin, app com.company.admob.sample.mixed).
    alias(libs.plugins.companyadmob.android.firebase)
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
    // region mediation-exclusion (quoted in docs/MEDIATION.md)
    configurations.configureEach {
        exclude(group = "com.google.android.gms", module = "play-services-ads")
        exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
    }
    // endregion
}

/**
 * Test device IDs for the `staging` build, from `local.properties` (never committed):
 * `companyAdmob.sample.testDeviceIds=33BE2250B43518CCDA7DE426D04EE231,…`. Empty by default. Logcat prints a device's
 * ID the first time it requests an ad.
 */
val testDeviceIds: List<String> = rootProject.file("local.properties").takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    ?.getProperty("companyAdmob.sample.testDeviceIds")
    ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
    .orEmpty()

android {
    namespace = "com.company.admob.sample.mixed"
    defaultConfig {
        applicationId = "com.company.admob.sample.mixed"
        buildConfigField("boolean", "WITH_MEDIATION", withMediation.toString())
        buildConfigField(
            "String[]",
            "TEST_DEVICE_IDS",
            testDeviceIds.joinToString(prefix = "{", postfix = "}") { "\"$it\"" },
        )
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    buildTypes {
        // Like debug (debuggable, not minified), but configured as a tester's build: STAGING (see SampleAdsConfig).
        create("staging") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
        }
    }
}

dependencies {
    // admob-compose brings admob-views (and admob-core): one dependency for the XML views and the composables.
    implementation(project(":admob-compose"))
    // Remote Config and Analytics (Firebase BoM, from admob-firebase); Firebase itself starts from google-services.json.
    implementation(project(":admob-firebase"))

    // Sample-only UI libraries. The CompanyAdMob libraries themselves never depend on these.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // A host dependency only: the CompanyAdMob libraries contain no mediation code.
    if (withMediation) implementation(libs.applovin.mediation.adapter)

    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    // Compose ui-test brings Espresso 3.5.0, whose reflection on InputManager.getInstance fails on Android 17.
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.leakcanary.android.instrumentation)
}
