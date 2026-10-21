import java.util.Properties

plugins {
    id("com.android.application")
}

/**
 * The Compose host (adroit-compose) by default; `-Psmoke.compose=false` builds a View-based host on adroit-views, with
 * no Compose at all, for the matrix rows on AGP 8: Compose 1.12 (adroit-compose's BOM) requires AGP 9.1.
 */
val withCompose: Boolean = providers.gradleProperty("smoke.compose").getOrElse("true").toBoolean()

/**
 * Adroit Billing too (the default): adroit-billing-compose or adroit-billing-views, initialized in demo mode, with a
 * paywall call. `-Psmoke.billing=false` for the matrix rows on Kotlin 2.1: billing needs Kotlin 2.2 (Play Billing 9).
 */
val withBilling: Boolean = providers.gradleProperty("smoke.billing").getOrElse("true").toBoolean()

// AGP 9 compiles Kotlin itself; AGP 8 (the matrix rows below 9) needs the Kotlin Android plugin, applied before the
// Compose compiler plugin.
if (com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION.substringBefore(".").toInt() < 9) {
    apply(plugin = "org.jetbrains.kotlin.android")
}
if (withCompose) apply(plugin = "org.jetbrains.kotlin.plugin.compose")

/** The version just published by the main build (adroit.version in its gradle.properties). */
val adroitVersion: String = Properties()
    .apply { rootDir.resolve("../gradle.properties").inputStream().use(::load) }
    .getProperty("adroit.version")

android {
    namespace = "io.github.thesobiaakbar.adroit.consumersmoke"
    compileSdk = providers.gradleProperty("smoke.compileSdk").getOrElse("37").toInt()
    defaultConfig {
        applicationId = "io.github.thesobiaakbar.adroit.consumersmoke"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Google's official sample AdMob app ID: never a real one in this repository.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    }
    buildFeatures {
        compose = withCompose
    }
    // R8 over the published artifacts and their consumer rules, as a host's release build does (unsigned).
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    // kotlin, not java: AGP 9 compiles no Kotlin from java dirs. srcDir (deprecated in AGP 9) because AGP 8.1 has no
    // `directories`.
    sourceSets.getByName("main").kotlin.srcDir(if (withCompose) "src/compose/kotlin" else "src/views/kotlin")
    sourceSets.getByName("main").kotlin.srcDir(if (withBilling) "src/billing/kotlin" else "src/noBilling/kotlin")
    if (withBilling) {
        sourceSets.getByName("main").kotlin.srcDir(if (withCompose) "src/billingCompose/kotlin" else "src/billingViews/kotlin")
    }
    // A Java 11 host: Java 11 bytecode from javac and kotlinc (the lowest the libraries support, ADR-010).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
}

dependencies {
    // The only Adroit dependency: the other modules and the Google SDKs must arrive transitively.
    if (withCompose) {
        implementation("io.github.thesobiaakbar:adroit-compose:$adroitVersion")
        implementation(platform("androidx.compose:compose-bom:2026.09.00"))
        implementation("androidx.compose.foundation:foundation")
        implementation("androidx.activity:activity-compose:1.13.0")
    } else {
        implementation("io.github.thesobiaakbar:adroit-views:$adroitVersion")
        // The host's own choice, not Adroit's: activity 1.13 needs AGP 8.9.1, so the oldest rows pass an older one.
        implementation("androidx.activity:activity:${providers.gradleProperty("smoke.activity").getOrElse("1.13.0")}")
    }
    if (withBilling) {
        // The only billing dependency: adroit-billing and Play Billing must arrive transitively.
        implementation("io.github.thesobiaakbar:${if (withCompose) "adroit-billing-compose" else "adroit-billing-views"}:$adroitVersion")
    }
    // In-app update: adroit-remote-config and Play In-App Updates must arrive transitively.
    implementation("io.github.thesobiaakbar:adroit-update:$adroitVersion")
}

/**
 * The published POMs and module metadata put the right things on the host's classpaths: Adroit and the Google
 * SDKs at runtime, but no Google Mobile Ads or UMP types at compile time (they're `implementation` in adroit-core).
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
            "io.github.thesobiaakbar:adroit-core",
            "io.github.thesobiaakbar:adroit-views",
            "com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk",
            "com.google.android.ump:user-messaging-platform",
            "io.github.thesobiaakbar:adroit-update",
            "io.github.thesobiaakbar:adroit-remote-config",
            "com.google.android.play:app-update",
        ).plus(listOfNotNull("io.github.thesobiaakbar:adroit-compose".takeIf { withCompose }))
            .plus(
                if (!withBilling) emptyList() else listOf(
                    "io.github.thesobiaakbar:adroit-billing",
                    "io.github.thesobiaakbar:${if (withCompose) "adroit-billing-compose" else "adroit-billing-views"}",
                    "com.android.billingclient:billing",
                ),
            )
            .filterNot { it in onRuntime }.forEach { problems += "missing from the runtime classpath: $it" }
        listOf(
            "com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk",
            "com.google.android.ump:user-messaging-platform",
            // Play Billing is `implementation` in adroit-billing: no Play Billing types in the host's code either.
            "com.android.billingclient:billing",
            // Play In-App Updates is `implementation` in adroit-update.
            "com.google.android.play:app-update",
        ).filter { it in onCompile }.forEach { problems += "on the compile classpath: $it" }
        if (problems.isNotEmpty()) throw GradleException(problems.joinToString("\n"))
        println("Consumer classpaths OK: Adroit and the Google SDKs at runtime, no Google SDK at compile time (billing: $withBilling)")
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
