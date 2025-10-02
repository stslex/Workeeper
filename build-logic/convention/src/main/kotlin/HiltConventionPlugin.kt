import AppExt.findPluginId
import AppExt.implementation
import AppExt.ksp
import AppExt.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("hilt"))
                apply(libs.findPluginId("ksp"))
            }

            dependencies {
                implementation("hilt-android")
                ksp("hilt-compiler")
            }
        }
    }
}
