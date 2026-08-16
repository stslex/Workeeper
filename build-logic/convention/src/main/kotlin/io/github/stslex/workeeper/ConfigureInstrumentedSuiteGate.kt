package io.github.stslex.workeeper

import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.kotlin.dsl.register

/**
 * Registers the two-part gate that keeps `ui_tests.yml`'s suite selector honest in every module.
 *
 * The two parts catch opposite signs of one defect, and neither implies the other:
 *
 * | task | asks | catches |
 * |---|---|---|
 * | `detektAndroidTestSuite` | is every `@Test` annotated `@Smoke`/`@Regression`? | a test selected by NO suite — it silently never runs |
 * | `verifyInstrumentedSuiteClasspath` | can the test APK resolve those annotations? | a filter androidx.test drops — the module runs in BOTH suites |
 *
 * Both were live when this was written (2026-08-16): `core/ui/kit` and `feature/app-dialogs/impl`
 * had the second, `core/ui/mvi` the first. The first had gone unnoticed for its entire life,
 * because a selector that matches nothing produces a green run and no diagnostic.
 *
 * **Registered here, for every module, rather than per-module by hand.** The bespoke
 * `detektAndroidTestNavigation` in `:app:app` is the precedent for the task shape, but not for
 * its scope: an opt-in gate is a convention, and forgetting to opt in is precisely how the
 * defect arrived. A module that acquires `src/androidTest` acquires the gate with it.
 *
 * The two hook onto different lifecycles on purpose. The detekt half is source-only and rides
 * `detekt`, which is what the pre-commit hook and CI's lint step run. The classpath half needs a
 * resolved runtime classpath, so it rides `assembleDebugAndroidTest` — the task that resolves
 * that configuration anyway, runs in CI, and precedes every `connectedDebugAndroidTest`. Hanging
 * the classpath half off `detekt` instead would make every commit resolve every module's
 * androidTest dependency graph to learn nothing new.
 */
internal fun Project.configureInstrumentedSuiteGate() {
    val sourceRoots = INSTRUMENTED_SOURCE_ROOTS.map(::file)

    // Registered unconditionally — including where no source root exists yet. A gate that is
    // registered only when it already has something to police cannot fail on the commit that
    // introduces the first unpoliced file.
    val detektSuite = tasks.register<Detekt>(DETEKT_TASK) {
        group = "verification"
        description =
            "Fails if an instrumented @Test is reachable by neither the smoke nor the " +
            "regression suite selector."
        setSource(files(sourceRoots))
        config.setFrom(rootProject.file("lint-rules/detekt-androidtest-suite.yml"))
        buildUponDefaultConfig = false
        // No baseline, by decision — baselines rot silently, and this gate's whole subject is
        // a failure mode that produces no visible symptom.
        reports {
            html.required.set(false)
            xml.required.set(true)
            txt.required.set(true)
            sarif.required.set(false)
            md.required.set(false)
        }
    }

    val verifyClasspath = tasks.register<VerifyInstrumentedSuiteClasspathTask>(CLASSPATH_TASK) {
        group = "verification"
        description =
            "Fails if instrumented tests exist whose suite-selector annotation is absent " +
            "from the test APK's runtime classpath."
        modulePath.set(this@configureInstrumentedSuiteGate.path)
        classpathName.set(ANDROID_TEST_RUNTIME_CLASSPATH)
        // fileTree, not the bare directories: a ConfigurableFileCollection built `from` a
        // directory yields that directory, so the task's own `isFile` filter discarded every
        // source and the gate reported "0 instrumented source files" for modules full of them
        // — green because it inspected nothing. Caught only because the task prints its input
        // count; that print is the reason this line is a fileTree and not a bug in production.
        instrumentedSources.from(sourceRoots.map(::fileTree))
        requiredClassEntries.set(REQUIRED_CLASS_ENTRIES)
        report.set(layout.buildDirectory.file("reports/instrumented-suite-gate/classpath.txt"))
        // Resolved lazily and only if the configuration exists: KMP modules and plain JVM
        // modules have no such configuration, and neither produces a test APK to mis-filter.
        //
        // Through an artifact VIEW, not the configuration directly. A raw Configuration added
        // to a file collection resolves without AGP's `artifactType` attribute, and a
        // dependency publishing several Android variants (measured on the KMP `:core:core`)
        // is then ambiguous and fails resolution outright. Asking for android-classes-jar is
        // what AGP itself asks for, and it is also the right question here: class files are
        // what a classloader reads. `lenient` keeps an unrelated resolution problem from
        // masquerading as a verdict — anything it drops is absent from the scan, which fails
        // this gate rather than passing it.
        testRuntimeClasspath.from(
            provider {
                val classpath = configurations.findByName(ANDROID_TEST_RUNTIME_CLASSPATH)
                    ?: return@provider files()
                classpath.incoming
                    .artifactView {
                        attributes.attribute(ARTIFACT_TYPE, ANDROID_CLASSES_JAR)
                        isLenient = true
                    }
                    .files
            },
        )
    }

    tasks.named("detekt") { dependsOn(detektSuite) }
    tasks.named("check") { dependsOn(detektSuite, verifyClasspath) }
    tasks.configureEach {
        if (name == ANDROID_TEST_ASSEMBLE_TASK) dependsOn(verifyClasspath)
    }
}

private const val DETEKT_TASK = "detektAndroidTestSuite"
private const val CLASSPATH_TASK = "verifyInstrumentedSuiteClasspath"
private const val ANDROID_TEST_RUNTIME_CLASSPATH = "debugAndroidTestRuntimeClasspath"
private const val ANDROID_TEST_ASSEMBLE_TASK = "assembleDebugAndroidTest"
private const val ANDROID_CLASSES_JAR = "android-classes-jar"
private val ARTIFACT_TYPE = Attribute.of("artifactType", String::class.java)

/**
 * Both source roots, always. `ExampleInstrumentedTest` lives under `src/androidTest/java` while
 * everything else is under `src/androidTest/kotlin`, and a gate that reads one of the two is a
 * gate with a hole in it.
 */
private val INSTRUMENTED_SOURCE_ROOTS = listOf("src/androidTest/kotlin", "src/androidTest/java")

private val REQUIRED_CLASS_ENTRIES = listOf(
    "io/github/stslex/workeeper/core/ui/test/annotations/Smoke.class",
    "io/github/stslex/workeeper/core/ui/test/annotations/Regression.class",
)
