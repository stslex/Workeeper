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
    //
    // compileOnly, not implementation, and the reason is the androidTest classpath: AGP wires a
    // module's OWN test fixtures onto its `androidTest` runtime classpath, so anything declared
    // `testFixturesImplementation` here lands in the instrumented-test APK of a module that has an
    // `androidTest` source set — which this one does (AppConfirmationDialogTest). Paparazzi drags
    // in layoutlib + com.android.tools:sdk-common + protobuf-java, and `:core:core` brings
    // protobuf-javalite via firebase-perf, so the two collide:
    // `checkDebugAndroidTestDuplicateClasses` and `mergeDebugAndroidTestJavaResource` both failed
    // (the latter on `google/protobuf/empty.proto`, then on JUnit 5's `META-INF/LICENSE.md`).
    // Measured, not assumed: `:core:ui:kit:dependencyInsight --configuration
    // debugAndroidTestRuntimeClasspath --dependency app.cash.paparazzi` listed it before this
    // change and finds nothing after; the eight other modules that apply the Paparazzi *plugin*
    // and have androidTest sources were clean throughout, so the plugin was never the leak.
    //
    // Nothing is lost at runtime. The harness is a JVM screenshot harness: it only ever executes
    // on a `testDebugUnitTest` classpath, and every one of the twelve modules that consume
    // `testFixtures(project(":core:ui:kit"))` applies the Paparazzi plugin itself and gets
    // junit-jupiter from the convention plugin's `test` bundle — set containment checked, not
    // assumed. The cost is that a future consumer which forgets the plugin fails at run time with
    // NoClassDefFoundError rather than at compile time; `assertGoldenLiveness` (golden-gate.gradle.kts)
    // turns that into a build failure rather than a silent pass.
    testFixturesCompileOnly(libs.paparazzi.core)
    testFixturesCompileOnly(platform(libs.junit.bom))
    testFixturesCompileOnly(libs.junit.jupiter)
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui)
    testFixturesImplementation(libs.androidx.compose.foundation)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
