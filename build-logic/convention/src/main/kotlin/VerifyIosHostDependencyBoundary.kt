import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

private const val IOS_HOST = ":app:ios"
private val CHECKED_PROJECT_GROUPS = setOf("app", "core", "feature")

internal fun Project.registerIosHostDependencyBoundary(): TaskProvider<VerifyIosHostDependencyBoundary> {
    val projects = subprojects.filter {
        it.path != IOS_HOST && it.path.removePrefix(":").substringBefore(':') in CHECKED_PROJECT_GROUPS
    }
    val boundary = tasks.register("verifyIosHostDependencyBoundary", VerifyIosHostDependencyBoundary::class.java) {
        group = "verification"
    }
    gradle.projectsEvaluated {
        boundary.configure {
            forbiddenConfigurations.set(projects.associate { project ->
                check(project.state.executed) { "Expected evaluated dependency declarations for ${project.path}" }
                project.path to project.configurations.filter { configuration ->
                    configuration.dependencies.withType(ProjectDependency::class.java).any { it.path == IOS_HOST }
                }.map { it.name }.sorted()
            })
        }
    }
    return boundary
}

@DisableCachingByDefault(because = "Checks the evaluated project dependency declarations")
abstract class VerifyIosHostDependencyBoundary : DefaultTask() {
    @get:Input
    abstract val forbiddenConfigurations: MapProperty<String, List<String>>

    @TaskAction
    fun verify() {
        val projects = forbiddenConfigurations.get()
        val edges = projects.toSortedMap().flatMap { (project, configurations) ->
            configurations.map { "$project:$it -> $IOS_HOST" }
        }
        check(edges.isEmpty()) {
            "$IOS_HOST must remain outside shipped dependency graphs:\n" + edges.joinToString("\n")
        }
        logger.lifecycle("Verified $IOS_HOST dependency boundary across ${projects.size} projects")
    }
}
