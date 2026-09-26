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

rootProject.name = "CompanyAdMob"

// Published libraries (docs/architecture/02_ARCHITECTURE.md §7)
include(":admob-core")
include(":admob-views")
include(":admob-compose")
include(":admob-testing")

// Samples (never published)
include(":sample-xml")
include(":sample-compose")
include(":sample-mixed")

// Compiled documentation snippets (docs/*.md), never published: ./gradlew -PincludeDocsSnippets :docs-snippets:build
if (providers.gradleProperty("includeDocsSnippets").isPresent) {
    include(":docs-snippets")
}
