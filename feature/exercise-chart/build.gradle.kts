plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // Route-arg feature (shape B — the arg is a @Provides bound instance on the extension factory, not
    // an @Assisted param), single @DefaultDispatcher.
    alias(libs.plugins.metro)
    // Goldens for the chart surface (extraction Part 4). The harness is NOT copied: it
    // comes from core:ui:golden-harness, so device config, tolerance and canvas width
    // cannot drift between modules.
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.exercise_chart.resources"
}

metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))

            // The kit's types appear only in internal code and internal constructors.
            implementation(project(":core:ui:kit"))
            // `api`: ExerciseChartStore, its handler store and Store impl and the public
            // CommonHandler extend the MVI contracts; exerciseChartGraph extends NavGraphScope and
            // the graph's Factory takes a Screen.ExerciseChart; the State carries the picker item's
            // ExerciseTypeUiModel.
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            api(project(":core:ui:plan-editor"))
            implementation(project(":core:data:exercise"))

            // `api`: exerciseChartGraph takes a Modifier and Event.HapticClick carries a
            // HapticFeedbackType; the graph exposes its CoroutineDispatcher; State and the UI
            // models carry ImmutableList / ImmutableMap and kotlinx-datetime's LocalDate (S1).
            api(libs.cmp.ui)
            api(libs.coroutines.core)
            api(libs.kotlinx.collections.immutable)
            // kotlinx-datetime is `implementation` in core:core, so it does not arrive transitively.
            api(libs.kotlinx.datetime)
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
    "androidHostTestImplementation"(project(":core:ui:golden-harness"))
    // For PrRuleFixture only — the shared, DB-free description of which set holds a record.
    // ChartFolder's day-winner is one of the five sites held to it; see
    // ChartFolderPrRuleParityTest and core/data/exercise's PrRuleParityTest.
    "androidHostTestImplementation"(project(":core:data:database-test"))

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
