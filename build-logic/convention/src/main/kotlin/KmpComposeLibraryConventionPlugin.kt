import AppExt.findPluginId
import AppExt.libs
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * KMP + Compose Multiplatform library convention: [KmpLibraryConventionPlugin] plus the CMP
 * stack, and the Paparazzi task aliases for golden-holding modules. See kmp-phase-2-probes.md.
 */
class KmpComposeLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("convention.kmpLibrary"))
                apply(libs.findPluginId("composeMultiplatform"))
                apply(libs.findPluginId("composeCompiler"))
            }

            val kmpExtension = extensions.getByType(KotlinMultiplatformExtension::class.java)
            val androidDsl = (kmpExtension as ExtensionAware).extensions
                .getByName("android") as KotlinMultiplatformAndroidLibraryExtension
            androidDsl.androidResources.enable = true

            dependencies {
                add("commonMainImplementation", libs.findLibrary("cmp-runtime").get())
                add("commonMainImplementation", libs.findLibrary("cmp-foundation").get())
                add("commonMainImplementation", libs.findLibrary("cmp-material3").get())
                add("commonMainImplementation", libs.findLibrary("cmp-ui").get())
                add("commonMainImplementation", libs.findLibrary("cmp-uiToolingPreview").get())
                add("commonMainImplementation", libs.findLibrary("cmp-components-resources").get())
            }

            registerPaparazziAliases()
        }
    }

    /**
     * CI and testing.md invoke the Android-library spellings `verifyPaparazziDebug` /
     * `recordPaparazziDebug`; on a KMP module Paparazzi registers only `*AndroidMain` tasks plus
     * the variantless aggregates. GUARD: without both aliases a converted golden module silently
     * vanishes from the repo-wide command (probe P1). Plain lifecycle tasks — never `Test`-typed.
     */
    private fun Project.registerPaparazziAliases() {
        pluginManager.withPlugin("app.cash.paparazzi") {
            // GUARD: on a KMP module Paparazzi's PrepareResourcesTask cannot serialize its
            // asset-dir provider into the configuration cache — storing fails the whole build.
            // Marking it incompatible degrades that invocation to a no-cc run instead.
            tasks.matching { task -> task.name.startsWith("preparePaparazzi") }.configureEach {
                notCompatibleWithConfigurationCache(
                    "Paparazzi 2.0.0-alpha05 PrepareResourcesTask cannot serialize its " +
                        "KMP asset-dir providers",
                )
            }
            tasks.register("verifyPaparazziDebug") {
                group = "verification"
                description =
                    "Alias: verifies this KMP module's Paparazzi goldens under the repo-wide task name."
                dependsOn("verifyPaparazzi")
            }
            tasks.register("recordPaparazziDebug") {
                group = "verification"
                description =
                    "Alias: records this KMP module's Paparazzi goldens under the repo-wide task name."
                dependsOn("recordPaparazzi")
            }
        }
    }
}
