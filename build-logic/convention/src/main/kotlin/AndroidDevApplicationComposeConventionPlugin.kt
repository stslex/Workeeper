import io.github.stslex.workeeper.configureApplication
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidDevApplicationComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            target.configureApplication(AppType.DEV)
        }
    }
}
