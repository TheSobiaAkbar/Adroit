package io.github.thesobiaakbar.adroit

/**
 * Marks API that exists only so Adroit's own modules (`adroit-views`, `adroit-compose`) can reach into core
 * (ADR-012). It is not for apps: it may change or disappear in any release, without notice. The opt-in is an error,
 * so using it by accident doesn't compile.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Internal to Adroit's own modules. Not for apps; it may change in any release.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalAdroitApi
