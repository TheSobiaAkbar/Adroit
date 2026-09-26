import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** The version just published by the main build (companyAdmob.version in its gradle.properties). */
val companyAdmobVersion: String = Properties()
    .apply { rootDir.resolve("../gradle.properties").inputStream().use(::load) }
    .getProperty("companyAdmob.version")

android {
    namespace = "com.company.admob.consumersmoke"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.company.admob.consumersmoke"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Google's official sample AdMob app ID: never a real one in this repository.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // The only CompanyAdMob dependency: admob-core, admob-views and the Google SDKs must arrive transitively.
    implementation("com.company:admob-compose:$companyAdmobVersion")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")
}

/**
 * The published POMs and module metadata put the right things on the host's classpaths: CompanyAdMob and the Google
 * SDKs at runtime, but no Google Mobile Ads or UMP types at compile time (they're `implementation` in admob-core).
 */
val verifyConsumerClasspath by tasks.registering {
    group = "verification"
    val compile = configurations.named("debugCompileClasspath").flatMap { it.incoming.resolutionResult.rootComponent }
    val runtime = configurations.named("debugRuntimeClasspath").flatMap { it.incoming.resolutionResult.rootComponent }
    val compileModules = compile.map { root -> root.modules() }
    val runtimeModules = runtime.map { root -> root.modules() }
    inputs.property("compile", compileModules)
    inputs.property("runtime", runtimeModules)
    doLast {
        val problems = mutableListOf<String>()
        val onRuntime = runtimeModules.get()
        val onCompile = compileModules.get()
        listOf(
            "com.company:admob-core",
            "com.company:admob-views",
            "com.company:admob-compose",
            "com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk",
            "com.google.android.ump:user-messaging-platform",
        ).filterNot { it in onRuntime }.forEach { problems += "missing from the runtime classpath: $it" }
        listOf(
            "com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk",
            "com.google.android.ump:user-messaging-platform",
        ).filter { it in onCompile }.forEach { problems += "on the compile classpath: $it" }
        if (problems.isNotEmpty()) throw GradleException(problems.joinToString("\n"))
        println("Consumer classpaths OK: CompanyAdMob and the Google SDKs at runtime, no Google SDK at compile time")
    }
}

fun org.gradle.api.artifacts.result.ResolvedComponentResult.modules(): Set<String> {
    val seen = mutableSetOf<String>()
    val queue = ArrayDeque(listOf(this))
    val visited = mutableSetOf<org.gradle.api.artifacts.result.ResolvedComponentResult>()
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        if (!visited.add(component)) continue
        (component.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)
            ?.let { seen += "${it.group}:${it.module}" }
        component.dependencies
            .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
            .forEach { queue += it.selected }
    }
    return seen
}
