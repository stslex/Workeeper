import AppExt.findPluginId
import AppExt.libs
import io.github.stslex.workeeper.configureWearApplication
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Base application convention; the Wear module declares its Firebase and platform dependencies. */
class WearApplicationConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.findPluginId("application"))
                apply(libs.findPluginId("composeCompiler"))
                apply(libs.findPluginId("serialization"))
                apply(libs.findPluginId("convention.lint"))
            }
            configureWearApplication()
        }
    }
}
