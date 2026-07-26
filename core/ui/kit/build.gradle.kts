plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Metro plugin so the app-scoped impls in this module (ActivityHolderImpl) are contributed to the
    // app-scope AppGraph via @ContributesBinding(AppScope) — the impl declares its own binding, so
    // app/app names only the bound interface and never the impl. includeJavax keeps any javax.inject
    // qualifier readable, matching the app/app + feature-module Metro config.
    alias(libs.plugins.metro)
    // Visual gate for the v3 redesign. Goldens live in src/test/snapshots/images and are
    // recorded with `:core:ui:kit:recordPaparazziDebug`, verified with `verifyPaparazziDebug`.
    alias(libs.plugins.paparazzi)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    // Supplies the AppScope DI token (commonMain `di` package) for @ContributesBinding(AppScope).
    implementation(project(":core:core"))

    implementation(libs.dev.haze.core)
    implementation(libs.dev.haze.materials)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * Liveness assertion for the visual gate.
 *
 * `verifyPaparazziDebug` is a `Test` task. A `Test` task that discovers none of the tests you
 * cared about is a *successful* task — measured, not assumed: with the golden package filtered
 * out, `verifyPaparazziDebug` exited 0 / BUILD SUCCESSFUL having executed zero golden tests and
 * written a results XML containing only an unrelated Jupiter test. Gradle's
 * `failOnNoDiscoveredTests` cannot catch that (and is off repo-wide anyway), because tests
 * *were* discovered — just not these.
 *
 * Moving the goldens onto JUnit 5 removed the *original* form of that hole: dropping the engine
 * now fails loudly with "Cannot create Launcher without at least one TestEngine", where on the
 * JUnit 4 path a missing Vintage engine was silent. It did not remove the hole in general, so
 * this closes it directly by requiring the gate to have actually run.
 *
 * The invariant: every committed golden PNG must correspond to a golden test case that
 * executed. Adding a golden test without recording still fails in Paparazzi itself; deleting a
 * golden together with its test keeps both sides in step.
 */
val goldenImagesDir = layout.projectDirectory.dir("src/test/snapshots/images")
val unitTestResultsDir = layout.buildDirectory.dir("test-results/testDebugUnitTest")

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
        val executed = resultFiles.sumOf { file ->
            val text = file.readText()
            val name = suiteName.find(text)?.groupValues?.get(1).orEmpty()
            if (name.contains(".golden.")) testCase.findAll(text).count() else 0
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
// cannot be reached with tasks.named(...) here.
tasks.matching { task ->
    task.name.startsWith("verifyPaparazzi") || task.name.startsWith("recordPaparazzi")
}.configureEach {
    finalizedBy(assertGoldenLiveness)
}
