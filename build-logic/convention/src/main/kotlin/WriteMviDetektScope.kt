import groovy.json.JsonOutput
import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

internal fun Project.configureMviDetektScope(kmp: KotlinMultiplatformExtension) {
    val scope = tasks.register("writeMviDetektScope", WriteMviDetektScope::class.java) {
        val buildDir = layout.buildDirectory.get().asFile
        val targets = kmp.targets.filterNot { it.name == "metadata" }
        val compilations = targets.flatMap { it.compilations }
        val native = targets.filterIsInstance<KotlinNativeTarget>().flatMap { it.compilations }
        val production = compilations.filter { it.name == "main" }.kotlinPaths(buildDir)
        val nativeMain = native.filter { it.name == "main" }.kotlinPaths(buildDir)
        val nativeTest = native.filter { it.name == "test" }.kotlinPaths(buildDir)
        check(nativeMain.isNotEmpty()) { "Expected evaluated shared MVI production inputs" }
        filesByScope.set(
            mapOf(
                "sharedProductionFiles" to nativeMain.sorted(),
                "sharedTestFiles" to nativeTest.sorted(),
                "productionFiles" to production.sorted(),
                "testFiles" to (compilations.filterNot { it.name == "main" }.kotlinPaths(buildDir) - production).sorted(),
            ),
        )
        output.set(layout.buildDirectory.file("kmp-verification/mvi-detekt-scope.yml"))
    }
    tasks.named("detekt", Detekt::class.java) {
        config.setFrom(
            rootProject.layout.projectDirectory.file("lint-rules/detekt.yml"),
            scope.flatMap { it.output },
        )
    }
}

private fun List<KotlinCompilation<*>>.kotlinPaths(buildDir: File): Set<String> =
    flatMap { it.allKotlinSourceSets }.toSet().flatMap { it.kotlin.files }.asSequence()
        .filter { it.extension == "kt" && !it.startsWith(buildDir) }
        .map { it.absoluteFile.normalize().path }
        .toSet()

@DisableCachingByDefault(because = "Writes checkout-local Kotlin file paths for Detekt")
abstract class WriteMviDetektScope : DefaultTask() {

    @get:Input
    abstract val filesByScope: MapProperty<String, List<String>>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun write() {
        val config = mapOf("mvi-architecture" to mapOf("MviSharedPlatformRule" to filesByScope.get()))
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.toJson(config))
        }
    }
}
