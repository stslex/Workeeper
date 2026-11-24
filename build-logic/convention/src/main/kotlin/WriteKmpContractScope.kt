import groovy.json.JsonOutput
import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

internal const val KMP_PREVIEW_SUBJECTS = "gradle/kmp-preview-subjects.json"
internal const val KMP_DECLARATION_CONTRACTS = "gradle/kmp-declaration-contracts.json"

internal fun Project.configureKmpContractScope(kmp: KotlinMultiplatformExtension) {
    if (path.removePrefix(":") !in readKmpManifest("gradle/kmp-migration-source-sets.json")) return
    val module = path.removePrefix(":")
    val databaseSources = provider {
        if (module !in setOf("feature:live-workout", "feature:exercise-chart")) emptyList() else {
            val databaseProject = rootProject.project(":core:data:database")
            val database = databaseProject.extensions.getByType(KotlinMultiplatformExtension::class.java)
            val fixtureProject = rootProject.project(":core:data:database-test")
            val fixtures = fixtureProject.extensions.getByType(LibraryExtension::class.java).sourceSets.getByName("main")
            val generated = listOf(databaseProject, fixtureProject).map { it.layout.buildDirectory.get().asFile }
            val files = database.targets.flatMap { it.compilations }.filter { it.name == "main" }
                .flatMap { it.allKotlinSourceSets }.flatMap { it.kotlin.files } +
                fixtureProject.files(fixtures.kotlin.directories + fixtures.java.directories).asFileTree.files
            files.filter { file -> file.extension == "kt" && generated.none(file::startsWith) }
                .map { it.absoluteFile.normalize().path }.distinct().sorted()
                .also { check(it.isNotEmpty()) { "$module: missing evaluated database symbol sources" } }
        }
    }
    val scope = tasks.register("writeKmpContractScope", WriteKmpContractScope::class.java) {
        val buildDir = layout.buildDirectory.get().asFile
        val native = kmp.targets.filterIsInstance<KotlinNativeTarget>().flatMap { it.compilations }
        fun paths(files: Iterable<java.io.File>) = files.asSequence()
            .filter { it.extension == "kt" && !it.startsWith(buildDir) }
            .map { it.absoluteFile.normalize().path }.distinct().sorted().toList()
        val nativeMain = paths(native.filter { it.name == "main" }
            .flatMap { it.allKotlinSourceSets }.flatMap { it.kotlin.files })
        val otherMain = paths(kmp.targets.filter { it !is KotlinNativeTarget && it.name != "metadata" }
            .flatMap { it.compilations }.filter { it.name == "main" }
            .flatMap { it.allKotlinSourceSets }.flatMap { it.kotlin.files })
        filesByScope.set(
            mapOf(
                "databaseSymbolFiles" to databaseSources.get(),
                "commonProductionFiles" to (nativeMain intersect otherMain.toSet()).sorted(),
                "nativeProductionFiles" to nativeMain,
                "allFiles" to paths(kmp.targets.filter { it.name != "metadata" }.flatMap { it.compilations }
                    .flatMap { it.allKotlinSourceSets }.flatMap { it.kotlin.files }),
                "sharedTestFiles" to paths(native.filter { it.name == "test" }
                    .flatMap { it.allKotlinSourceSets }.flatMap { it.kotlin.files }),
            ),
        )
        moduleDirectory.set(layout.projectDirectory.asFile.absolutePath)
        modulePath.set(module)
        verifyPreviewBodies.set(module != "core:ui:kit")
        requirePureMutatorInventory.set(module == "feature:live-workout")
        previewSubjects.set(provider {
            val entry = readKmpManifest(KMP_PREVIEW_SUBJECTS)[module]
            check(entry is List<*>) { "$module: missing preview subject policy" }
            entry.map { it as String }
        })
        expectedDeclarations.set(provider {
            val entry = readKmpManifest(KMP_DECLARATION_CONTRACTS)[module]
            check(entry is List<*>) { "$path: missing declaration policy" }
            entry.map { it as String }
        })
        output.set(layout.buildDirectory.file("kmp-verification/contracts-detekt-scope.yml"))
    }
    tasks.named("detekt", Detekt::class.java) {
        config.from(scope.flatMap { it.output })
        inputs.files(databaseSources).withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

@DisableCachingByDefault(because = "Writes checkout-local evaluated Kotlin input paths for Detekt")
abstract class WriteKmpContractScope : DefaultTask() {
    @get:Input
    abstract val filesByScope: MapProperty<String, List<String>>

    @get:Input
    abstract val moduleDirectory: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val modulePath: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val previewSubjects: org.gradle.api.provider.ListProperty<String>

    @get:Input
    abstract val verifyPreviewBodies: org.gradle.api.provider.Property<Boolean>

    @get:Input
    abstract val requirePureMutatorInventory: org.gradle.api.provider.Property<Boolean>

    @get:Input
    abstract val expectedDeclarations: org.gradle.api.provider.ListProperty<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun write() {
        val directory = java.io.File(moduleDirectory.get())
        val relative = filesByScope.get().mapValues { (_, files) ->
            files.map { java.io.File(it).relativeTo(directory).invariantSeparatorsPath }
        }
        val base = mapOf("moduleDirectory" to moduleDirectory.get())
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.toJson(mapOf("mvi-architecture" to mapOf(
                "KmpSharedContractsRule" to base + relative + mapOf("modulePath" to modulePath.get()) +
                    mapOf("requirePureMutatorInventory" to requirePureMutatorInventory.get()),
                "KmpDeclarationContractsRule" to base + relative.filterKeys {
                    it == "allFiles" || it == "commonProductionFiles"
                } + mapOf(
                    "expectedDeclarations" to expectedDeclarations.get(),
                    "previewSubjects" to previewSubjects.get(),
                    "verifyPreviewBodies" to verifyPreviewBodies.get(),
                ),
            ))))
        }
    }
}
