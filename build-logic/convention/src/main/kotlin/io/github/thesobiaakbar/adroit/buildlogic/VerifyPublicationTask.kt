package io.github.thesobiaakbar.adroit.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks what `publishReleasePublicationToVerificationBuildDirRepository` wrote: the AAR, POM, Gradle module
 * metadata, sources jar and a javadoc jar with HTML in it, and that the POM's dependency scopes match the module's
 * declared `api` (compile) and `implementation` (runtime) dependencies.
 */
abstract class VerifyPublicationTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val repository: DirectoryProperty

    @get:Input abstract val groupId: Property<String>
    @get:Input abstract val artifactId: Property<String>
    @get:Input abstract val version: Property<String>

    /** `group:name` expected with scope `compile` (from `api`). */
    @get:Input abstract val compileScope: ListProperty<String>

    /** `group:name` expected with scope `runtime` (from `implementation`). */
    @get:Input abstract val runtimeScope: ListProperty<String>

    @TaskAction
    fun verify() {
        val artifactId = artifactId.get()
        val dir = repository.get().asFile
            .resolve(groupId.get().replace('.', '/'))
            .resolve(artifactId)
            .resolve(version.get())
        if (!dir.isDirectory) throw GradleException("Nothing published to $dir")

        val problems = mutableListOf<String>()
        // A snapshot is published with a timestamp in place of "SNAPSHOT"; take the newest file of each kind.
        fun published(suffix: String): File? =
            dir.listFiles().orEmpty().filter { it.name.startsWith("$artifactId-") && it.name.endsWith(suffix) }
                .maxByOrNull { it.lastModified() }
                .also { if (it == null || it.length() == 0L) problems += "missing or empty: *$suffix" }

        published(".aar")
        published(".module")
        published("-sources.jar")?.let { jar ->
            if (jar.entryNames().none { it.endsWith(".kt") }) problems += "the sources jar has no Kotlin sources"
        }
        published("-javadoc.jar")?.let { jar ->
            if (jar.entryNames().none { it.endsWith(".html") }) problems += "the javadoc jar has no HTML pages"
        }
        published(".pom")?.let { pom -> problems += checkScopes(pom) }

        if (problems.isNotEmpty()) {
            throw GradleException("$artifactId publication is incomplete:\n" + problems.joinToString("\n") { "  - $it" })
        }
        logger.lifecycle("$artifactId: aar, pom, module, sources and javadoc published; POM scopes match")
    }

    private fun File.entryNames(): List<String> = ZipFile(this).use { zip -> zip.entries().toList().map { it.name } }

    private fun checkScopes(pom: File): List<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom)
        val nodes = document.getElementsByTagName("dependency")
        val scopes = (0 until nodes.length).map { nodes.item(it) as Element }.associate { dependency ->
            fun child(tag: String) = dependency.getElementsByTagName(tag).item(0)?.textContent?.trim()
            "${child("groupId")}:${child("artifactId")}" to (child("scope") ?: "compile")
        }
        val expected = compileScope.get().associateWith { "compile" } + runtimeScope.get().associateWith { "runtime" }
        return expected.mapNotNull { (coordinate, scope) ->
            when (val actual = scopes[coordinate]) {
                null -> "POM is missing $coordinate (expected scope $scope)"
                scope -> null
                else -> "POM scope of $coordinate is $actual, expected $scope"
            }
        }
    }
}
