package com.company.admob.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `companyadmob.android.library.compose`: a library module that contains Compose code. */
class ComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(AndroidLibraryConventionPlugin::class.java)
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true
        }
        dependencies {
            add("implementation", platform(libs.findLibrary("compose-bom").get()))
            add("testImplementation", platform(libs.findLibrary("compose-bom").get()))
        }
    }
}
