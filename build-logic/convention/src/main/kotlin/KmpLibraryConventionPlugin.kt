import AppExt.findPluginId
import AppExt.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Kotlin Multiplatform library convention (Phase C KMP foundation).
 *
 * Applies AGP's KMP-native `com.android.kotlin.multiplatform.library` plugin — required
 * since AGP 9.0, which rejects the legacy `com.android.library` + `kotlin-multiplatform`
 * combination. Deliberately bypasses [KotlinAndroid.configureKotlinAndroid]: that helper is
 * typed against AGP's `ApplicationExtension` / `LibraryExtension`, neither of which the KMP
 * android target exposes, and it wires Android-only concerns (Robolectric-JUnit5, the
 * instrumentation runner, core-library desugaring, `buildConfig`, the Android test bundles)
 * that a KMP leaf does not want. Where a leaf also needs android.* implementations or the Metro
 * plugin, those live in a sibling Android-library module (`core:core` → `core:core-android`).
 * Detekt + the custom `:lint-rules` still apply via the shared `convention.lint` plugin.
 *
 * First applied in C.1 (L1: `core:core`). Consuming modules declare their own targets
 * (`android { }` + `iosSimulatorArm64()`), source-set layout, namespace/compileSdk/minSdk,
 * and any Metro/Room3 wiring in their build script.
 *
 * `-Xexpect-actual-classes` is set here so `expect`/`actual` classes, objects, and
 * annotations (the KMP DI-qualifier and Firebase-holder seams) do not emit the Beta
 * warning on every compilation across every future KMP module.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("kotlinMultiplatform"))
                apply(libs.findPluginId("androidKmpLibrary"))
                apply(libs.findPluginId("convention.lint"))
            }

            extensions.configure(KotlinMultiplatformExtension::class.java) {
                tasks.withType(KotlinCompilationTask::class.java).configureEach {
                    compilerOptions.freeCompilerArgs.addAll(
                        // expect/actual classes/objects/annotations (DI qualifiers, Firebase
                        // holders) are Beta — silence the per-compilation warning.
                        "-Xexpect-actual-classes",
                        // Repo-standard opt-ins, mirroring KotlinAndroid.configureKotlinAndroid
                        // so KMP modules match the Android convention's experimental surface.
                        "-opt-in=kotlin.RequiresOptIn",
                        "-opt-in=kotlin.uuid.ExperimentalUuidApi",
                        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                        "-opt-in=kotlin.time.ExperimentalTime",
                    )
                }

                // Pin the JVM bytecode level, mirroring KotlinAndroid.configureKotlin's
                // JvmTarget.JVM_21. Unlike the Android convention this is NOT cosmetic: without
                // it a KMP leaf inherits the *daemon's* JDK as its jvmTarget, so on any JDK newer
                // than 21 every Android consumer fails with "Cannot inline bytecode built with
                // JVM target <N> into bytecode that is being built with JVM target 21" the moment
                // it calls one of this module's inline helpers (runIf, transition, ...).
                // Keep in sync with KotlinAndroid.configureKotlin.
                tasks.withType(KotlinJvmCompile::class.java).configureEach {
                    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
                }
            }
        }
    }
}
