import org.gradle.api.DefaultTask
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Validates the configured target names without producing an artifact")
abstract class VerifyKmpNativeTargets : DefaultTask() {

    @get:Input
    abstract val requiredTargets: SetProperty<String>

    @get:Input
    abstract val configuredTargets: SetProperty<String>

    @TaskAction
    fun verify() {
        val required = requiredTargets.get()
        val configured = configuredTargets.get()
        check(required.isNotEmpty() && required == configured) {
            "$path: required Native targets ${required.sorted()}, configured ${configured.sorted()}"
        }
        logger.lifecycle("$path: verified ${configured.size} Native targets")
    }
}
