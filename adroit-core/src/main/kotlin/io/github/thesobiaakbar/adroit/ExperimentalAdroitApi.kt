package io.github.thesobiaakbar.adroit

/**
 * Marks Adroit API that is not yet stable. It may change or be removed in a minor version.
 * Opt in with `@OptIn(ExperimentalAdroitApi::class)`.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This Adroit API is experimental and may change in a minor version.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.TYPEALIAS)
public annotation class ExperimentalAdroitApi
