// SPDX-License-Identifier: GPL-3.0-only
//
// The visual gate's liveness assertion, shared by every module that records Paparazzi goldens.
// Applied with `apply(from = "$rootDir/gradle/golden-gate.gradle.kts")`. See testing.md.

// Module kind decides the golden/XML locations and which task renders the goldens: classic
// Android modules keep `src/test` + `testDebugUnitTest`; AGP-KMP modules use `src/androidHostTest`
// + `testAndroidHostTest`. See kmp-phase-7-1-ui-kit.md.
val isKmpModule = pluginManager.hasPlugin("com.android.kotlin.multiplatform.library")

/**
 * Liveness assertion: every committed golden PNG must match a golden test case that actually
 * executed, since a `Test` task discovering none of them still exits 0. See testing.md.
 */
val goldenImagesDir = layout.projectDirectory.dir(
    if (isKmpModule) "src/androidHostTest/snapshots/images" else "src/test/snapshots/images",
)
val unitTestResultsDir = layout.buildDirectory.dir(
    if (isKmpModule) "test-results/testAndroidHostTest" else "test-results/testDebugUnitTest",
)

val assertGoldenLiveness = tasks.register("assertGoldenLiveness") {
    group = "verification"
    description = "Fails if the Paparazzi goldens did not actually execute."

    val imagesDir = goldenImagesDir
    val resultsDir = unitTestResultsDir

    doLast {
        val images = imagesDir.asFile
            .listFiles { file -> file.isFile && file.name.endsWith(".png") }
            ?.size
            ?: 0
        check(images > 0) {
            "No golden images under ${imagesDir.asFile}. The visual gate is not guarding anything."
        }

        val resultFiles = resultsDir.get().asFile
            .listFiles { file -> file.isFile && file.name.endsWith(".xml") }
            ?.toList()
            .orEmpty()
        check(resultFiles.isNotEmpty()) {
            "No unit-test results XML in ${resultsDir.get().asFile}. " +
                "The Paparazzi task reported success without running any test at all."
        }

        val suiteName = Regex("""<testsuite[^>]*\bname="([^"]+)"""")
        val testCase = Regex("""<testcase\b""")
        // A skipped test still emits a `<testcase>`, so subtract `<skipped>` to count real renders.
        val skipped = Regex("""<skipped\b""")
        val executed = resultFiles.sumOf { file ->
            val text = file.readText()
            val name = suiteName.find(text)?.groupValues?.get(1).orEmpty()
            if (name.contains(".golden.")) {
                testCase.findAll(text).count() - skipped.findAll(text).count()
            } else {
                0
            }
        }

        check(executed >= images) {
            "Visual gate did not run: $executed golden test case(s) executed but $images golden " +
                "image(s) are committed. A Paparazzi task that skips its tests still exits 0, so " +
                "this is the check that turns that into a failure."
        }
        logger.lifecycle("Visual gate live: $executed golden test case(s) executed for $images golden image(s).")
    }
}

// The Paparazzi tasks are registered per-variant after this script is evaluated, so they
// cannot be reached with tasks.named(...) here. On a KMP module the prefix also matches the
// verify/recordPaparazziDebug compatibility aliases; finalizedBy is idempotent, so the
// assertion still runs once.
tasks.matching { task ->
    task.name.startsWith("verifyPaparazzi") || task.name.startsWith("recordPaparazzi")
}.configureEach {
    finalizedBy(assertGoldenLiveness)
}

/**
 * A bare unit-test run renders the goldens but compares nothing, so they are excluded from it.
 * The mode guess is start-parameter based; `assertGoldenLiveness` catches a wrong guess.
 */
val paparazziModeRequested = gradle.startParameter.taskNames.any { name -> "Paparazzi" in name }

if (isKmpModule) {
    // GUARD: the KMP module's `testDebugUnitTest` is a plain lifecycle alias — casting it to
    // `Test` is a measured ClassCastException (probe P3). Configure the real host `Test` tasks.
    tasks.withType<Test>().configureEach {
        if (paparazziModeRequested) {
            // GUARD: a gate whose result can be replayed from the cache is not a gate — the
            // liveness assertion reads this task's cached XML output. See documentation/testing.md.
            outputs.doNotCacheIf("a visual gate must execute, not be restored from cache") { true }
            outputs.upToDateWhen { false }
        } else {
            filter {
                excludeTestsMatching("*.golden.*")
                // Filtered-to-zero is a legal state for a golden-only module's plain run; the
                // filter's own fail-on-no-match fires independently of failOnNoDiscoveredTests.
                isFailOnNoMatchingTests = false
            }
        }
    }
} else {
    tasks.matching { task -> task.name == "testDebugUnitTest" }.configureEach {
        if (paparazziModeRequested) {
            // GUARD: a gate whose result can be replayed from the cache is not a gate — the
            // liveness assertion reads this task's cached XML output. See documentation/testing.md.
            outputs.doNotCacheIf("a visual gate must execute, not be restored from cache") { true }
            outputs.upToDateWhen { false }
        } else {
            (this as Test).filter {
                excludeTestsMatching("*.golden.*")
            }
        }
    }
}
