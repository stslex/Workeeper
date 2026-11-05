import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Writes paths for this checkout's CI invocations")
abstract class ExportKmpProjectVerificationModel : DefaultTask() {

    @get:Input
    abstract val modulePath: Property<String>

    @get:Input
    abstract val simulatorTask: Property<String>

    @get:Input
    abstract val simulatorResults: Property<String>

    @get:Input
    abstract val simulatorBinary: Property<String>

    @get:Input
    abstract val deviceLinkTask: Property<String>

    @get:Input
    abstract val deviceBinary: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun export() {
        val model = linkedMapOf(
            "module" to modulePath.get(),
            "simulatorTask" to simulatorTask.get(),
            "simulatorResults" to simulatorResults.get(),
            "simulatorBinary" to simulatorBinary.get(),
            "deviceLinkTask" to deviceLinkTask.get(),
            "deviceBinary" to deviceBinary.get(),
        )
        output.get().asFile.writeText(JsonOutput.toJson(model) + "\n")
    }
}

@DisableCachingByDefault(because = "Writes paths for this checkout's CI invocations")
abstract class ExportKmpVerificationModel : DefaultTask() {

    @get:Input
    abstract val requiredModules: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleModels: ConfigurableFileCollection

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun export() {
        val required = requiredModules.get()
        check(required.isNotEmpty() && required.size == required.toSet().size) {
            "Expected a nonempty, unique Native module roster"
        }
        val models = moduleModels.files.map { file ->
            val model = JsonSlurper().parse(file) as Map<*, *>
            check(model["module"] is String) { "Missing module identifier in ${file.name}" }
            model
        }
        val byModule = models.groupBy { it["module"] }
        check(byModule.keys == required.toSet() && byModule.values.all { it.size == 1 }) {
            "Required Native modules $required do not match evaluated modules ${models.map { it["module"] }}"
        }
        val ordered = required.map { byModule.getValue(it).single() }
        output.get().asFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(ordered)) + "\n")
        logger.lifecycle("Exported ${ordered.size} evaluated Native test and device-link task pairs")
    }
}
