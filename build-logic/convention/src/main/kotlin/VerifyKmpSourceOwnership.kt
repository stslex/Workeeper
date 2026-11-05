import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal const val KMP_SOURCE_OWNERSHIP = "gradle/kmp-migration-source-sets.json"

internal fun Project.readKmpManifest(relativePath: String): Map<*, *> =
    JsonSlurper().parseText(
        providers.fileContents(rootProject.layout.projectDirectory.file(relativePath)).asText.get(),
    ) as Map<*, *>

internal fun Project.registerSourceOwnership(kmp: KotlinMultiplatformExtension) {
    val module = path.removePrefix(":")
    val directory = layout.projectDirectory.asFile
    val buildDirectory = layout.buildDirectory
    val tracked = providers.exec {
        workingDir(directory)
        commandLine("git", "ls-files", "-z", "--", "*.kt")
    }.standardOutput.asText.map { output ->
        output.split('\u0000').filter(String::isNotEmpty).toSet()
    }

    tasks.register("verifyKmpSourceOwnership", VerifyKmpSourceOwnership::class.java) {
        group = "verification"
        dependsOn("verifyKmpNativeTargets")
        val expected = readKmpManifest(KMP_SOURCE_OWNERSHIP)[module] as? List<*>
            ?: error("Required source sets for $module are missing from $KMP_SOURCE_OWNERSHIP")
        requiredSourceSets.set(expected.map { it as String })
        trackedFiles.set(tracked)

        val compilations = kmp.targets.flatMap { target ->
            target.compilations.map { compilation ->
                "${target.name}/${compilation.name}" to compilation.allKotlinSourceSets
            }
        }.toMap()
        sourceSetCompilations.set(compilations.mapValues { (_, sets) -> sets.map { it.name }.sorted() })
        val generated = buildDirectory.get().asFile.toPath()
        val productionAndTestSets = compilations.filterKeys { !it.startsWith("metadata/") }
            .values.flatten().toSet()
        compiledFiles.set(productionAndTestSets.associate { sourceSet ->
            val kotlinFiles = sourceSet.kotlin.files.asSequence()
                .filter { it.extension == "kt" && !it.toPath().startsWith(generated) }
                .map { it.relativeTo(directory).invariantSeparatorsPath }
                .sorted()
                .toList()
            sourceSet.name to kotlinFiles
        })
        val hostTest = tasks.findByName("testAndroidHostTest") as? Test
        val aliases = linkedMapOf(
            "assembleDebug" to "assemble",
            "testDebugUnitTest" to "testAndroidHostTest",
            "lintDebug" to "lint",
            "assembleDebugAndroidTest" to "assembleAndroidDeviceTest",
            "connectedDebugAndroidTest" to "connectedAndroidDeviceTest",
        )
        disconnectedAliases.set(aliases.mapNotNull { (alias, terminal) ->
            val entry = tasks.findByName(alias)
            val expectedTask = tasks.findByName(terminal)
            val connected = entry != null && entry !is Test && expectedTask != null &&
                entry.reaches(expectedTask, mutableSetOf())
            if (connected) null else "$alias -> $terminal"
        })
        junitPlatform.set(hostTest?.enabled == true && hostTest.options is JUnitPlatformOptions)
    }
}

private fun Task.reaches(terminal: Task, seen: MutableSet<String>): Boolean {
    if (this == terminal) return enabled
    if (!enabled || !seen.add(path)) return false
    return taskDependencies.getDependencies(this).any { task ->
        task.project == project && task.reaches(terminal, seen)
    }
}

@DisableCachingByDefault(because = "Validates source ownership and alias wiring without producing an artifact")
abstract class VerifyKmpSourceOwnership : DefaultTask() {

    @get:Input
    abstract val requiredSourceSets: SetProperty<String>

    @get:Input
    abstract val trackedFiles: SetProperty<String>

    @get:Input
    abstract val compiledFiles: MapProperty<String, List<String>>

    @get:Input
    abstract val sourceSetCompilations: MapProperty<String, List<String>>

    @get:Input
    abstract val disconnectedAliases: SetProperty<String>

    @get:Input
    abstract val junitPlatform: org.gradle.api.provider.Property<Boolean>

    @TaskAction
    fun verify() {
        val required = requiredSourceSets.get()
        val compiled = compiledFiles.get()
        val tracked = trackedFiles.get()
        val orphans = tracked - compiled.values.flatten().toSet()
        val missing = required.filter { sourceSet -> compiled[sourceSet].orEmpty().none { it in tracked } }
        val compilations = sourceSetCompilations.get()
        val obligations = mapOf(
            "android/main" to setOf("commonMain", "androidMain"),
            "android/hostTest" to setOf("commonTest", "androidHostTest"),
            "android/deviceTest" to setOf("androidDeviceTest"),
            "iosArm64/main" to setOf("commonMain", "iosMain"),
            "iosArm64/test" to setOf("commonTest", "iosTest"),
            "iosSimulatorArm64/main" to setOf("commonMain", "iosMain"),
            "iosSimulatorArm64/test" to setOf("commonTest", "iosTest"),
        )
        val wrongCompilations = obligations.filterNot { (name, sets) ->
            compilations[name].orEmpty().containsAll(sets)
        }
        val failures = buildList {
            if (required.isEmpty()) add("required source-set inventory is empty")
            if (missing.isNotEmpty()) add("missing populated source sets: ${missing.sorted()}")
            if (orphans.isNotEmpty()) add("orphaned tracked Kotlin files: ${orphans.sorted()}")
            if (wrongCompilations.isNotEmpty()) add("wrong source-set membership: $wrongCompilations")
            val disconnected = disconnectedAliases.get()
            if (disconnected.isNotEmpty()) add("disconnected CI aliases: ${disconnected.sorted()}")
            if (!junitPlatform.get()) add("testAndroidHostTest must be an enabled JUnit Platform task")
        }
        check(failures.isEmpty()) { "$path:\n${failures.joinToString("\n")}" }
        logger.lifecycle("$path: verified ${tracked.size} tracked Kotlin files and ${required.size} required source sets")
    }
}
