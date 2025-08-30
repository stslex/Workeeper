import AppExt.api
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class NavigationLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {

        dependencies {
            api("decompose")
            api("decompose.extensions")
            api("essenty.lifecycle")
            api("essenty.stateKeeper")
            api("essenty.backHandler")
        }
    }
}

