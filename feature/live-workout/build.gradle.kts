plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // Largest feature. Route-arg feature (shape B), single @DefaultDispatcher.
    alias(libs.plugins.metro)
    // Goldens for the session's set and exercise states; harness from core:ui:golden-harness.
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.live_workout.resources"
}

metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // No public signature names its types; the graph's AppScope and DefaultDispatcher
            // annotations reach the app through the app's own core:core edge.
            implementation(project(":core:core"))

            // No public signature names a kit type either: StringResolver arrives only through
            // internal constructors.
            implementation(project(":core:ui:kit"))
            // `api`: LiveWorkoutStore, its handler store and Store impl and the public picker
            // handler extend the MVI contracts; liveWorkoutGraph extends NavGraphScope and the
            // graph's Factory takes a Screen.LiveWorkout; the State, the UI models, the picker seam
            // and PendingUndo carry the plan editor's models.
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            api(project(":core:ui:plan-editor"))
            // Production, not a fixture: the domain mapper maps its data models, which no public
            // declaration names (plan-editor's precedent).
            implementation(project(":core:data:database"))
            // `api`: the graph exposes the inherited SessionRepository and ExternalSessionWrites.
            api(project(":core:data:exercise"))

            // `api`: liveWorkoutGraph takes a Modifier and the haptic events carry a
            // HapticFeedbackType; the interactor returns Flows and the graph exposes a
            // CoroutineDispatcher; State and the UI models carry ImmutableList, ImmutableSet and
            // ImmutableMap.
            api(libs.cmp.ui)
            api(libs.coroutines.core)
            api(libs.kotlinx.collections.immutable)
            // The card's animateContentSize and animateColorAsState, and their animation specs.
            implementation(libs.cmp.animation)
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
    // ExerciseDoneRuleTest's WorkoutTargetParityFixture (Phase 7.13 D4-A): host only.
    "androidHostTestImplementation"(project(":core:data:database-test"))
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
