import io.github.stslex.workeeper.configureApplication
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.configureApplication(AppType.STORE)
    }
}
