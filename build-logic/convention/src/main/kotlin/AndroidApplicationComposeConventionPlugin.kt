import AppExt.findPluginId
import AppExt.libs
import com.android.build.api.dsl.ApplicationExtension
import io.github.stslex.workeaper.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {

            }
            pluginManager.apply("com.android.application")
            pluginManager.apply(libs.findPluginId("kotlin"))
            pluginManager.apply(libs.findPluginId("composeCompiler"))
            pluginManager.apply(libs.findPluginId("vkompose"))

            configureAndroidCompose(
                commonExtension = extensions.getByType<ApplicationExtension>()
            )
        }
    }
}