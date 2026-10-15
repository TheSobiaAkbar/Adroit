plugins {
    // ABI validation is configured per library module in build-logic (KGP built-in, ADR-007).
}

// Maven Central (docs/architecture/PUBLISHING_NOTES.md). Each library publishes, signed, into build/central-bundle;
// this zips the current version's files into the bundle the Central Portal takes, and the release workflow uploads it.
val publishedLibraries = listOf("adroit-core", "adroit-views", "adroit-compose", "adroit-testing", "adroit-firebase")
val adroitVersion = providers.gradleProperty("adroit.version").get()

tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Builds build/central-bundle.zip, the signed upload for the Maven Central Portal."
    dependsOn(publishedLibraries.map { ":$it:publishReleasePublicationToCentralBundleRepository" })
    from(layout.buildDirectory.dir("central-bundle")) {
        // Only this version: an earlier run's files stay in the folder.
        include("io/github/thesobiaakbar/*/$adroitVersion/**")
        // Gradle's own checksums of maven-metadata.xml are not part of a release.
        exclude("**/maven-metadata.xml*")
    }
    archiveFileName.set("central-bundle.zip")
    destinationDirectory.set(layout.buildDirectory)
    val signingKey = providers.environmentVariable("SIGNING_KEY")
    doFirst {
        // Central rejects unsigned files and snapshot versions; say so here rather than after the upload.
        check(!signingKey.orNull.isNullOrBlank()) { "Maven Central needs signed files: set SIGNING_KEY and SIGNING_PASSWORD" }
        check(!adroitVersion.endsWith("-SNAPSHOT")) { "Maven Central takes no -SNAPSHOT versions" }
    }
}
