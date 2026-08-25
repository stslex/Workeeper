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
 * stack. Paparazzi and the golden gate land with Phase 7. See kmp-phase-2-probes.md.
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
        }
    }
}
