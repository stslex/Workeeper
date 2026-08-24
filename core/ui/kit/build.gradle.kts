plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Contributes this module's app-scoped impls to AppGraph via @ContributesBinding(AppScope).
    alias(libs.plugins.metro)
    // Goldens live in src/test/snapshots/images; record with `recordPaparazziDebug`.
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
    // GUARD: carries the @Smoke / @Regression annotations — without this edge androidx.test
    // silently drops ui_tests.yml's filter. Enforced by `verifyInstrumentedSuiteClasspath`.
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // `runComposeUiTest` on the JVM side, so accessibility assertions gate every PR under
    // `testDebugUnitTest` rather than the dispatch-only instrumented workflow.
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Shared golden harness, so device config, tolerance and canvas width cannot drift.
    testImplementation(project(":core:ui:golden-harness"))
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
