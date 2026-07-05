import AppExt.findPluginId
import AppExt.libs
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Minimal Kotlin Multiplatform library scaffolding for the Metro DI spike.
 *
 * Phase B.0: androidTarget only — no iOS target yet. Uses AGP's KMP-native
 * `com.android.kotlin.multiplatform.library` plugin (required since AGP 9.0, which
 * rejects `com.android.library` + `org.jetbrains.kotlin.multiplatform` together).
 *
 * Deliberately does NOT go through [io.github.stslex.workeeper.configureKotlinAndroid],
 * which force-applies Hilt / KSP / Robolectric to every Android module; a KMP + Metro
 * module must stay clear of Hilt. Detekt (with the custom :lint-rules MVI checks) still
 * applies via `convention.lint`. The android target's namespace / compileSdk / minSdk
 * are set in the module script via the `kotlin { androidLibrary { } }` DSL, whose typed
 * accessor only exists in a `.gradle.kts` script, not in a precompiled plugin.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("kotlinMultiplatform"))
                apply(libs.findPluginId("androidKmpLibrary"))
                apply(libs.findPluginId("convention.lint"))
            }
        }
    }
}
