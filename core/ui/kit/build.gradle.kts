plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // Contributes this module's app-scoped impls to AppGraph via @ContributesBinding(AppScope).
    alias(libs.plugins.metro)
    // Goldens live in src/androidHostTest/snapshots/images; record with `recordPaparazziDebug`.
    alias(libs.plugins.paparazzi)
}

metro {
    interop {
        includeJavax()
    }
}

compose.resources {
    // GUARD: both are load-bearing for the ten modules that read kit strings cross-module —
    // an internal or default-packaged Res breaks every external `Res.string.*` call site.
    publicResClass = true
    packageOfResClass = "io.github.stslex.workeeper.core.ui.kit.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Supplies the AppScope DI token (commonMain `di` package) for @ContributesBinding(AppScope).
            implementation(project(":core:core"))

            implementation(libs.coroutines.core)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.androidx.compose.paging)

            implementation(libs.dev.haze.core)
            implementation(libs.dev.haze.materials)

            // The KMP compose convention supplies runtime/foundation/material3/ui, but not
            // Animation — and the kit imports androidx.compose.animation.* directly.
            implementation(libs.cmp.animation)
            implementation(libs.cmp.material.icons.core)
            implementation(libs.cmp.material.icons.extended)

            // GUARD: `api`, not implementation — kit's public `Res.string.*` fields are
            // `StringResource`, so every cross-module caller compiles against this artifact.
            api(libs.cmp.components.resources)
        }

        androidMain.dependencies {
            // LocalActivity + WindowCompat/View APIs for the retained Android window seams.
            implementation(libs.androidx.compose.activity)
            implementation(libs.androidx.core.ktx)
            // Metro interop: javax.inject.Qualifier must be visible to includeJavax().
            implementation(libs.javax.inject)
        }

        iosTest.dependencies {
            implementation(kotlin("test"))
            // CMP UI test runner (v2 API) driving the native headless Compose scene.
            implementation(libs.cmp.ui.test)
        }
    }
}

dependencies {
    // `runComposeUiTest` on the JVM side, so accessibility assertions gate every PR under
    // `testDebugUnitTest` rather than the dispatch-only instrumented workflow.
    "androidHostTestImplementation"(libs.androidx.compose.ui.test.junit4)

    // Shared golden harness, so device config, tolerance and canvas width cannot drift.
    "androidHostTestImplementation"(project(":core:ui:golden-harness"))

    // GUARD: supplies the ComponentActivity that host-side `runComposeUiTest` launches under
    // Robolectric — the classic module got it from `debugImplementation`, which AGP-KMP lacks.
    "androidHostTestImplementation"(libs.androidx.compose.ui.test.manifest)

    // Robolectric under JUnit 5 for androidHostTest; androidx-test supplies ApplicationProvider.
    "androidHostTestImplementation"(libs.robolectric)
    "androidHostTestImplementation"(libs.robolectric.junit5.extension)
    "androidHostTestImplementation"(libs.androidx.test)

    "androidDeviceTestImplementation"(libs.bundles.android.test)
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.junit4)
    // GUARD: ui-test-manifest is versionless in the catalog; classic modules resolve it through
    // the convention's compose BOM, which the KMP convention does not add — so add it here.
    "androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)
    // GUARD: carries the @Smoke / @Regression annotations — without this edge androidx.test
    // silently drops ui_tests.yml's filter. Enforced by `verifyInstrumentedSuiteClasspath`.
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

// GUARD: the robolectric-junit5 bridge needs launcher interceptors on, or every test dies with
// "No instrumentation registered". See feature-specs/kmp-phase-3-core-collapse.md.
tasks.withType<Test>().configureEach {
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
