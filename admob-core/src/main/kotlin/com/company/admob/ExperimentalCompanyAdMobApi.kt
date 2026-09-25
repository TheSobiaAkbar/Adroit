package com.company.admob

/**
 * Marks CompanyAdMob API that is not yet stable. It may change or be removed in a minor version.
 * Opt in with `@OptIn(ExperimentalCompanyAdMobApi::class)`.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This CompanyAdMob API is experimental and may change in a minor version.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.TYPEALIAS)
public annotation class ExperimentalCompanyAdMobApi
