package com.company.admob

/**
 * Marks API that exists only so CompanyAdMob's own modules (`admob-views`, `admob-compose`) can reach into core
 * (ADR-012). It is not for apps: it may change or disappear in any release, without notice. The opt-in is an error,
 * so using it by accident doesn't compile.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Internal to CompanyAdMob's own modules. Not for apps; it may change in any release.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalCompanyAdMobApi
