package com.company.admob.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/**
 * Forbidden dependency edges (docs/architecture/02_ARCHITECTURE.md §8).
 *
 * Coordinates are matched as regular expressions against `group:name` for external modules and against
 * `project :path` for project dependencies, across the whole resolved release runtime classpath.
 */
private val FORBIDDEN_EVERYWHERE = listOf(
    // The legacy Google Mobile Ads SDK must never be resolved (ADR-001). Mediation adapters must exclude it.
    "com\\.google\\.android\\.gms:play-services-ads",
    "com\\.google\\.android\\.gms:play-services-ads-lite",
)

/** Checked against the whole transitive release runtime classpath. */
private val FORBIDDEN_TRANSITIVE_BY_PROJECT = mapOf(
    ":admob-core" to listOf(
        "androidx\\.compose\\..*:.*",
        "com\\.google\\.android\\.material:.*",
        "com\\.google\\.dagger:.*",
        "com\\.google\\.firebase:.*",
        "project :admob-views",
        "project :admob-compose",
        "project :admob-firebase",
    ),
    ":admob-views" to listOf(
        "androidx\\.compose\\..*:.*",
        "project :admob-compose",
    ),
    // Compose stays design-system neutral: no Material or Material3, Navigation, Hilt or Firebase (PROMPT 14 §7).
    ":admob-compose" to listOf(
        "androidx\\.compose\\.material3?:.*",
        "com\\.google\\.android\\.material:.*",
        "androidx\\.navigation:.*",
        "com\\.google\\.dagger:.*",
        "com\\.google\\.firebase:.*",
        "project :admob-firebase",
    ),
)

/**
 * Checked against direct (first-level) dependencies only.
 *
 * The Google Mobile Ads Next-Gen SDK itself pulls `androidx.fragment:fragment` (via play-services-basement) and
 * `androidx.appcompat:appcompat-resources` transitively, which we cannot control; the rule guarantees that
 * CompanyAdMob code never adds these dependencies itself.
 */
private val FORBIDDEN_DIRECT_BY_PROJECT = mapOf(
    ":admob-core" to listOf(
        "androidx\\.appcompat:.*",
        "androidx\\.fragment:.*",
    ),
    ":admob-views" to listOf(
        "androidx\\.appcompat:.*",
        "androidx\\.fragment:.*",
        "com\\.google\\.android\\.material:.*",
    ),
    ":admob-compose" to listOf(
        "androidx\\.appcompat:.*",
        "androidx\\.fragment:.*",
    ),
    // Fakes for host tests: never Google's SDKs directly (they arrive only through admob-core), and JUnit only as
    // compileOnly, so it isn't forced on consumers. Source references are checked by NoGoogleReferencesTest.
    ":admob-testing" to listOf(
        "com\\.google\\.android\\.libraries\\.ads\\.mobile\\.sdk:.*",
        "com\\.google\\.android\\.ump:.*",
        "junit:junit",
    ),
)

internal fun Project.registerDependencyGuard() {
    // Captured here: inside the task configuration block, `path` would be the task's path, not the project's.
    val modulePath = path
    val guard = tasks.register<DependencyGuardTask>("checkModuleDependencies") {
        group = "verification"
        description = "Fails if this module resolves a forbidden dependency (see 02_ARCHITECTURE.md §8)."
        projectPath.set(modulePath)
        forbiddenTransitive.set(FORBIDDEN_EVERYWHERE + FORBIDDEN_TRANSITIVE_BY_PROJECT[modulePath].orEmpty())
        forbiddenDirect.set(FORBIDDEN_DIRECT_BY_PROJECT[modulePath].orEmpty())
        report.set(layout.buildDirectory.file("reports/dependency-guard/releaseRuntimeClasspath.txt"))
    }
    // The release runtime classpath configuration is created by AGP during configuration.
    afterEvaluate {
        val classpath = configurations.findByName("releaseRuntimeClasspath")
            ?: throw GradleException("$path: releaseRuntimeClasspath not found; dependency guard cannot run.")
        val root = classpath.incoming.resolutionResult.rootComponent
        guard.configure {
            resolvedComponents.set(root.map { it.allComponentIds() })
            directComponents.set(root.map { it.directComponentIds() })
        }
    }
    tasks.named("check") { dependsOn(guard) }
}

private fun ResolvedComponentResult.directComponentIds(): List<String> =
    dependencies
        .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
        .mapNotNull { it.selected.id.asCoordinate() }

private fun org.gradle.api.artifacts.component.ComponentIdentifier.asCoordinate(): String? = when (this) {
    is ModuleComponentIdentifier -> "$group:$module:$version"
    is ProjectComponentIdentifier -> "project $projectPath"
    else -> null
}

private fun ResolvedComponentResult.allComponentIds(): List<String> {
    val seen = LinkedHashSet<String>()
    val queue = ArrayDeque(listOf(this))
    val visited = HashSet<ResolvedComponentResult>()
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        if (!visited.add(component)) continue
        if (component !== this) component.id.asCoordinate()?.let { seen += it }
        component.dependencies
            .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
            .forEach { queue += it.selected }
    }
    return seen.toList()
}

/** Match "group:name" (version dropped) for modules and the whole string for projects. */
private fun List<String>.matching(patterns: List<String>): List<String> {
    val regexes = patterns.map { Regex(it) }
    return filter { component ->
        val key = if (component.startsWith("project ")) component else component.substringBeforeLast(':')
        regexes.any { it.matches(key) }
    }
}

abstract class DependencyGuardTask : DefaultTask() {
    @get:Input abstract val projectPath: Property<String>
    @get:Input abstract val forbiddenTransitive: ListProperty<String>
    @get:Input abstract val forbiddenDirect: ListProperty<String>
    @get:Input abstract val resolvedComponents: ListProperty<String>
    @get:Input abstract val directComponents: ListProperty<String>
    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val components = resolvedComponents.get()
        val violations = components.matching(forbiddenTransitive.get()).map { "$it (transitive rule)" } +
            directComponents.get().matching(forbiddenDirect.get()).map { "$it (direct dependency rule)" }
        report.get().asFile.writeText(
            "# direct\n" + directComponents.get().sorted().joinToString("\n") +
                "\n# transitive\n" + components.sorted().joinToString("\n") + "\n"
        )
        if (violations.isNotEmpty()) {
            throw GradleException(
                "${projectPath.get()} resolves forbidden dependencies (docs/architecture/02_ARCHITECTURE.md §8):\n" +
                    violations.joinToString("\n") { "  - $it" }
            )
        }
    }
}
