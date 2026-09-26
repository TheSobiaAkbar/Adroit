plugins {
    // AGP 9 with its built-in Kotlin (2.2.10), not the 2.4.20 the libraries are built with: this also checks that a
    // host on an older Kotlin can consume the libraries (ADR-010: metadata targets Kotlin 2.2).
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
