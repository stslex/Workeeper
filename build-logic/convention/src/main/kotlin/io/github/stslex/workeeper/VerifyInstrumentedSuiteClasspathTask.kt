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
 * Fails when a module ships instrumented tests whose suite-selector annotation cannot be
 * resolved from the test APK's runtime classpath.
 *
 * **The defect this exists to catch.** androidx.test's `TestRequestBuilder` treats an
 * `-e annotation <fqn>` filter it cannot load as no filter at all: it drops it and runs
 * everything. `ui_tests.yml` passes exactly such a filter to select the smoke and regression
 * suites, so a module missing the annotation on its classpath runs its ENTIRE androidTest suite
 * under both selectors — silently, with nothing in the run output to say the selector was
 * ignored. Measured 2026-08-16: a deliberately nonexistent annotation FQN still started all 10
 * tests in `feature/app-dialogs/impl`, proving drop-on-unloadable rather than annotation
 * matching.
 *
 * The check is deliberately a *classpath* assertion rather than a dependency-declaration one.
 * "Does `:core:ui:test-utils` appear in the dependency block" is a proxy that a `compileOnly`
 * declaration, a configuration rename, or a future move of the annotations to another module
 * would each quietly falsify. What the instrumentation runner does is look the class up in the
 * APK's classloader, so what this task does is look the class file up on the classpath that APK
 * is assembled from.
 *
 * **Why it hangs off `assembleDebugAndroidTest`.** That task already resolves this exact
 * configuration, so the check adds resolution work to no build that was not doing it anyway,
 * and it fires precisely when a test APK is produced — in CI's `assembleDebugAndroidTest` step
 * and ahead of every local `connectedDebugAndroidTest`. The companion source-level rule
 * ([InstrumentedSuiteSelectorRule][io.github.stslex.workeeper.lint_rules.InstrumentedSuiteSelectorRule])
 * rides the much cheaper `detekt` lifecycle instead, which is what the pre-commit hook runs.
 *
 * A module with no instrumented sources is reported as such and passes. That is not a vacuous
 * green: there is no test APK whose filter could be dropped, and the report file records the
 * input count so the distinction between "nothing to check" and "checked nothing" stays legible.
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

    /** Name of the configuration [testRuntimeClasspath] came from, for the failure message. */
    @get:Input
    abstract val classpathName: Property<String>

    /**
     * The owning module's Gradle path, captured at configuration time. Reading `project` from a
     * task action is a configuration-cache violation, so the message's copy is an input.
     */
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
            |${project.path}'s build script, and annotate its tests @Smoke / @Regression.
            """.trimMargin(),
        )
    }

    /**
     * True when [entry] is readable from this classpath element. Directories are probed
     * directly; archives are scanned, including one level into a nested `classes.jar`, which is
     * how an AAR's own classes arrive on a resolved runtime classpath.
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
        val SOURCE_EXTS = setOf("kt", "java")
        val ARCHIVE_EXTS = setOf("jar", "aar", "zip")
        const val NESTED_CLASSES_JAR = "classes.jar"
    }
}
