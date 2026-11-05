import org.gradle.api.Plugin
import org.gradle.api.Project

internal const val KMP_NATIVE_IDENTITIES = "gradle/kmp-native-identities.json"

class KmpVerificationConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            require(this == rootProject) { "Apply KMP verification to the root project" }
            tasks.register("verifyKmpMigrationContracts") {
                group = "verification"
                val sourceOwners = nativeModules() + "feature:app-dialogs:api"
                val dependencyOwners = sourceOwners + "app:app"
                checkCoverage(KMP_SOURCE_OWNERSHIP, sourceOwners)
                checkCoverage(KMP_DEPENDENCY_EDGES, dependencyOwners)
                for (name in sourceOwners) {
                    val module = ":$name"
                    check(findProject(module) != null) { "Required KMP module $module is missing" }
                    dependsOn("$module:verifyKmpSourceOwnership")
                }
                for (name in dependencyOwners) {
                    val module = ":$name"
                    check(findProject(module) != null) { "Required dependency owner $module is missing" }
                    dependsOn("$module:verifyKmpDependencyEdges")
                }
            }
            tasks.register("exportKmpVerificationModel", ExportKmpVerificationModel::class.java) {
                group = "verification"
                val modules = nativeModules().map { name -> ":$name" }
                requiredModules.set(modules)
                for (module in modules) {
                    val required = findProject(module)
                        ?: error("Required Native module $module is missing from the evaluated build")
                    dependsOn("$module:exportKmpProjectVerificationModel")
                    moduleModels.from(required.layout.buildDirectory.file("kmp-verification/module.json"))
                }
                output.set(layout.buildDirectory.file("kmp-verification/model.json"))
            }
        }
    }

    private fun Project.nativeModules(): Set<String> {
        val modules = readKmpManifest(KMP_NATIVE_IDENTITIES).keys.map { it as String }.toSet()
        check(modules.isNotEmpty()) { "Expected a nonempty Native module roster" }
        return modules
    }

    private fun Project.checkCoverage(manifest: String, required: Set<String>) {
        val actual = readKmpManifest(manifest).keys.map { it as String }.toSet()
        val missing = (required - actual).sorted()
        val extra = (actual - required).sorted()
        check(missing.isEmpty() && extra.isEmpty()) {
            "$manifest covers ${actual.size} of ${required.size} expected modules, " +
                "missing=$missing (${missing.size}), extra=$extra (${extra.size})"
        }
    }
}
