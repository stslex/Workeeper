import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Category
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

internal const val KMP_DEPENDENCY_EDGES = "gradle/kmp-migration-edges.json"

internal fun Project.registerKmpDependencyEdges() {
    val module = path.removePrefix(":")
    val verifier = tasks.register("verifyKmpDependencyEdges", VerifyKmpDependencyEdges::class.java) {
        group = "verification"
    }
    afterEvaluate {
        verifier.configure { captureDependencyEdges(module, this) }
    }
}

private fun Project.captureDependencyEdges(module: String, verifier: VerifyKmpDependencyEdges) {
    with(verifier) {
        val policy = readKmpManifest(KMP_DEPENDENCY_EDGES)[module] as? Map<*, *>
            ?: error("Required dependency policy for $module is missing")
        val required = policy.configurations("required")
        val forbidden = policy.configurations("forbidden")
        requiredEdges.set(required)
        forbiddenEdges.set(forbidden)
        val declarations = configurations.names.filter { name ->
            name in required || forbidden.keys.any { it == "*" || name.startsWith(it) } &&
                DECLARATION_SUFFIXES.any { name.endsWith(it, ignoreCase = true) }
        }
        declaredEdges.set(declarations.associateWith { name ->
            configurations.getByName(name).dependencies.mapNotNull { dependency ->
                when (dependency) {
                    is ProjectDependency -> dependency.path
                    is ExternalModuleDependency -> {
                        val category = dependency.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)?.name
                        val coordinate = "${dependency.group}:${dependency.name}"
                        if (category == Category.REGULAR_PLATFORM) "platform:$coordinate" else coordinate
                    }
                    else -> null
                }
            }.sorted()
        })
    }
}

private val DECLARATION_SUFFIXES = listOf("Api", "Implementation", "CompileOnly", "RuntimeOnly")

private fun Map<*, *>.configurations(name: String): Map<String, List<String>> =
    (get(name) as Map<*, *>).entries.associate { (configuration, edges) ->
        (configuration as String) to (edges as List<*>).map { it as String }
    }

@DisableCachingByDefault(because = "Verifies declared dependencies without resolving their artifacts")
abstract class VerifyKmpDependencyEdges : DefaultTask() {

    @get:Input
    abstract val requiredEdges: MapProperty<String, List<String>>

    @get:Input
    abstract val forbiddenEdges: MapProperty<String, List<String>>

    @get:Input
    abstract val declaredEdges: MapProperty<String, List<String>>

    @TaskAction
    fun verify() {
        val required = requiredEdges.get()
        val declared = declaredEdges.get()
        check(required.isNotEmpty() && required.values.all { it.isNotEmpty() }) {
            "$path: empty required dependency inventory"
        }
        val missing = required.flatMap { (configuration, edges) ->
            val actual = declared[configuration].orEmpty()
            edges.filterNot { it in actual }.map { "$configuration -> $it" }
        }
        val forbidden = forbiddenEdges.get().flatMap { (scope, edges) ->
            declared.filterKeys { scope == "*" || it.startsWith(scope) }.flatMap { (configuration, actual) ->
                actual.filter { it in edges || it.removePrefix("platform:") in edges }
                    .map { "$configuration -> $it" }
            }
        }
        check(missing.isEmpty() && forbidden.isEmpty()) {
            "$path: missing required edges $missing, forbidden edges $forbidden"
        }
        logger.lifecycle("$path: verified ${required.values.sumOf { it.size }} required dependency edges")
    }
}
