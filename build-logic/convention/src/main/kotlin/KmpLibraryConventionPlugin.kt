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
 * Kotlin Multiplatform library convention: targets, CI task aliases, JUnit 5, detekt sources
 * and Android Lint. See documentation/feature-specs/kmp-phase-2-probes.md.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("kotlinMultiplatform"))
                apply(libs.findPluginId("androidKmpLibrary"))
                // GUARD: AGP creates lint tasks on a KMP module only when the standalone
                // com.android.lint is co-applied; without it the module has no lint task.
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
        // The AGP KMP plugin registers its DSL under the name "android" with no public
        // extension type, so it is reached by name and cast rather than by configure<T>().
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
            // Host (JVM) unit-test source set: src/androidHostTest. GUARD: withHostTest is
            // single-call, so every host-test option a module needs must be set here.
            withHostTest {
                isIncludeAndroidResources = true
            }
            // Device (instrumented) test source set: src/androidDeviceTest/kotlin. Also
            // single-call, and created unconditionally so a first test cannot land uncompiled.
            withDeviceTest {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            // Parity with the Android convention; consumers' AAR metadata demands desugaring.
            enableCoreLibraryDesugaring = true
            configureLintOptions(lint)
        }

        kmpExtension.iosSimulatorArm64()
    }

    private fun Project.configureCompilerOptions() {
        tasks.withType<KotlinCompilationTask<*>>().configureEach {
            compilerOptions.freeCompilerArgs.addAll(
                "-Xexpect-actual-classes",
                // Repo-standard opt-ins, mirroring KotlinAndroid.configureKotlinAndroid.
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
        dependencies {
            // The desugared-runtime half of enableCoreLibraryDesugaring (configureTargets).
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
        // Without this alias the iOS target is absent from every CI task graph, so a broken
        // iosMain actual merges green; `assemble` builds klibs without linking binaries.
        tasks.register("assembleDebug") {
            group = "build"
            description =
                "Alias: builds every target of this KMP module (incl. iOS klibs) under the repo-wide task name."
            dependsOn("assemble")
        }
        // CI runs `./gradlew lintDebug`; the KMP module's lint reporting task is `lint`.
        tasks.register("lintDebug") {
            group = "verification"
            description =
                "Alias: runs this KMP module's lint reporting under the repo-wide task name."
            dependsOn("lint")
        }
        // The instrumented pair: CI invokes the Android-library spellings, while the AGP-KMP
        // tasks are assembleAndroidDeviceTest / connectedAndroidDeviceTest.
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
