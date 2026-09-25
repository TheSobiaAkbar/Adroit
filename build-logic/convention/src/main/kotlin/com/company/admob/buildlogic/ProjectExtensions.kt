package com.company.admob.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.intVersion(alias: String): Int =
    findVersion(alias).orElseThrow { IllegalStateException("Missing version '$alias' in libs.versions.toml") }
        .requiredVersion.toInt()

internal fun Project.requiredProperty(name: String): String =
    providers.gradleProperty(name).orNull ?: error("Missing Gradle property '$name' (see gradle.properties)")

/** Java/Kotlin bytecode target for every module (ADR-010): the lowest the Next-Gen SDK supports. */
internal val JAVA_TARGET: JavaVersion = JavaVersion.VERSION_11

/**
 * Oldest Kotlin language/API version library metadata targets (ADR-010).
 *
 * Kotlin compilers read metadata one version ahead, so 2.2 metadata is consumable by Kotlin 2.1+ hosts.
 * 2.1 itself is deprecated by the Kotlin 2.4 compiler (a warning, fatal under allWarningsAsErrors).
 */
private val LIBRARY_KOTLIN_VERSION = KotlinVersion.KOTLIN_2_2

/** kotlin-stdlib version libraries depend on; must match [LIBRARY_KOTLIN_VERSION] so consumers aren't forced up. */
private const val LIBRARY_STDLIB_VERSION = "2.2.0"

/**
 * Kotlin settings shared by all modules.
 *
 * AGP 9 compiles Kotlin with its built-in Kotlin support (no `org.jetbrains.kotlin.android` plugin); it still
 * registers the standard `kotlin` extension ([KotlinAndroidProjectExtension]).
 */
internal fun Project.configureKotlin(library: Boolean) {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            if (library) {
                languageVersion.set(LIBRARY_KOTLIN_VERSION)
                apiVersion.set(LIBRARY_KOTLIN_VERSION)
                allWarningsAsErrors.set(true)
                // New library: interface default members compile to JVM default methods only, without the
                // legacy `DefaultImpls` compatibility classes that would otherwise become part of the binary API.
                jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
            }
        }
        if (library) {
            explicitApi()
            coreLibrariesVersion = LIBRARY_STDLIB_VERSION
        }
    }
}

