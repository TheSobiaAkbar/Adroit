package io.github.thesobiaakbar.adroit.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `adroit.android.library`: settings shared by every published library module
 * (see docs/architecture/02_ARCHITECTURE.md §5 and §7).
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.library")

        group = requiredProperty("adroit.group")
        version = requiredProperty("adroit.version")

        extensions.configure<LibraryExtension> {
            compileSdk = libs.intVersion("compileSdk")
            resourcePrefix = requiredProperty("adroit.resourcePrefix")

            defaultConfig {
                minSdk = libs.intVersion("minSdk")
                consumerProguardFiles("consumer-rules.pro")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                aarMetadata {
                    minCompileSdk = libs.intVersion("consumerMinCompileSdk")
                }
            }
            compileOptions {
                sourceCompatibility = JAVA_TARGET
                targetCompatibility = JAVA_TARGET
            }
            testOptions {
                unitTests.isIncludeAndroidResources = true
            }
            lint {
                abortOnError = true
                warningsAsErrors = false
                checkDependencies = false
                baseline = file("lint-baseline.xml")
                // SARIF next to the HTML and XML reports, for CI code scanning (PROMPT 22).
                sarifReport = true
            }
        }

        configureKotlin(library = true)
        registerDependencyGuard()
        registerApiValidation()
    }
}
