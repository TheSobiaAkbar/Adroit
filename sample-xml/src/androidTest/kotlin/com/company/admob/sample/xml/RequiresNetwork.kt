package com.company.admob.sample.xml

/**
 * The test loads real Google test ads, so it needs network access. The nightly CI job runs these with
 * `-Pandroid.testInstrumentationRunnerArguments.annotation=com.company.admob.sample.xml.RequiresNetwork`; offline
 * runs can exclude them with `notAnnotation`. Every such test must also pass when no ad loads.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class RequiresNetwork
