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
 * | `verifyInstrumentedSuiteClasspath` | can the test APK resolve those annotations, and is every source Kotlin? | a filter androidx.test drops — the module runs in BOTH suites; and a `.java` test the detekt half cannot parse |
 *
 * Both signs are reachable and both have been observed; the measured cases live in
 * `documentation/feature-specs/kmp-phase-0-instrumented-filter.md` → "One hole, two opposite signs".
 *
 * **Registered here, for every module, rather than per-module by hand.** The bespoke
 * `detektAndroidTestNavigation` in `:app:app` is the precedent for the task shape, but not for
 * its scope: an opt-in gate is a convention, and a module that forgets to opt in is exactly the
 * module that needs it. A module that acquires instrumented sources acquires the gate with them.
 *
 * The two hook onto different lifecycles on purpose. The detekt half is source-only and rides
 * `detekt`, which is what the pre-commit hook and CI's lint step run. The classpath half needs a
 * resolved runtime classpath, so it rides the `assemble*AndroidTest` task — which resolves that
 * configuration anyway, runs in CI, and precedes every `connectedDebugAndroidTest`. Hanging the
 * classpath half off `detekt` instead would make every commit resolve every module's androidTest
 * dependency graph to learn nothing new.
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
            // Own filenames. Detekt's defaults are `detekt.xml`/`detekt.txt`, which the plain
            // `detekt` task in the same module also writes — and since `detekt` dependsOn this
            // task, the plain run overwrites the gate's report every time. A gate whose report is
            // clobbered by the task that triggers it has no durable evidence of its own.
            xml.outputLocation.set(layout.buildDirectory.file("reports/detekt/$DETEKT_TASK.xml"))
            txt.outputLocation.set(layout.buildDirectory.file("reports/detekt/$DETEKT_TASK.txt"))
        }
    }

    val verifyClasspath = tasks.register<VerifyInstrumentedSuiteClasspathTask>(CLASSPATH_TASK) {
        group = "verification"
        description =
            "Fails if instrumented tests exist whose suite-selector annotation is absent from " +
            "the test APK's runtime classpath, or if any instrumented test is written in Java, " +
            "which the Kotlin-PSI selector rule cannot inspect."
        modulePath.set(this@configureInstrumentedSuiteGate.path)
        // The FIRST candidate that exists, or "" — and "" with instrumented sources present is a
        // hard failure in the task, not a quiet pass. An Android library resolves
        // `debugAndroidTestRuntimeClasspath`; an AGP-KMP module that calls `withDeviceTest`
        // resolves the device-test one instead. Guessing wrong must not look like "nothing to
        // check", because that is the same silent vanish this gate exists to close.
        classpathName.set(
            provider {
                ANDROID_TEST_RUNTIME_CLASSPATHS.firstOrNull { configurations.findByName(it) != null }
                    .orEmpty()
            },
        )
        knownClasspathNames.set(ANDROID_TEST_RUNTIME_CLASSPATHS)
        // fileTree, NOT the bare directories: a ConfigurableFileCollection built `from` a
        // directory yields that directory itself, which the task's `isFile` filter then discards
        // — leaving it with zero sources and a vacuous pass over a module full of tests.
        instrumentedSources.from(sourceRoots.map(::fileTree))
        requiredClassEntries.set(REQUIRED_CLASS_ENTRIES)
        report.set(layout.buildDirectory.file("reports/instrumented-suite-gate/classpath.txt"))
        // Resolved lazily, and only through whichever candidate configuration this module
        // actually has. A plain JVM module has none and also has no instrumented sources, so it
        // falls out harmlessly; a module that HAS instrumented sources but matches no candidate
        // is failed by the task rather than skipped.
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
                val classpath = ANDROID_TEST_RUNTIME_CLASSPATHS
                    .firstNotNullOfOrNull { configurations.findByName(it) }
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
    // Every spelling of "assemble the test APK" — see ANDROID_TEST_ASSEMBLE_TASKS for which name
    // belongs to which plugin, and for the CI-reachability caveat that this hook cannot fix on its
    // own. Hooking only the Android-library name leaves the gate structurally inert on every module
    // phases 6 and 7 convert, which is the wrong direction of travel for a gate whose entire
    // subject is checks that quietly police nothing.
    tasks.configureEach {
        if (name in ANDROID_TEST_ASSEMBLE_TASKS || name in ANDROID_TEST_RUN_TASKS) {
            dependsOn(verifyClasspath)
        }
    }
}

private const val DETEKT_TASK = "detektAndroidTestSuite"
private const val CLASSPATH_TASK = "verifyInstrumentedSuiteClasspath"
private const val ANDROID_CLASSES_JAR = "android-classes-jar"
private val ARTIFACT_TYPE = Attribute.of("artifactType", String::class.java)

/**
 * Runtime-classpath configurations that can back an instrumented test APK, in priority order.
 * `debugAndroidTestRuntimeClasspath` is the Android library/application spelling;
 * `androidDeviceTestRuntimeClasspath` is the AGP-KMP one, which appears once a KMP module calls
 * `withDeviceTest`. A module with instrumented sources matching NONE of these is failed by
 * [VerifyInstrumentedSuiteClasspathTask], never skipped.
 */
private val ANDROID_TEST_RUNTIME_CLASSPATHS = listOf(
    "debugAndroidTestRuntimeClasspath",
    "androidDeviceTestRuntimeClasspath",
)

/**
 * Every task that assembles an instrumented test APK.
 *
 * `assembleDebugAndroidTest` is the Android library/application spelling and the one CI invokes.
 * `assembleAndroidDeviceTest` is the AGP-KMP device-test APK task — the name is measured in
 * `documentation/feature-specs/kmp-phase-2-probes.md` → "P4c", NOT `assembleAndroidTest`, which is
 * only the generic lifecycle aggregate a KMP module exposes whether or not it has device tests.
 *
 * The aggregate is listed anyway: hooking it costs nothing and covers anyone who invokes it by
 * hand. It is not a substitute for the real one.
 *
 * **This set alone does not make the gate reachable on a converted KMP module.** The same probe
 * records that CI's `assembleDebugAndroidTest` will not build a deviceTest APK at all, and that the
 * first instrumented-test module to convert needs an
 * `assembleDebugAndroidTest → assembleAndroidDeviceTest` alias in the KMP convention. Until that
 * alias exists, a converted module's APK — and therefore this gate — stays outside CI's task graph.
 * That alias belongs with the conversion that needs it, where it can actually be exercised.
 */
private val ANDROID_TEST_ASSEMBLE_TASKS = setOf(
    "assembleDebugAndroidTest",
    "assembleAndroidDeviceTest",
    "assembleAndroidTest",
)

/**
 * The instrumented RUN tasks. `connectedDebugAndroidTest` does NOT depend on the
 * `assembleDebugAndroidTest` lifecycle task — it consumes the APK artifacts from their producers —
 * so hooking only the assemble names leaves a developer's direct
 * `./gradlew connectedDebugAndroidTest` running with this gate absent from the task graph
 * (verified with `--dry-run`). CI is covered only incidentally, by assembling in an earlier step.
 * `connectedAndroidDeviceTest` is the AGP-KMP spelling
 * (`documentation/feature-specs/kmp-phase-2-probes.md` → "P4c").
 */
private val ANDROID_TEST_RUN_TASKS = setOf(
    "connectedDebugAndroidTest",
    "connectedAndroidDeviceTest",
)

/**
 * Every instrumented source root, always. `ExampleInstrumentedTest` lives under
 * `src/androidTest/java` while everything else is under `src/androidTest/kotlin`, and a gate that
 * reads one of the two is a gate with a hole in it.
 *
 * Those two are DIRECTORIES holding Kotlin. Listing the `java` one does NOT extend the detekt rule
 * to the Java *language* — detekt parses Kotlin only — which is why
 * [VerifyInstrumentedSuiteClasspathTask] rejects `.java` instrumented sources outright rather than
 * letting them past unexamined.
 *
 * `src/androidDeviceTest/kotlin` is the AGP-KMP instrumented source set, which is what phases 6 and
 * 7 convert modules onto. Listed now so the gate starts policing the first such module the day it
 * appears, rather than the day someone notices it was never policed.
 */
private val INSTRUMENTED_SOURCE_ROOTS = listOf(
    "src/androidTest/kotlin",
    "src/androidTest/java",
    "src/androidDeviceTest/kotlin",
)

private val REQUIRED_CLASS_ENTRIES = listOf(
    "io/github/stslex/workeeper/core/ui/test/annotations/Smoke.class",
    "io/github/stslex/workeeper/core/ui/test/annotations/Regression.class",
)
