package com.company.admob.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `companyadmob.publishing`: Maven publication of library modules.
 *
 * Intentionally empty until PROMPT 21 (docs/implementation-prompts/PROMPT_21_maven_publishing.md).
 */
class PublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // TODO(PROMPT-21): maven-publish, sources/javadoc jars, POM metadata, repositories.
    }
}
