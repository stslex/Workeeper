plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // Non-collider, PLAIN Store (archive template), single @DefaultDispatcher (no collision).
    alias(libs.plugins.metro)
    // Goldens for the all-exercises surface. This module had none, and the v3 rebuild rewrites the
    // row, the list, the FAB, the empty state and the tag band at once — a whole-surface change with
    // no before-picture is a diff nobody can read. The harness is NOT copied: it comes from
    // core:ui:golden-harness, so device config, tolerance and canvas width cannot drift between
    // this module and the sibling it must stay in step with.
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.all_exercises.resources"
}

// Metro reads javax.inject qualifiers so the inherited @DefaultDispatcher keeps its qualifier.
metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))

            // `api`: AllExercisesStore.State carries the kit's PagingUiState and BlockedArchiveItem,
            // AllExercisesStore and AllExercisesHandlerStore extend the MVI contracts, and
            // allExercisesGraph extends NavGraphScope.
            api(project(":core:ui:kit"))
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            implementation(project(":core:data:exercise"))

            // `api`: allExercisesGraph takes a Modifier and Event.Haptic carries a
            // HapticFeedbackType; AllExercisesInteractor and AllExercisesStore.State expose Flow and
            // PagingData; State and ExerciseUiModel carry ImmutableList / ImmutableSet.
            api(libs.cmp.ui)
            api(libs.androidx.paging.common)
            api(libs.coroutines.core)
            api(libs.kotlinx.collections.immutable)
            implementation(libs.androidx.compose.paging)
            implementation(libs.cmp.animation)
            implementation(libs.cmp.material.icons.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutine.test)
            // D5: the domain suite's in-file ExerciseRepository fake names PlanSetDataModel, which
            // core:data:exercise keeps implementation-scoped.
            implementation(project(":core:data:database"))
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
