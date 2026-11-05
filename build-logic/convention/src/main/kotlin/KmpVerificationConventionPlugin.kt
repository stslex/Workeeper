import org.gradle.api.Plugin
import org.gradle.api.Project

class KmpVerificationConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            require(this == rootProject) { "Apply KMP verification to the root project" }
            tasks.register("verifyKmpMigrationContracts") {
                group = "verification"
                val modules = readKmpManifest(KMP_SOURCE_OWNERSHIP).keys
                check(modules.isNotEmpty()) { "Expected a nonempty source-ownership roster" }
                for (name in modules) {
                    val module = ":${name as String}"
                    check(findProject(module) != null) { "Required KMP module $module is missing" }
                    dependsOn("$module:verifyKmpSourceOwnership")
                }
                for (name in readKmpManifest(KMP_DEPENDENCY_EDGES).keys) {
                    val module = ":${name as String}"
                    check(findProject(module) != null) { "Required dependency owner $module is missing" }
                    dependsOn("$module:verifyKmpDependencyEdges")
                }
            }
            tasks.register("exportKmpVerificationModel", ExportKmpVerificationModel::class.java) {
                group = "verification"
                val manifest = readKmpManifest("gradle/kmp-native-identities.json")
                val modules = manifest.keys.map { name -> ":${name as String}" }
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
}
