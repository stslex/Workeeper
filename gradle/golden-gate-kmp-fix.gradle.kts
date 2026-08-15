// SPDX-License-Identifier: GPL-3.0-only
//
// P3 FIX-SHAPE ARTIFACT (probe branch only — the mergeable change lands with Phase 7's kit
// conversion, not before). This is gradle/golden-gate.gradle.kts with its three
// classic-module hardcodes parametrized on the applied Android plugin. Classic modules keep
// byte-identical behavior; KMP modules get the same three guarantees against their real
// task/dir names.
//
// The three divergences on com.android.kotlin.multiplatform.library, each measured by P3:
//   1. The unit-test task is testAndroidHostTest; "testDebugUnitTest" is a plain alias Task,
//      so the classic script's `(this as Test)` cast throws ClassCastException.
//   2. Paparazzi's snapshotDir derives from the host-test source set: goldens live in
//      src/androidHostTest/snapshots/images, not src/test/snapshots/images.
//   3. Test results land in build/test-results/testAndroidHostTest, not .../testDebugUnitTest.

val isKmpModule = pluginManager.hasPlugin("com.android.kotlin.multiplatform.library")

val unitTestTaskName = if (isKmpModule) "testAndroidHostTest" else "testDebugUnitTest"
val goldenImagesDir = layout.projectDirectory.dir(
    if (isKmpModule) "src/androidHostTest/snapshots/images" else "src/test/snapshots/images",
)
val unitTestResultsDir = layout.buildDirectory.dir("test-results/$unitTestTaskName")

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
                "image(s) are committed."
        }
        logger.lifecycle("Visual gate live: $executed golden test case(s) executed for $images golden image(s).")
    }
}

tasks.matching { task ->
    task.name.startsWith("verifyPaparazzi") || task.name.startsWith("recordPaparazzi")
}.configureEach {
    finalizedBy(assertGoldenLiveness)
}

val paparazziModeRequested = gradle.startParameter.taskNames.any { name -> "Paparazzi" in name }

// withType<Test> instead of a name-only match: on a KMP module the repo-wide task name
// belongs to a plain alias Task, and the classic script's unguarded cast is exactly
// divergence 1 above.
tasks.withType<Test>().matching { task -> task.name == unitTestTaskName }.configureEach {
    if (paparazziModeRequested) {
        outputs.doNotCacheIf("a visual gate must execute, not be restored from cache") { true }
        outputs.upToDateWhen { false }
    } else {
        filter {
            excludeTestsMatching("*.golden.*")
            // A module whose ONLY host tests are goldens must not fail the plain run:
            // the exclusion leaves zero matches and the filter's own fail-on-no-match
            // (independent of failOnNoDiscoveredTests) turns that into
            // "No tests found for given includes" — measured on the probe module.
            isFailOnNoMatchingTests = false
        }
    }
}
