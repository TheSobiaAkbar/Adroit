plugins {
    // Versions come from settings.gradle.kts: by default AGP 9.1.1 with its built-in Kotlin 2.2.10, not the 2.4.20
    // the libraries are built with, so this also checks that a host on an older Kotlin can consume them (ADR-010:
    // metadata targets Kotlin 2.2). The compatibility matrix (README.md) overrides both with Gradle properties.
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}
