plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // Collider — two same-typed dispatchers; the route arg is a bound instance (shape B).
    alias(libs.plugins.metro)
    // Goldens for the exercise-detail surface; the harness comes from core:ui:golden-harness.
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.exercise.resources"
}

// includeJavax keeps @Default and @MainImmediate distinct as binding keys.
metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`: the Store contract and the interactor carry ImageRef and ImageSaveResult, and
            // the graph exposes the inherited CameraPermission.
            api(project(":core:core"))

            // `api`: the State carries the kit's AppTagItem and BlockedArchiveItem; the Store, its
            // handler store and Store impl extend the MVI contracts; exerciseGraph extends
            // NavGraphScope and the graph's Factory takes a Screen.Exercise; the State and the
            // actions carry the plan editor's models.
            api(project(":core:ui:kit"))
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            api(project(":core:ui:plan-editor"))
            // Production, not a fixture: the domain mapper maps its data models, which no public
            // declaration names (plan-editor's precedent).
            implementation(project(":core:data:database"))
            // Its repositories appear only in internal constructors.
            implementation(project(":core:data:exercise"))

            // `api`: exerciseGraph takes a Modifier and Event.Haptic carries a HapticFeedbackType;
            // the interactor returns Flows and the graph exposes two CoroutineDispatchers; the
            // State and the UI models carry ImmutableList.
            api(libs.cmp.ui)
            api(libs.coroutines.core)
            api(libs.kotlinx.collections.immutable)
            // The graph's shared-transition opt-in.
            implementation(libs.cmp.animation)
            // The description block's AsyncImage and LocalPlatformContext (image-viewer's edge).
            implementation(libs.coil.compose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutine.test)
        }

        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.cmp.ui.test)
        }
    }
}

dependencies {
    "androidHostTestImplementation"(project(":core:ui:golden-harness"))

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

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
