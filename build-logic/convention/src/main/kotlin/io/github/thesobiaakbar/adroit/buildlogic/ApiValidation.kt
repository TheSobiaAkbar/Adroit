package io.github.thesobiaakbar.adroit.buildlogic

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import kotlinx.validation.api.dump
import kotlinx.validation.api.filterOutNonPublic
import kotlinx.validation.api.loadApiFromJvmClasses
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Public API dumps for library modules (ADR-007).
 *
 * - `./gradlew apiDump` writes `<module>/api/<module>.api`
 * - `./gradlew apiCheck` (part of `check`) fails when the compiled public API differs from that file
 *
 * Why not the binary-compatibility-validator plugin or KGP `abiValidation`: both hook the
 * `org.jetbrains.kotlin.android` plugin's extension points, which AGP 9's built-in Kotlin does not register
 * (KT-83410), so they produce no tasks or empty dumps. These tasks instead feed the release variant's classes
 * (AGP `ScopedArtifacts` API) into BCV's own JVM dump engine, the same pipeline its plugin runs, so the `.api`
 * format is unchanged. Remove this file once KT-83410 is fixed and switch to KGP `abiValidation`.
 */
internal fun Project.registerApiValidation() {
    val modulePath = path
    val workerClasspath = configurations.create("adroitApiDumpClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies.add(workerClasspath.name, libs.findLibrary("binaryCompatibilityValidator").get())
    dependencies.add(workerClasspath.name, libs.findLibrary("asm-tree").get())
    dependencies.add(workerClasspath.name, libs.findLibrary("kotlin-metadata-jvm").get())

    val apiFileName = "${project.name}.api"
    val apiBuild = tasks.register<ApiBuildTask>("apiBuild") {
        group = "verification"
        description = "Dumps the public API of the release variant to build/api."
        this.workerClasspath.from(workerClasspath)
        outputApiFile.set(layout.buildDirectory.file("api/$apiFileName"))
    }
    extensions.configure<LibraryAndroidComponentsExtension> {
        onVariants(selector().withBuildType("release")) { variant ->
            variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                .use(apiBuild)
                .toGet(ScopedArtifact.CLASSES, ApiBuildTask::classJars, ApiBuildTask::classDirs)
        }
    }

    val committedApiFile = layout.projectDirectory.file("api/$apiFileName")
    tasks.register<ApiDumpTask>("apiDump") {
        group = "verification"
        description = "Writes the current public API to api/$apiFileName. Review the diff before committing."
        builtApiFile.set(apiBuild.flatMap { it.outputApiFile })
        committedApi.set(committedApiFile)
    }
    val apiCheck = tasks.register<ApiCheckTask>("apiCheck") {
        group = "verification"
        description = "Fails if the public API differs from api/$apiFileName."
        builtApiFile.set(apiBuild.flatMap { it.outputApiFile })
        val committed = committedApiFile.asFile
        if (committed.exists()) committedApi.set(committedApiFile)
        projectPathForMessages.set(modulePath)
        report.set(layout.buildDirectory.file("reports/api/apiCheck.txt"))
    }
    tasks.named("check") { dependsOn(apiCheck) }
}

@CacheableTask
abstract class ApiBuildTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classDirs: ListProperty<Directory>

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classJars: ListProperty<RegularFile>

    @get:Classpath
    abstract val workerClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputApiFile: RegularFileProperty

    @get:Inject
    abstract val workers: WorkerExecutor

    @TaskAction
    fun build() {
        workers.classLoaderIsolation { classpath.from(workerClasspath) }
            .submit(ApiBuildWorkAction::class.java) {
                classFiles.from(classDirs.get().map { it.asFileTree.matching { include("**/*.class") } })
                jars.from(classJars.get())
                outputFile.set(outputApiFile)
            }
    }
}

interface ApiBuildParameters : WorkParameters {
    val classFiles: ConfigurableFileCollection
    val jars: ConfigurableFileCollection
    val outputFile: RegularFileProperty
}

abstract class ApiBuildWorkAction : WorkAction<ApiBuildParameters> {
    override fun execute() {
        val classBytes = buildList {
            parameters.classFiles.files.filterNot { isGenerated(it.name) }.sortedBy { it.invariantSeparatorsPath }
                .forEach { add(it.readBytes()) }
            parameters.jars.files.sortedBy { it.name }.forEach { jar ->
                ZipFile(jar).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
                        .filterNot { isGenerated(it.name.substringAfterLast('/')) }
                        .sortedBy { it.name }
                        .forEach { entry -> add(zip.getInputStream(entry).use { it.readBytes() }) }
                }
            }
        }
        val signatures = classBytes.asSequence().map { ByteArrayInputStream(it) }
            .loadApiFromJvmClasses()
            .filterOutNonPublic()
        val output = parameters.outputFile.get().asFile
        output.parentFile.mkdirs()
        output.bufferedWriter().use { signatures.dump(it) }
    }

    /** AGP-generated classes are not part of the designed API. */
    private fun isGenerated(classFileName: String): Boolean =
        classFileName == "BuildConfig.class" || classFileName == "R.class" || classFileName.startsWith("R$")
}

abstract class ApiDumpTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val builtApiFile: RegularFileProperty

    @get:OutputFile
    abstract val committedApi: RegularFileProperty

    @TaskAction
    fun dump() {
        builtApiFile.get().asFile.copyTo(committedApi.get().asFile, overwrite = true)
    }
}

@CacheableTask
abstract class ApiCheckTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val builtApiFile: RegularFileProperty

    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE)
    abstract val committedApi: RegularFileProperty

    @get:org.gradle.api.tasks.Input
    abstract val projectPathForMessages: org.gradle.api.provider.Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val module = projectPathForMessages.get()
        val committedFile: File = committedApi.orNull?.asFile
            ?: throw GradleException("$module: no committed API dump. Run `./gradlew $module:apiDump` and commit it.")
        val expected = committedFile.readLines().map { it.trimEnd() }
        val actual = builtApiFile.get().asFile.readLines().map { it.trimEnd() }
        if (expected == actual) {
            report.get().asFile.writeText("OK\n")
            return
        }
        val removed = expected - actual.toSet()
        val added = actual - expected.toSet()
        val message = buildString {
            appendLine("$module: public API differs from ${committedFile.name}.")
            removed.take(MAX_LINES).forEach { appendLine("  - $it") }
            added.take(MAX_LINES).forEach { appendLine("  + $it") }
            appendLine("If the change is intended, run `./gradlew $module:apiDump`, review the diff, and commit it.")
        }
        report.get().asFile.writeText(message)
        throw GradleException(message)
    }

    private companion object {
        const val MAX_LINES = 40
    }
}
