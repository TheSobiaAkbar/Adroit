package com.company.admob.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Category
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension

/** Per-module publication settings: `companyAdmobPublishing { artifactId = "…"; description = "…" }`. */
abstract class CompanyAdMobPublishingExtension {
    /** Defaults to the project name. */
    abstract val artifactId: Property<String>

    /** The POM description. Required. */
    abstract val description: Property<String>
}

/**
 * `companyadmob.publishing`: Maven publication of a library module with the built-in `maven-publish` plugin
 * (docs/architecture/02_ARCHITECTURE.md §27, docs/architecture/PUBLISHING_NOTES.md).
 *
 * - The AGP `release` variant, with sources and javadoc jars, as publication `release`.
 * - POM metadata from `companyAdmob.pom.*` Gradle properties; the dependency scopes come from AGP (`api` →
 *   compile, `implementation` → runtime).
 * - Repositories: `mavenLocal()` always; GitHub Packages and the company repository only when their URL and
 *   credentials are supplied through Gradle properties or environment variables (never committed).
 * - Signing only when `SIGNING_KEY` and `SIGNING_PASSWORD` are set (in-memory ASCII-armored key).
 * - The company repository only accepts release versions (`checkReleaseVersion`).
 * - `verifyPublication` publishes to a repository in the build directory and checks the files and the POM scopes.
 */
class PublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("maven-publish")
        val settings = extensions.create<CompanyAdMobPublishingExtension>("companyAdmobPublishing")
        settings.artifactId.convention(name)

        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> {
                publishing {
                    singleVariant(VARIANT) {
                        withSourcesJar()
                        withJavadocJar()
                    }
                }
            }
        }

        val projectVersion = version.toString()
        val isSnapshot = projectVersion.endsWith("-SNAPSHOT")
        val verificationRepo = layout.buildDirectory.dir("verification-repo")

        extensions.configure<PublishingExtension> {
            repositories {
                mavenLocal()
                maven {
                    name = VERIFICATION_REPO
                    url = uri(verificationRepo)
                }
                gitHubPackagesUrl()?.let { packagesUrl ->
                    val user = propertyOrEnv("gpr.user", "GITHUB_ACTOR")
                    val token = propertyOrEnv("gpr.key", "GITHUB_TOKEN")
                    if (user != null && token != null) {
                        maven {
                            name = "GitHubPackages"
                            url = uri(packagesUrl)
                            credentials {
                                username = user
                                password = token
                            }
                        }
                    }
                }
                propertyOrEnv("companyAdmob.repo.url", "COMPANY_MAVEN_URL")?.let { companyUrl ->
                    val user = providers.environmentVariable("COMPANY_MAVEN_USER").orNull
                    val password = providers.environmentVariable("COMPANY_MAVEN_PASSWORD").orNull
                    maven {
                        name = COMPANY_REPO
                        url = uri(companyUrl)
                        // Only with both: a repository without authentication (or a file:// one) takes none.
                        if (user != null && password != null) {
                            credentials {
                                username = user
                                this.password = password
                            }
                        }
                    }
                }
            }
        }

        // AGP creates the release component while the project is being evaluated, so the publication that uses it
        // is created afterwards (as in the AGP publishing guide).
        afterEvaluate {
            val publishing = extensions.getByType<PublishingExtension>()
            val artifactId = settings.artifactId.get()
            val description = settings.description.orNull
                ?: throw GradleException("$path: set companyAdmobPublishing.description for the POM")
            publishing.publications.create<MavenPublication>(PUBLICATION) {
                from(components.getByName(VARIANT))
                groupId = project.group.toString()
                this.artifactId = artifactId
                version = projectVersion
                // Resolved versions in the POM, also for dependencies whose version comes from a BOM (Compose),
                // so Maven consumers see them. Gradle consumers read the module metadata.
                versionMapping { allVariants { fromResolutionResult() } }
                configurePom(project, artifactId, description)
            }
            configureSigning(publishing)
        }

        val checkReleaseVersion = tasks.register("checkReleaseVersion") {
            group = "publishing"
            description = "Fails for a -SNAPSHOT version: the company repository only takes releases."
            doLast {
                if (isSnapshot) {
                    throw GradleException(
                        "Version $projectVersion is a snapshot. Snapshots go to mavenLocal or GitHub Packages only; " +
                            "set a release version in gradle.properties (companyAdmob.version) to publish to the " +
                            "company repository.",
                    )
                }
            }
        }
        tasks.withType<PublishToMavenRepository>()
            .matching { it.name.endsWith("To${COMPANY_REPO}Repository") }
            .configureEach { dependsOn(checkReleaseVersion) }

        registerVerifyPublication(settings)
    }

    private fun Project.propertyOrEnv(property: String, env: String): String? =
        providers.gradleProperty(property).orNull?.takeIf(String::isNotBlank)
            ?: providers.environmentVariable(env).orNull?.takeIf(String::isNotBlank)

    /** `https://maven.pkg.github.com/<owner>/<repo>` from `companyAdmob.github.repository` or GITHUB_REPOSITORY. */
    private fun Project.gitHubPackagesUrl(): String? =
        propertyOrEnv("companyAdmob.github.repository", "GITHUB_REPOSITORY")
            ?.let { "https://maven.pkg.github.com/$it" }

    private fun MavenPublication.configurePom(project: Project, artifactId: String, description: String) {
        pom {
            name.set("CompanyAdMob $artifactId")
            this.description.set(description)
            url.set(project.requiredProperty("companyAdmob.pom.url"))
            licenses {
                license {
                    name.set(project.requiredProperty("companyAdmob.pom.license.name"))
                }
            }
            developers {
                developer {
                    id.set(project.requiredProperty("companyAdmob.pom.developer.id"))
                    name.set(project.requiredProperty("companyAdmob.pom.developer.name"))
                }
            }
            scm {
                url.set(project.requiredProperty("companyAdmob.pom.url"))
                connection.set(project.requiredProperty("companyAdmob.pom.scm.connection"))
            }
        }
    }

    /** Signs the publication only when an in-memory key is supplied through the environment. */
    private fun Project.configureSigning(publishing: PublishingExtension) {
        val key = providers.environmentVariable("SIGNING_KEY").orNull?.takeIf(String::isNotBlank) ?: return
        val password = providers.environmentVariable("SIGNING_PASSWORD").orNull ?: return
        pluginManager.apply("signing")
        extensions.configure<SigningExtension> {
            useInMemoryPgpKeys(key, password)
            sign(publishing.publications.getByName(PUBLICATION))
        }
    }

    private fun Project.registerVerifyPublication(settings: CompanyAdMobPublishingExtension) {
        val publishTask = "publish${PUBLICATION.replaceFirstChar(Char::uppercase)}PublicationTo${VERIFICATION_REPO}Repository"
        val groupPath = group.toString()
        tasks.register<VerifyPublicationTask>("verifyPublication") {
            group = "verification"
            description = "Publishes to build/verification-repo and checks the files and the POM's dependency scopes."
            dependsOn(publishTask)
            repository.set(layout.buildDirectory.dir("verification-repo"))
            groupId.set(groupPath)
            artifactId.set(settings.artifactId)
            version.set(project.version.toString())
            // Declared at configuration time: what the POM's scopes must reflect.
            compileScope.set(provider { declaredCoordinates("api") })
            runtimeScope.set(provider { declaredCoordinates("implementation") })
        }
    }

    /**
     * `group:name` of every dependency declared in [configuration] (project dependencies included). Platforms (BOMs)
     * are left out: the POM imports them under `dependencyManagement`, not as dependencies.
     */
    private fun Project.declaredCoordinates(configuration: String): List<String> =
        configurations.getByName(configuration).dependencies.filterNot { it.isPlatform() }.map { dependency ->
            if (dependency is ProjectDependency) {
                val dependencyProject = rootProject.project(dependency.path)
                val artifactId = dependencyProject.extensions.findByType(CompanyAdMobPublishingExtension::class.java)
                    ?.artifactId?.orNull ?: dependencyProject.name
                "${dependencyProject.group}:$artifactId"
            } else {
                "${dependency.group}:${dependency.name}"
            }
        }

    private fun Dependency.isPlatform(): Boolean {
        val category = (this as? ModuleDependency)?.attributes?.getAttribute(Category.CATEGORY_ATTRIBUTE)?.name
        return category == Category.REGULAR_PLATFORM || category == Category.ENFORCED_PLATFORM
    }

    private companion object {
        const val VARIANT = "release"
        const val PUBLICATION = "release"
        const val VERIFICATION_REPO = "VerificationBuildDir"
        const val COMPANY_REPO = "Company"
    }
}
