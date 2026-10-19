import java.util.Properties

plugins {
    alias(libs.plugins.adroit.android.sample)
    // Reads google-services.json (Firebase project sample-ads-plugin, app io.github.thesobiaakbar.adroit.sample.xml).
    alias(libs.plugins.adroit.android.firebase)
}

/**
 * Test device IDs for the `staging` build, from `local.properties` (never committed):
 * `adroit.sample.testDeviceIds=33BE2250B43518CCDA7DE426D04EE231,…`. Empty by default. Logcat prints a device's
 * ID the first time it requests an ad.
 */
val testDeviceIds: List<String> = rootProject.file("local.properties").takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    ?.getProperty("adroit.sample.testDeviceIds")
    ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
    .orEmpty()

android {
    namespace = "io.github.thesobiaakbar.adroit.sample.xml"
    defaultConfig {
        applicationId = "io.github.thesobiaakbar.adroit.sample.xml"
        buildConfigField(
            "String[]",
            "TEST_DEVICE_IDS",
            testDeviceIds.joinToString(prefix = "{", postfix = "}") { "\"$it\"" },
        )
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // FirebaseTemplateTest rewrites firebase/remote_config_template.json with -PupdateFirebaseTemplate.
        unitTests.all { it.systemProperty("updateFirebaseTemplate", providers.gradleProperty("updateFirebaseTemplate").isPresent.toString()) }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    // Instrumented tests (smoke and leak tests) run against R8-processed code: connectedMinifiedDebugAndroidTest.
    testBuildType = "minifiedDebug"
    buildTypes {
        getByName("minifiedDebug") {
            // R8 also processes the test APK of a minified test build type; see the file for the failure it fixes.
            testProguardFiles("proguard-test-rules.pro")
            // What the test APK needs from the app's own classpath; see the file. Not applied to release.
            proguardFile("proguard-minified-debug-rules.pro")
        }
        // Like debug (debuggable, not minified), but configured as a tester's build: STAGING (see SampleAdsConfig).
        // No applicationId suffix: google-services.json registers only io.github.thesobiaakbar.adroit.sample.xml.
        create("staging") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
        }
    }
}

dependencies {
    implementation(project(":adroit-core"))
    implementation(project(":adroit-views"))
    // Remote Config and Analytics (Firebase BoM, from adroit-firebase); Firebase itself starts from google-services.json.
    implementation(project(":adroit-firebase"))
    // In-app purchases: the paywalls (Views), with their Firebase keys and Analytics events.
    implementation(project(":adroit-billing"))
    implementation(project(":adroit-billing-views"))
    implementation(project(":adroit-billing-firebase"))

    // Sample-only UI libraries. The Adroit libraries themselves never depend on these.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.leakcanary.android.instrumentation)
}
