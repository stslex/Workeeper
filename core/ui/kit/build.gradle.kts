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
 *
 * This task reads `build/test-results/testDebugUnitTest`, which is a cacheable *output* of the
 * test task — so on a build-cache hit it would read restored XML and vouch for a run that never
 * happened. That is why the test task is marked non-cacheable in Paparazzi mode below: this
 * assertion is only as honest as the execution it inspects.
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
        // A skipped test still emits a `<testcase>` element, wrapping a `<skipped/>` child. Counting
        // raw `<testcase>` would therefore let `@Disabled` on a golden class hold the count up while
        // nothing rendered — the same "green without execution" failure this task exists to catch.
        // `<skipped>` appears only inside a `<testcase>`, so subtracting is exact.
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
// cannot be reached with tasks.named(...) here.
tasks.matching { task ->
    task.name.startsWith("verifyPaparazzi") || task.name.startsWith("recordPaparazzi")
}.configureEach {
    finalizedBy(assertGoldenLiveness)
}

/**
 * PF1 finding, measured not assumed: a bare `testDebugUnitTest` runs the golden tests but
 * compares nothing.
 *
 * `Paparazzi` decides verify-vs-record from the `paparazzi.test.verify` system property, and
 * the plugin injects that at *execution* time on its own tasks only — it is absent from the
 * test task's configured `systemProperties` under either invocation. So under a plain
 * `testDebugUnitTest` the goldens render into the build-dir report and always pass: a mutated
 * golden that `verifyPaparazziDebug` rejects sails through. That was ~6 s per CI build of
 * something that looked like a second safety net and was not one.
 *
 * Excluded from the plain run, therefore. `verifyPaparazziDebug` depends on this same test
 * task, so the exclusion must not apply when a Paparazzi task was asked for.
 *
 * The guess is start-parameter based and could in principle be wrong (a lifecycle task that
 * pulls in `verifyPaparazziDebug` without naming it). That is exactly what
 * `assertGoldenLiveness` below catches: it fails the build whenever the gate reports success
 * having executed fewer golden tests than there are committed goldens. Verified.
 */
val paparazziModeRequested = gradle.startParameter.taskNames.any { name -> "Paparazzi" in name }

tasks.matching { task -> task.name == "testDebugUnitTest" }.configureEach {
    if (paparazziModeRequested) {
        // A gate whose result can be *replayed* is not a gate. Measured, not assumed: with
        // `org.gradle.caching=true` (which `.github/properties/gradle-ci.properties` sets) and a
        // warm build cache, `rm -rf core/ui/kit/build && ./gradlew :core:ui:kit:verifyPaparazziDebug`
        // reported `testDebugUnitTest FROM-CACHE`, restored the JUnit XML as a cached *output*, and
        // `assertGoldenLiveness` then read that restored XML and announced
        // "Visual gate live: 10 golden test case(s) executed" — in a 565 ms build in which zero
        // golden tests ran. The liveness assertion was answering with the previous build's evidence.
        //
        // What this does NOT fix, because it was never broken: the goldens under
        // `src/test/snapshots/images` are declared inputs of this task, so a *changed* golden always
        // misses the cache. Verified by copying the light PNG over the dark golden — the task
        // executed and failed (`primaryButton [2] DARK FAILED`) while 19 upstream tasks still came
        // FROM-CACHE. A corrupt golden could not sail through; only the liveness *claim* was hollow.
        //
        // So the fix is scoped to the claim: in Paparazzi mode this task must actually execute.
        // Done here rather than with `--no-build-cache` on the CI step so the guarantee holds for
        // every invocation, and so upstream compilation keeps its cache hits — the flag would strip
        // caching from the whole task graph to discipline one task.
        outputs.doNotCacheIf("a visual gate must execute, not be restored from cache") { true }
        outputs.upToDateWhen { false }
    } else {
        (this as Test).filter {
            excludeTestsMatching("io.github.stslex.workeeper.core.ui.kit.golden.*")
        }
    }
}
