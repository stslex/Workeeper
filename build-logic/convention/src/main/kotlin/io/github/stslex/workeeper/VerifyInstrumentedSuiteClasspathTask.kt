package io.github.stslex.workeeper

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile

/**
 * Fails when instrumented tests exist whose suite-selector annotation is absent from the test APK
 * classpath: androidx.test silently drops a filter it cannot load. See the phase-0 filter spec.
 */
@CacheableTask
abstract class VerifyInstrumentedSuiteClasspathTask : DefaultTask() {

    /** Every `.kt`/`.java` file under the module's instrumented-test source roots. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val instrumentedSources: ConfigurableFileCollection

    /** The resolved runtime classpath the test APK is assembled from. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val testRuntimeClasspath: ConfigurableFileCollection

    /** Class-file entries that must be present, e.g. `a/b/Smoke.class`. */
    @get:Input
    abstract val requiredClassEntries: ListProperty<String>

    /** Configuration [testRuntimeClasspath] came from; empty is a failure when sources exist. */
    @get:Input
    abstract val classpathName: Property<String>

    /** Every configuration name that was tried, for the "none matched" diagnostic. */
    @get:Input
    abstract val knownClasspathNames: ListProperty<String>

    /** The owning module's Gradle path; reading `project` in a task action is cache-illegal. */
    @get:Input
    abstract val modulePath: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val sources = instrumentedSources.files.filter { it.isFile && it.extension in SOURCE_EXTS }
        val out = report.get().asFile
        out.parentFile.mkdirs()

        if (sources.isEmpty()) {
            out.writeText("instrumented source files: 0 — no test APK to filter, nothing to verify\n")
            logger.lifecycle("${modulePath.get()}: 0 instrumented source files; nothing to verify.")
            return
        }

        // GUARD: the companion detekt rule parses Kotlin only, so a `.java` @Test is invisible
        // to it and runs in NEITHER suite; detekt's own inputs are already filtered sources.
        val javaSources = sources.filter { it.extension == JAVA_EXT }.sorted()
        if (javaSources.isNotEmpty()) {
            out.writeText(
                buildString {
                    appendLine("instrumented source files: ${sources.size}")
                    appendLine("unsupported .java sources: ${javaSources.size}")
                    javaSources.forEach { appendLine("  JAVA $it") }
                },
            )
            throw GradleException(
                """
                |${modulePath.get()} has ${javaSources.size} instrumented test source file(s) in Java:
                |${javaSources.joinToString("\n") { "  $it" }}
                |
                |The half of this gate that checks for @Smoke / @Regression is a detekt rule with a
                |Kotlin-PSI visitor, so it CANNOT SEE a .java test. A Java @Test carrying neither
                |annotation is reported by nothing and runs in NEITHER ui_tests.yml suite.
                |
                |Fix: write the test in Kotlin under src/androidTest/kotlin.
                """.trimMargin(),
            )
        }

        // Hard failure, not a skip: an empty file collection would scan zero entries, find
        // nothing "missing" only because it looked nowhere, and pass.
        if (classpathName.get().isEmpty()) {
            out.writeText(
                "instrumented source files: ${sources.size}\n" +
                    "runtime classpath configuration: NONE MATCHED\n",
            )
            throw GradleException(
                """
                |${modulePath.get()} has ${sources.size} instrumented test source file(s), but none of
                |the known runtime-classpath configurations exists on it, so this gate cannot see what
                |its test APK would load.
                |
                |Tried: ${knownClasspathNames.get().joinToString(", ")}
                |
                |An AGP-KMP module exposes a device-test configuration instead of the Android-library
                |one. Wire the correct name into ANDROID_TEST_RUNTIME_CLASSPATHS in
                |ConfigureInstrumentedSuiteGate.kt — do not let the module through unchecked.
                """.trimMargin(),
            )
        }

        val classpath = testRuntimeClasspath.files
        val missing = requiredClassEntries.get().filterNot { entry ->
            classpath.any { it.containsClassEntry(entry) }
        }

        out.writeText(
            buildString {
                appendLine("instrumented source files: ${sources.size}")
                appendLine("classpath entries scanned: ${classpath.size} (${classpathName.get()})")
                appendLine("required annotations missing: ${missing.size}")
                missing.forEach { appendLine("  MISSING $it") }
            },
        )
        logger.lifecycle(
            "${modulePath.get()}: ${sources.size} instrumented source files, " +
                "${classpath.size} classpath entries scanned, ${missing.size} missing.",
        )

        if (missing.isEmpty()) return
        throw GradleException(
            """
            |${modulePath.get()} has ${sources.size} instrumented test source file(s), but its suite
            |selector annotation(s) are absent from `${classpathName.get()}`:
            |${missing.joinToString("\n") { "  $it" }}
            |
            |androidx.test SILENTLY DROPS an `-e annotation <fqn>` filter it cannot load, so this
            |module's whole androidTest suite would run in BOTH the smoke and the regression run
            |of ui_tests.yml, and the run would report nothing wrong.
            |
            |Fix: add `androidTestImplementation(project(":core:ui:test-utils"))` to
            |${modulePath.get()}'s build script, and annotate its tests @Smoke / @Regression.
            """.trimMargin(),
        )
    }

    /**
     * True when [entry] is readable from this classpath element: directories are probed directly,
     * archives scanned one level into a nested `classes.jar`, which is how an AAR's classes arrive.
     */
    private fun File.containsClassEntry(entry: String): Boolean = when {
        isDirectory -> File(this, entry).isFile
        isFile && extension.lowercase() in ARCHIVE_EXTS -> runCatching {
            ZipFile(this).use { zip ->
                zip.getEntry(entry) != null ||
                    zip.entries().asSequence().any { nested ->
                        nested.name.endsWith(NESTED_CLASSES_JAR) &&
                            zip.getInputStream(nested).use { it.containsZipEntry(entry) }
                    }
            }
        }.getOrDefault(false)

        else -> false
    }

    private fun java.io.InputStream.containsZipEntry(entry: String): Boolean =
        java.util.zip.ZipInputStream(this).use { stream ->
            generateSequence { stream.nextEntry }.any { it.name == entry }
        }

    private companion object {
        const val JAVA_EXT = "java"
        val SOURCE_EXTS = setOf("kt", JAVA_EXT)
        val ARCHIVE_EXTS = setOf("jar", "aar", "zip")
        const val NESTED_CLASSES_JAR = "classes.jar"
    }
}
