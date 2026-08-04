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

android {
    // The golden harness (GoldenHarness.kt, GoldenTheme.kt) is published as a test fixture so
    // feature modules can record goldens against the SAME device config, tolerance and canvas
    // width. A copied harness would let those three drift per module, which is exactly the
    // failure §8 warns about for the rail's threshold — and here it would silently weaken the
    // gate rather than the layout.
    testFixtures.enable = true
}

dependencies {
    // Supplies the AppScope DI token (commonMain `di` package) for @ContributesBinding(AppScope).
    implementation(project(":core:core"))

    implementation(libs.dev.haze.core)
    implementation(libs.dev.haze.materials)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Compose's semantics-tree test surface on the JVM side, so an accessibility assertion runs
    // under `testDebugUnitTest` and therefore gates every PR. Instrumented tests here could not:
    // both `connectedDebugAndroidTest` jobs in ui_tests.yml select by the runner's `annotation`
    // argument, and that workflow is dispatch-only in the first place. Robolectric and the Jupiter
    // `RobolectricExtension` are already on this configuration from the convention plugin; this
    // line adds the one missing piece, `runComposeUiTest`. See AccessibilitySemanticsTest.
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Consumed by every module that records goldens.
    testFixturesImplementation(libs.paparazzi.core)
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui)
    testFixturesImplementation(libs.androidx.compose.foundation)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
