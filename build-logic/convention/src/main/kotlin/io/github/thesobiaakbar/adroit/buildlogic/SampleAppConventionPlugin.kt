package io.github.thesobiaakbar.adroit.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `adroit.android.sample`: sample/playground apps. Never published.
 *
 * The AdMob app ID is injected through the `admobAppId` manifest placeholder. It defaults to Google's official
 * sample app ID and can be overridden with the Gradle property `adroit.sample.appId`, for example in
 * `~/.gradle/gradle.properties`. Never commit a real app ID to this repository.
 */
class SampleAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.intVersion("compileSdk")
            defaultConfig {
                minSdk = libs.intVersion("minSdk")
                targetSdk = libs.intVersion("targetSdk")
                versionCode = 1
                versionName = requiredProperty("adroit.version")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                manifestPlaceholders["admobAppId"] =
                    providers.gradleProperty("adroit.sample.appId").orNull ?: GOOGLE_SAMPLE_APP_ID
            }
            buildTypes {
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    // Samples only: debug-sign release builds so minified builds can be installed locally.
                    signingConfig = signingConfigs.getByName("debug")
                }
                // The release build (R8 minification and resource shrinking) made debuggable, so instrumented tests
                // can run against R8-processed code (PROMPT 20). Libraries fall back to their release variant.
                create("minifiedDebug") {
                    initWith(getByName("release"))
                    isDebuggable = true
                    matchingFallbacks += "release"
                }
            }
            lint {
                // SARIF next to the HTML and XML reports, for CI code scanning (PROMPT 22).
                sarifReport = true
            }
            compileOptions {
                sourceCompatibility = JAVA_TARGET
                targetCompatibility = JAVA_TARGET
            }
        }

        configureKotlin(library = false)
        registerDependencyGuard()

        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
        }
    }

    private companion object {
        /** Google's official sample AdMob app ID (https://developers.google.com/admob/android/next-gen/quick-start). */
        const val GOOGLE_SAMPLE_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    }
}

/** `adroit.android.sample.compose`: a sample app that uses Compose. */
class ComposeSampleAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(SampleAppConventionPlugin::class.java)
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<ApplicationExtension> {
            buildFeatures.compose = true
        }
        dependencies {
            add("implementation", platform(libs.findLibrary("compose-bom").get()))
        }
    }
}
