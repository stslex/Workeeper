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
    // Carries the @Smoke / @Regression suite annotations. Without this edge androidx.test
    // cannot load the class named by ui_tests.yml's `-e annotation` filter and SILENTLY drops
    // the filter, running this module's whole suite in both the smoke and the regression run.
    // `:core:ui:test-utils` depends back on this module's main source set; that is not a cycle
    // (androidTest is a separate compilation) and `:core:ui:mvi` has carried the same shape
    // since the app-scope collapse. Enforced by `verifyInstrumentedSuiteClasspath`.
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Compose's semantics-tree test surface on the JVM side, so an accessibility assertion runs
    // under `testDebugUnitTest` and therefore gates every PR. Instrumented tests here could not:
    // both `connectedDebugAndroidTest` jobs in ui_tests.yml select by the runner's `annotation`
    // argument, and that workflow is dispatch-only in the first place. Robolectric and the Jupiter
    // `RobolectricExtension` are already on this configuration from the convention plugin; this
    // line adds the one missing piece, `runComposeUiTest`. See AccessibilitySemanticsTest.
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // The golden harness, shared with the 12 other golden-holding modules so device config,
    // tolerance and canvas width cannot drift per module.
    testImplementation(project(":core:ui:golden-harness"))
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
