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
 * ignored. That the behaviour is drop-on-unloadable rather than annotation matching was
 * established by experiment; the measurement lives in
 * `documentation/feature-specs/kmp-phase-0-instrumented-filter.md` → "One hole, two opposite
 * signs".
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

    /**
     * Name of the configuration [testRuntimeClasspath] came from, or empty when this module has
     * none of the known ones — which, with instrumented sources present, is itself a failure.
     */
    @get:Input
    abstract val classpathName: Property<String>

    /** Every configuration name that was tried, for the "none matched" diagnostic. */
    @get:Input
    abstract val knownClasspathNames: ListProperty<String>

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

        // The companion coverage check is a DETEKT rule, and detekt parses Kotlin only: its visitor
        // is `InstrumentedSuiteSelectorRule.visitNamedFunction(KtNamedFunction)` and its source
        // filter admits `.kt`/`.kts` whatever directories the task is handed. Listing
        // `src/androidTest/java` there buys the Kotlin files sitting in that directory and nothing
        // else, so a `.java` @Test carrying neither @Smoke nor @Regression is invisible to it and
        // runs in NEITHER suite — the exact failure this gate exists to make impossible.
        //
        // The guard lives here rather than in a `doFirst` on the detekt half because that task's
        // inputs are its FILTERED source: adding a `.java` file leaves it UP-TO-DATE, so the guard
        // would not run. This task's `instrumentedSources` is the unfiltered tree, so a new `.java`
        // file invalidates it. Rationale and the measured probe:
        // `documentation/feature-specs/kmp-phase-0-instrumented-filter.md` → "The gate".
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

        // Instrumented sources with no recognized runtime-classpath configuration behind them.
        // This is a hard failure and not a skip: an empty file collection would scan zero entries,
        // find zero of the required classes "missing" only because it looked nowhere, and pass.
        // That is the vacuous green this whole gate exists to make impossible, and it is exactly
        // the shape a module takes on the day it converts to AGP-KMP without the device-test
        // configuration being wired.
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
        const val JAVA_EXT = "java"
        val SOURCE_EXTS = setOf("kt", JAVA_EXT)
        val ARCHIVE_EXTS = setOf("jar", "aar", "zip")
        const val NESTED_CLASSES_JAR = "classes.jar"
    }
}
