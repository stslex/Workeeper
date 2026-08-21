import AppExt.APP_PREFIX
import AppExt.findPluginId
import AppExt.findVersionInt
import AppExt.libs
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import io.github.stslex.workeeper.configureLintOptions
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Kotlin Multiplatform library convention.
 *
 * Applies AGP's KMP-native `com.android.kotlin.multiplatform.library` plugin — required
 * since AGP 9.0, which rejects the legacy `com.android.library` + `kotlin-multiplatform`
 * combination. Deliberately bypasses [KotlinAndroid.configureKotlinAndroid]: that helper is
 * typed against AGP's `ApplicationExtension` / `LibraryExtension`, neither of which the KMP
 * android target exposes. android.* implementations live in the module's own `androidMain`,
 * and the Metro plugin applies to a KMP module directly (`core:data:dataStore` is the
 * precedent for both).
 *
 * A consuming module's build script is plugin application plus dependencies — nothing else.
 * The convention owns:
 *
 * - **Targets.** `android` (namespace derived from the module path by the same rule as
 *   [KotlinAndroid.configureKotlinAndroid], compileSdk/minSdk from the catalog, a host-test
 *   source set via `withHostTest {}` and a device-test source set via `withDeviceTest {}` —
 *   the AGP KMP android target creates neither implicitly) and `iosSimulatorArm64`. A module
 *   needing more targets declares them in its own `kotlin {}` block; the DSL is additive.
 *
 * - **The `testDebugUnitTest` alias — the canonical false green in this repo.** CI runs
 *   exactly ONE unit-test command, `./gradlew testDebugUnitTest`
 *   (.github/workflows/android_build_unified.yml), and Gradle silently skips projects that
 *   have no task under that name. The AGP KMP android target has no build types: it names
 *   the host-test task after the unit-test component identity (`testAndroidHostTest`), so
 *   without the alias every KMP module SILENTLY VANISHES from CI — the build stays green
 *   because nothing ran. The alias depends on the LIVE `tasks.withType<Test>()` collection
 *   instead of a hardcoded task name: the collection is resolved when the task graph is
 *   built (after AGP has registered its tasks), so it keeps working if AGP ever renames or
 *   re-shapes the host-test task.
 *
 * - **JUnit 5 wiring.** The KMP host-test task does not enable the JUnit Platform on its
 *   own, so JUnit 5 tests are otherwise not discovered — another silent vanish, caught the
 *   same way. The convention enables the platform, turns on extension autodetection (the
 *   Robolectric extension registers via ServiceLoader when a module adds it), and puts the
 *   JUnit BOM + Jupiter + launcher + kotlinx-coroutines-test on the host-test classpath,
 *   mirroring what [KotlinAndroid.configureKotlinAndroid] wires for Android modules.
 *   Anything beyond that baseline (mockk, robolectric, turbine…) is a module concern,
 *   declared with the raw configuration name: `"androidHostTestImplementation"(libs.mockk)`.
 *   The tasks keep Gradle's `failOnNoDiscoveredTests` default (true): a regression in this
 *   wiring fails loudly instead of passing over zero discovered tests — a deliberate
 *   divergence from [KotlinAndroid.configureKotlinAndroid], which sets it false. A module
 *   with no `androidHostTest` sources needs no opt-out: its task is `NO-SOURCE`. Only a
 *   module that has host-test sources but intentionally discovers no tests should override
 *   `failOnNoDiscoveredTests` per-module.
 *
 * - **detekt sources.** detekt's default source resolution is `src/main/…` + `src/test/…`,
 *   which in a KMP layout matches NOTHING — the task goes green over zero inputs. The
 *   convention derives detekt's sources from the live Kotlin source-set model (minus
 *   anything under the build directory), so a new source set can never silently escape
 *   the gate.
 *
 * - **Android Lint.** The KMP android DSL is not a `CommonExtension`, so
 *   [LintConventionPlugin]'s lookup cannot see it; the shared option block is applied here
 *   via [configureLintOptions]. The KMP plugin itself registers NO lint reporting task —
 *   AGP creates one only when the standalone `com.android.lint` plugin is co-applied, so
 *   the convention co-applies it and aliases `lintDebug` onto the resulting `lint` task —
 *   without both, androidMain lint analysis is absent from the repo-wide gate
 *   (documentation/feature-specs/kmp-phase-2-probes.md, "Findings" §1).
 *
 * - **Compiler flags.** `-Xexpect-actual-classes` (expect/actual classes/objects/annotations
 *   — the KMP DI-qualifier and Firebase-holder seams — are Beta; silence the warning
 *   per-repo, not per-module), the repo-standard opt-ins, and the JVM_21 bytecode pin.
 *   The pin is NOT cosmetic: without it a KMP module inherits the daemon's JDK as its
 *   jvmTarget, and on any JDK newer than 21 every Android consumer fails with "Cannot
 *   inline bytecode built with JVM target <N>" the moment it calls an inline helper from
 *   this module. Keep in sync with [KotlinAndroid.configureKotlin].
 */
class KmpLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("kotlinMultiplatform"))
                apply(libs.findPluginId("androidKmpLibrary"))
                // The STANDALONE lint plugin: AGP's KmpTaskManager creates lint reporting
                // tasks on a KMP module only when com.android.lint is co-applied. Without it
                // the module has NO lint task and silently vanishes from the repo-wide
                // lintDebug gate.
                apply(libs.findPluginId("androidLint"))
                apply(libs.findPluginId("convention.lint"))
            }

            val kmpExtension = extensions.getByType(KotlinMultiplatformExtension::class.java)

            configureTargets(kmpExtension)
            configureCompilerOptions()
            configureHostTests()
            registerCiAliases()
            configureDetektSources(kmpExtension)
        }
    }

    private fun Project.configureTargets(kmpExtension: KotlinMultiplatformExtension) {
        // The AGP KMP plugin registers its DSL on the kotlin extension under the name
        // "android" (KotlinMultiplatformAndroidPlugin.ANDROID_EXTENSION_ON_KOTLIN_EXTENSION_NAME;
        // "androidLibrary" is the deprecated alias), without a public extension type — so it
        // is reached by name and cast, not by configure<T>().
        val androidDsl = (kmpExtension as ExtensionAware).extensions
            .getByName("android") as KotlinMultiplatformAndroidLibraryExtension

        // Same namespace rule as KotlinAndroid.configureKotlinAndroid's library branch.
        val moduleName = path.split(":")
            .drop(1)
            .joinToString(".")
            .replace("-", "_")

        androidDsl.apply {
            namespace = if (moduleName.isNotEmpty()) "$APP_PREFIX.$moduleName" else APP_PREFIX
            compileSdk = libs.findVersionInt("compileSdk")
            minSdk = libs.findVersionInt("minSdk")
            // Host (JVM) unit-test source set: src/androidHostTest. withHostTest is
            // SINGLE-CALL — a module that calls it again gets "Android host tests have
            // already been enabled" — so every host-test option a module could ever need
            // must be set here, in the one call the convention owns.
            // isIncludeAndroidResources mirrors the Android convention's repo-wide
            // `unitTests.isIncludeAndroidResources = true` and is what lets Paparazzi
            // resolve the module R class on the host test
            // (documentation/feature-specs/kmp-phase-2-probes.md, P1).
            withHostTest {
                isIncludeAndroidResources = true
            }
            // Device (instrumented) test source set: src/androidDeviceTest/kotlin. Like
            // withHostTest, SINGLE-CALL and convention-owned. Created unconditionally —
            // mirroring classic AGP, where every library has an androidTest component whether
            // or not sources exist — because a component created only for modules that already
            // have device tests cannot catch the module whose FIRST instrumented test lands
            // outside the compiled set: sources without a component neither compile nor run
            // nor fail, the exact vanish ConfigureInstrumentedSuiteGate polices.
            withDeviceTest {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            // Repo-wide parity with KotlinAndroid.configureKotlinAndroid's
            // isCoreLibraryDesugaringEnabled = true. Not optional in practice: every Android
            // convention module publishes AAR metadata demanding desugaring of its consumers,
            // so the first KMP device-test APK that depends on one (core:data:database →
            // core:ui:test-utils) fails checkAndroidDeviceTestAarMetadata without this.
            enableCoreLibraryDesugaring = true
            configureLintOptions(lint)
        }

        kmpExtension.iosSimulatorArm64()
    }

    private fun Project.configureCompilerOptions() {
        tasks.withType<KotlinCompilationTask<*>>().configureEach {
            compilerOptions.freeCompilerArgs.addAll(
                "-Xexpect-actual-classes",
                // Repo-standard opt-ins, mirroring KotlinAndroid.configureKotlinAndroid
                // so KMP modules match the Android convention's experimental surface.
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlin.uuid.ExperimentalUuidApi",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlin.time.ExperimentalTime",
            )
        }
        tasks.withType<KotlinJvmCompile>().configureEach {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    private fun Project.configureHostTests() {
        // Raw configuration names, the pattern the KMP source-set DSL forces for platform()
        // anyway; the configurations exist because configureTargets ran withHostTest first.
        dependencies {
            // The desugared-runtime half of enableCoreLibraryDesugaring (configureTargets);
            // same artifact the Android convention wires.
            add("coreLibraryDesugaring", libs.findLibrary("android-desugarJdkLibs").get())

            add("androidHostTestImplementation", platform(libs.findLibrary("junit-bom").get()))
            add("androidHostTestImplementation", libs.findLibrary("junit-jupiter").get())
            add("androidHostTestImplementation", libs.findLibrary("coroutine-test").get())
            add("androidHostTestRuntimeOnly", libs.findLibrary("junit-launcher").get())
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            systemProperty("junit.jupiter.extensions.autodetection.enabled", true)
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }

    private fun Project.registerCiAliases() {
        tasks.register("testDebugUnitTest") {
            group = "verification"
            description =
                "Alias: runs this KMP module's host (JVM) tests under the repo-wide task name."
            dependsOn(tasks.withType<Test>())
        }
        // Without this alias the iOS target is absent from every CI task graph — consumers
        // pull only the androidMain compilation — so a broken iosMain actual merges green.
        // With it, `assemble` puts every target's klib into the graph WITHOUT linking
        // binaries, so it stays green on Linux runners — Kotlin/Native cross-compiles
        // Apple klibs on any host; only linking needs macOS. The build cache cannot mask
        // a break: changed inputs miss the key and must compile.
        tasks.register("assembleDebug") {
            group = "build"
            description =
                "Alias: builds every target of this KMP module (incl. iOS klibs) under the repo-wide task name."
            dependsOn("assemble")
        }
        // Same silent-vanish shape as the two aliases above: CI runs `./gradlew lintDebug`,
        // and the KMP module's lint reporting task (from the standalone lint plugin) is
        // named `lint`. Without the alias, androidMain lint analysis never gates a PR.
        tasks.register("lintDebug") {
            group = "verification"
            description =
                "Alias: runs this KMP module's lint reporting under the repo-wide task name."
            dependsOn("lint")
        }
        // The instrumented pair. CI assembles test APKs with `assembleDebugAndroidTest`
        // (android_build_unified.yml, every PR) and ui_tests.yml runs them with
        // `connectedDebugAndroidTest` — both Android-library spellings, invoked repo-wide.
        // The AGP-KMP tasks are `assembleAndroidDeviceTest` / `connectedAndroidDeviceTest`
        // (kmp-phase-2-probes.md, P4c; kmp-phase-6-data-layer.md → §9
        // "The connected-run alias is the FIFTH silent vanish."). Literal task names on
        // purpose: if AGP renames its device-test tasks, graph construction must fail loudly,
        // not fall back to depending on nothing.
        tasks.register("assembleDebugAndroidTest") {
            group = "build"
            description =
                "Alias: assembles this KMP module's device-test APK under the repo-wide task name."
            dependsOn("assembleAndroidDeviceTest")
        }
        tasks.register("connectedDebugAndroidTest") {
            group = "verification"
            description =
                "Alias: runs this KMP module's device tests under the repo-wide task name."
            dependsOn("connectedAndroidDeviceTest")
        }
    }

    private fun Project.configureDetektSources(kmpExtension: KotlinMultiplatformExtension) {
        extensions.configure(DetektExtension::class.java) {
            val buildDirectory = layout.buildDirectory
            source.setFrom(
                provider {
                    kmpExtension.sourceSets
                        .flatMap { sourceSet -> sourceSet.kotlin.srcDirs }
                        .filterNot { dir -> dir.startsWith(buildDirectory.get().asFile) }
                },
            )
        }
    }
}
