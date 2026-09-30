package io.github.thesobiaakbar.adroit

/**
 * Marks API that exposes Google Mobile Ads SDK behaviour or types. It bypasses Adroit's isolation from the
 * Google SDK and may break on any Google SDK upgrade, including in a Adroit patch release.
 * Opt in with `@OptIn(GoogleSdkEscapeHatch::class)` only when there is no supported alternative.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Exposes Google Mobile Ads SDK types; may break on any Google SDK upgrade.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.TYPEALIAS)
public annotation class GoogleSdkEscapeHatch
