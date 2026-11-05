import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KmpNativeTargetsTest {

    @TempDir
    lateinit var fixture: File

    @Test
    fun conventionChecksTheConfiguredTargets() {
        val result = runner("").build()
        assertExecuted(result, TaskOutcome.SUCCESS)
        assertTrue(result.output.contains("verified 2 Native targets"), result.output)
    }

    @Test
    fun equivalentConfigurationThroughALambdaKeepsTheSameTargets() {
        val result = runner(
            """
            kotlin {
                iosArm64 { compilations.configureEach { } }
                iosSimulatorArm64 { compilations.configureEach { } }
            }
            """.trimIndent(),
        ).build()
        assertExecuted(result, TaskOutcome.SUCCESS)
    }

    @Test
    fun extraConfiguredTargetFailsThePolicyCheck() {
        val result = runner("kotlin { macosArm64() }").buildAndFail()
        assertExecuted(result, TaskOutcome.FAILED)
        assertTrue(
            result.output.contains(
                "required Native targets [iosArm64, iosSimulatorArm64], configured [iosArm64, iosSimulatorArm64, macosArm64]",
            ),
            result.output,
        )
    }

    @Test
    fun missingConfiguredTargetFailsThePolicyCheck() {
        val result = runner(
            """
            kotlin {
                targets.remove(targets.getByName("iosArm64"))
            }
            """.trimIndent(),
        ).buildAndFail()
        assertExecuted(result, TaskOutcome.FAILED)
        assertTrue(
            result.output.contains(
                "required Native targets [iosArm64, iosSimulatorArm64], configured [iosSimulatorArm64]",
            ),
            result.output,
        )
    }

    @Test
    fun orphanedTrackedKotlinFailsSourceOwnership() {
        val result = runner(
            configuration = "",
            task = "verifyKmpSourceOwnership",
            sources = listOf("src/commonMain/kotlin/Sample.kt", "src/comonMain/kotlin/Orphan.kt"),
        ).buildAndFail()
        assertExecuted(result, TaskOutcome.FAILED, "verifyKmpSourceOwnership")
        assertTrue(result.output.contains("orphaned tracked Kotlin files: [src/comonMain/kotlin/Orphan.kt]"))
    }

    @Test
    fun configuredCustomRootCompilesTheTrackedSource() {
        val result = runner(
            configuration = """kotlin.sourceSets.named("commonMain") { kotlin.srcDir("custom") }""",
            task = "verifyKmpSourceOwnership",
            sources = listOf("custom/Sample.kt"),
        ).build()
        assertExecuted(result, TaskOutcome.SUCCESS, "verifyKmpSourceOwnership")
    }

    @Test
    fun existingAliasWithoutItsTestDependencyFails() {
        val result = runner(
            configuration = """tasks.named("testDebugUnitTest") { setDependsOn(emptyList<Any>()) }""",
            task = "verifyKmpSourceOwnership",
            sources = listOf("src/commonMain/kotlin/Sample.kt"),
        ).buildAndFail()
        assertExecuted(result, TaskOutcome.FAILED, "verifyKmpSourceOwnership")
        assertTrue(result.output.contains("disconnected CI aliases: [testDebugUnitTest -> testAndroidHostTest]"))
    }

    @Test
    fun requiredModuleMissingFromTheBuildFailsTheExport() {
        val result = runner(
            configuration = "apply<KmpVerificationConventionPlugin>()",
            task = "exportKmpVerificationModel",
        ).buildAndFail()
        assertTrue(result.output.contains("Required Native module :required:module is missing"), result.output)
    }

    @Test
    fun requiredApiEdgeDoesNotAcceptAnImplementationDeclaration() {
        val result = runner(
            configuration = """dependencies.add("commonMainImplementation", "org.example:protected:1.0")""",
            task = "verifyKmpDependencyEdges",
        ).buildAndFail()
        assertExecuted(result, TaskOutcome.FAILED, "verifyKmpDependencyEdges")
        assertTrue(result.output.contains("missing required edges [commonMainApi -> org.example:protected]"))
    }

    @Test
    fun equivalentDependencyDeclarationsVerifyTheSameEdge() {
        val result = runner(
            configuration = """
                tasks.named("verifyKmpDependencyEdges").get()
                kotlin.sourceSets.named("commonMain") {
                    dependencies { api("org.example:protected:1.0") }
                }
            """.trimIndent(),
            task = "verifyKmpDependencyEdges",
        ).build()
        assertExecuted(result, TaskOutcome.SUCCESS, "verifyKmpDependencyEdges")
    }

    @Test
    fun aggregateRequiresCompletePoliciesWithoutRegisteringUncoveredVerifiers() {
        val sourceOwners = listOf("covered", "feature:app-dialogs:api")
        val dependencyOwners = sourceOwners + "app:app"
        val projects = dependencyOwners + "uncovered"
        val gradleDir = setupFixture(projects)
        fixture.resolve("build.gradle.kts").writeText(
            """
            plugins { id("workeeper.kmp.library") apply false }
            apply<KmpVerificationConventionPlugin>()
            """.trimIndent(),
        )
        git("init", "--quiet")
        for (project in projects) {
            val dir = fixture.resolve(project.replace(':', '/')).apply { mkdirs() }
            dir.resolve("build.gradle.kts").writeText(
                """
                plugins { id("workeeper.kmp.library") }
                dependencies.add("commonMainApi", "org.example:protected:1.0")
                """.trimIndent(),
            )
            dir.resolve("src/commonMain/kotlin/Sample.kt").apply {
                parentFile.mkdirs()
                writeText("package fixture\n")
            }
        }
        git("add", ".")
        gradleDir.resolve("kmp-native-identities.json").writeText("""{"covered": {}}""")
        val source = gradleDir.resolve("kmp-migration-source-sets.json")
        val edges = gradleDir.resolve("kmp-migration-edges.json")
        val sourcePolicy = """["commonMain"]"""
        val edgePolicy = """{"required": {"commonMainApi": ["org.example:protected"]}, "forbidden": {}}"""
        fun policy(owners: List<String>, row: String) = owners.joinToString(",", "{", "}") { "\"$it\": $row" }
        source.writeText(policy(sourceOwners, sourcePolicy))
        edges.writeText(policy(dependencyOwners, edgePolicy))

        val discovery = gradle("tasks", "--all").build()
        assertExecuted(discovery, TaskOutcome.SUCCESS, "tasks")
        assertTrue(discovery.output.contains("covered:verifyKmpSourceOwnership"), discovery.output)
        assertTrue(discovery.output.contains("covered:verifyKmpDependencyEdges"), discovery.output)
        assertFalse(discovery.output.contains("uncovered:verifyKmpSourceOwnership"), discovery.output)
        assertFalse(discovery.output.contains("uncovered:verifyKmpDependencyEdges"), discovery.output)

        for ((manifest, changed, diagnostic) in listOf(
            Triple(edges, policy(dependencyOwners - "app:app", edgePolicy), "missing=[app:app] (1)"),
            Triple(source, policy(sourceOwners - "feature:app-dialogs:api", sourcePolicy),
                "missing=[feature:app-dialogs:api] (1)"),
            Triple(edges, policy(dependencyOwners + "uncovered", edgePolicy), "extra=[uncovered] (1)"),
        )) {
            val original = manifest.readBytes()
            try {
                manifest.writeText(changed)
                val result = gradle(":verifyKmpMigrationContracts").buildAndFail()
                assertTrue(result.output.contains(diagnostic), result.output)
            } finally {
                manifest.writeBytes(original)
                assertTrue(original.contentEquals(manifest.readBytes()))
            }
        }

        val restored = gradle(":verifyKmpMigrationContracts").build()
        for (project in sourceOwners) {
            assertExecuted(restored, TaskOutcome.SUCCESS, "$project:verifyKmpSourceOwnership")
        }
        for (project in dependencyOwners) {
            assertExecuted(restored, TaskOutcome.SUCCESS, "$project:verifyKmpDependencyEdges")
        }
    }

    private fun runner(
        configuration: String,
        task: String = "verifyKmpNativeTargets",
        sources: List<String> = emptyList(),
    ): GradleRunner {
        val gradleDir = setupFixture()
        gradleDir.resolve("kmp-migration-source-sets.json").writeText("""{"": ["commonMain"]}""")
        gradleDir.resolve("kmp-native-identities.json").writeText("""{"required:module": {}}""")
        gradleDir.resolve("kmp-migration-edges.json").writeText(
            """{"": {"required": {"commonMainApi": ["org.example:protected"]}, "forbidden": {}}}""",
        )
        if (sources.isNotEmpty()) {
            git("init", "--quiet")
            for (name in sources) {
                fixture.resolve(name).apply {
                    parentFile.mkdirs()
                    writeText("package fixture\n")
                }
            }
            git("add", "--", *sources.toTypedArray())
        }
        fixture.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("workeeper.kmp.library")
            }

            $configuration
            """.trimIndent(),
        )
        return gradle(task)
    }

    private fun setupFixture(projects: List<String> = emptyList()): File {
        val repository = File(System.getProperty("workeeper.repository"))
        val gradleDir = fixture.resolve("gradle").apply { mkdir() }
        for (filename in listOf("libs.versions.toml", "kmp-native-targets.txt")) {
            repository.resolve("gradle/$filename").copyTo(gradleDir.resolve(filename))
        }
        fixture.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "kmp-verification-fixture"
            include(":lint-rules")
            ${projects.joinToString("\n") { "include(\":$it\")" }}
            """.trimIndent(),
        )
        fixture.resolve("lint-rules").mkdir()
        return gradleDir
    }

    private fun gradle(vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(fixture)
            .withGradleInstallation(File(System.getProperty("workeeper.gradleInstallation")))
            .withPluginClasspath()
            .withArguments(
                *arguments,
                "--rerun-tasks",
                "--no-build-cache",
                "--no-configuration-cache",
                "--full-stacktrace",
                "--console=plain",
            )
    private fun git(vararg args: String) {
        val process = ProcessBuilder("git", *args)
            .directory(fixture)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, process.waitFor(), output)
    }

    private fun assertExecuted(
        result: BuildResult,
        outcome: TaskOutcome,
        task: String = "verifyKmpNativeTargets",
    ) {
        assertEquals(outcome, result.task(":$task")?.outcome, result.output)
    }
}
