pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Adroit"

// Published libraries (docs/architecture/02_ARCHITECTURE.md §7)
include(":adroit-core")
include(":adroit-views")
include(":adroit-compose")
include(":adroit-testing")
include(":adroit-firebase")

// Samples (never published)
include(":sample-xml")
include(":sample-compose")

// Compiled documentation snippets (docs/*.md), never published: ./gradlew -PincludeDocsSnippets :docs-snippets:build
if (providers.gradleProperty("includeDocsSnippets").isPresent) {
    include(":docs-snippets")
}
