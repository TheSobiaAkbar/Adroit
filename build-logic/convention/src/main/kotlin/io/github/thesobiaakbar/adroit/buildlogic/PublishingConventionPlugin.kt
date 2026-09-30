package io.github.thesobiaakbar.adroit.buildlogic

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
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.plugins.signing.SigningExtension

/** Per-module publication settings: `adroitPublishing { artifactId = "…"; description = "…" }`. */
abstract class AdroitPublishingExtension {
    /** Defaults to the project name. */
    abstract val artifactId: Property<String>

    /** The POM description. Required. */
    abstract val description: Property<String>
}

/**
 * `adroit.publishing`: Maven publication of a library module with the built-in `maven-publish` plugin
 * (docs/architecture/02_ARCHITECTURE.md §27, docs/architecture/PUBLISHING_NOTES.md).
 *
 * - The AGP `release` variant, with sources and javadoc jars, as publication `release`.
 * - POM metadata from `adroit.pom.*` Gradle properties; the dependency scopes come from AGP (`api` →
 *   compile, `implementation` → runtime).
 * - Repositories: `mavenLocal()` always; GitHub Packages only when its credentials are supplied through Gradle
 *   properties or environment variables (never committed).
 * - Signing only when `SIGNING_KEY` and `SIGNING_PASSWORD` are set (in-memory ASCII-armored key).
 * - `verifyPublication` publishes to a repository in the build directory and checks the files and the POM scopes.
 */
class PublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("maven-publish")
        val settings = extensions.create<AdroitPublishingExtension>("adroitPublishing")
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
            }
        }

        // AGP creates the release component while the project is being evaluated, so the publication that uses it
        // is created afterwards (as in the AGP publishing guide).
        afterEvaluate {
            val publishing = extensions.getByType<PublishingExtension>()
            val artifactId = settings.artifactId.get()
            val description = settings.description.orNull
                ?: throw GradleException("$path: set adroitPublishing.description for the POM")
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

        registerVerifyPublication(settings)
    }

    private fun Project.propertyOrEnv(property: String, env: String): String? =
        providers.gradleProperty(property).orNull?.takeIf(String::isNotBlank)
            ?: providers.environmentVariable(env).orNull?.takeIf(String::isNotBlank)

    /** `https://maven.pkg.github.com/<owner>/<repo>` from `adroit.github.repository` or GITHUB_REPOSITORY. */
    private fun Project.gitHubPackagesUrl(): String? =
        propertyOrEnv("adroit.github.repository", "GITHUB_REPOSITORY")
            ?.let { "https://maven.pkg.github.com/$it" }

    private fun MavenPublication.configurePom(project: Project, artifactId: String, description: String) {
        pom {
            name.set("Adroit $artifactId")
            this.description.set(description)
            url.set(project.requiredProperty("adroit.pom.url"))
            licenses {
                license {
                    name.set(project.requiredProperty("adroit.pom.license.name"))
                }
            }
            developers {
                developer {
                    id.set(project.requiredProperty("adroit.pom.developer.id"))
                    name.set(project.requiredProperty("adroit.pom.developer.name"))
                }
            }
            scm {
                url.set(project.requiredProperty("adroit.pom.url"))
                connection.set(project.requiredProperty("adroit.pom.scm.connection"))
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

    private fun Project.registerVerifyPublication(settings: AdroitPublishingExtension) {
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
                val artifactId = dependencyProject.extensions.findByType(AdroitPublishingExtension::class.java)
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
    }
}
