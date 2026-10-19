plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // Collider — two same-typed dispatchers; the route arg is a bound instance (shape B).
    alias(libs.plugins.metro)
    // The training editor's only visual gate (§27); the harness comes from core:ui:golden-harness.
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.single_training.resources"
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
            // Its types appear only in internal constructors and internal code.
            implementation(project(":core:core"))

            // `api`: the State carries the kit's AppTagItem; the Store, its handler store and Store
            // impl extend the MVI contracts; singleTrainingsGraph extends NavGraphScope and the
            // graph's Factory takes a Screen.Training; the UI models and events carry the plan
            // editor's ExerciseTypeUiModel and PlanSetUiModel.
            api(project(":core:ui:kit"))
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            api(project(":core:ui:plan-editor"))
            // Production, not a fixture: the domain mapper maps its data models, which no public
            // declaration names (plan-editor's precedent).
            implementation(project(":core:data:database"))
            // `api`: the graph exposes the inherited SessionConflictResolver.
            api(project(":core:data:exercise"))

            // `api`: singleTrainingsGraph takes a Modifier and Event.HapticClick carries a
            // HapticFeedbackType; the interactor returns Flows and the graph exposes two
            // CoroutineDispatchers; State and the UI models carry ImmutableList / ImmutableSet.
            api(libs.cmp.ui)
            api(libs.coroutines.core)
            api(libs.kotlinx.collections.immutable)
            // The card's AnimatedVisibility and the graph's shared-transition opt-in.
            implementation(libs.cmp.animation)
            // Icons.Default.Search and Icons.Filled.DragHandle.
            implementation(libs.cmp.material.icons.extended)
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
