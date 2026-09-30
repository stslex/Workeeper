package io.github.stslex.workeeper

import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.kotlin.dsl.register

/**
 * Two-part gate keeping `ui_tests.yml`'s suite selectors honest on every module: a detekt source
 * check plus a test-APK classpath check. See kmp-phase-0-instrumented-filter.md.
 */
internal fun Project.configureInstrumentedSuiteGate() {
    val sourceRoots = INSTRUMENTED_SOURCE_ROOTS.map(::file)

    // Registered unconditionally, so it can fail on the commit adding the first unpoliced file.
    val detektSuite = tasks.register<Detekt>(DETEKT_TASK) {
        group = "verification"
        description =
            "Fails if an instrumented @Test is reachable by neither the smoke nor the " +
            "regression suite selector."
        setSource(files(sourceRoots))
        config.setFrom(rootProject.file("lint-rules/detekt-androidtest-suite.yml"))
        buildUponDefaultConfig = false
        // No baseline: this gate's subject is a failure mode that produces no visible symptom.
        reports {
            html.required.set(false)
            xml.required.set(true)
            txt.required.set(true)
            sarif.required.set(false)
            md.required.set(false)
            // GUARD: own filenames — detekt's defaults collide with the plain `detekt` task,
            // which dependsOn this one and would clobber the gate's report.
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
        // The FIRST candidate that exists, or "" — and "" with instrumented sources present
        // is a hard failure in the task, not a quiet pass.
        classpathName.set(
            provider {
                ANDROID_TEST_RUNTIME_CLASSPATHS.firstOrNull { configurations.findByName(it) != null }
                    .orEmpty()
            },
        )
        knownClasspathNames.set(ANDROID_TEST_RUNTIME_CLASSPATHS)
        // GUARD: fileTree, NOT the bare directories — a file collection built `from` a
        // directory yields the directory itself, which the task's `isFile` filter discards.
        instrumentedSources.from(sourceRoots.map(::fileTree))
        requiredClassEntries.set(REQUIRED_CLASS_ENTRIES)
        report.set(layout.buildDirectory.file("reports/instrumented-suite-gate/classpath.txt"))
        // Through an artifact VIEW, not the configuration directly: a raw Configuration
        // resolves without AGP's `artifactType`, and a multi-variant dependency is ambiguous.
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
    // Every spelling of "assemble the test APK", plus the run tasks — see the two sets below.
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
 * Matching none of them, with instrumented sources present, is a failure and never a skip.
 */
private val ANDROID_TEST_RUNTIME_CLASSPATHS = listOf(
    "debugAndroidTestRuntimeClasspath",
    // The Wear application has a distribution flavor while preserving one shared androidTest.
    "devDebugAndroidTestRuntimeClasspath",
    "storeDebugAndroidTestRuntimeClasspath",
    "androidDeviceTestRuntimeClasspath",
)

/**
 * Every task that assembles an instrumented test APK: the Android library/application spelling CI
 * invokes, the AGP-KMP device-test task, and the generic lifecycle aggregate.
 */
private val ANDROID_TEST_ASSEMBLE_TASKS = setOf(
    "assembleDebugAndroidTest",
    "assembleDevDebugAndroidTest",
    "assembleStoreDebugAndroidTest",
    "assembleAndroidDeviceTest",
    "assembleAndroidTest",
)

/**
 * The instrumented RUN tasks. GUARD: `connectedDebugAndroidTest` does not depend on
 * `assembleDebugAndroidTest`, so the assemble names alone leave the gate out of the graph.
 */
private val ANDROID_TEST_RUN_TASKS = setOf(
    "connectedDebugAndroidTest",
    "connectedDevDebugAndroidTest",
    "connectedStoreDebugAndroidTest",
    "connectedAndroidDeviceTest",
)

/**
 * Every instrumented source root, always — Kotlin tests live under both `androidTest/java` and
 * `androidTest/kotlin`, and `androidDeviceTest/kotlin` is the AGP-KMP source set.
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
