import AppExt.findPluginId
import AppExt.libs
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Kotlin Multiplatform library convention (Phase C KMP foundation).
 *
 * Applies AGP's KMP-native `com.android.kotlin.multiplatform.library` plugin — required
 * since AGP 9.0, which rejects the legacy `com.android.library` + `kotlin-multiplatform`
 * combination. Deliberately bypasses [KotlinAndroid.configureKotlinAndroid], which
 * force-applies Hilt to every Android module; a KMP module wires DI through Metro instead.
 * Detekt + the custom `:lint-rules` still apply via the shared `convention.lint` plugin.
 *
 * Status in C.0: this convention is REGISTERED but APPLIED TO NO MODULE. It is the seam the
 * first Hilt->Metro / Room-2->3 slice (C.1) applies when it converts a leaf module. Target
 * declarations (android + iosX/Arm64), source-set layout, namespace/compileSdk/minSdk, and
 * the Metro/Room3 wiring are intentionally deferred to that slice rather than guessed here.
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
