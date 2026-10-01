plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // PLAIN Store, single @DefaultDispatcher; the template every other feature graph follows.
    alias(libs.plugins.metro)
    // Goldens for the archive surface; the harness comes from core:ui:golden-harness.
    alias(libs.plugins.paparazzi)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.archive.resources"
}

// includeJavax keeps the inherited qualified dispatcher bindings from silently merging.
metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))

            // `api`: ArchiveStore.State carries the kit's PagingUiState, ArchiveStore and
            // ArchiveHandlerStore extend the MVI contracts, and archiveGraph extends NavGraphScope.
            api(project(":core:ui:kit"))
            api(project(":core:ui:mvi"))
            api(project(":core:ui:navigation"))
            implementation(project(":core:data:exercise"))

            // `api`: archiveGraph takes a Modifier and Event.Haptic carries a HapticFeedbackType;
            // ArchiveInteractor and ArchiveStore.State expose Flow and PagingData.
            api(libs.cmp.ui)
            api(libs.androidx.paging.common)
            api(libs.coroutines.core)
            implementation(libs.androidx.compose.paging)
            implementation(libs.cmp.material.icons.extended)
            implementation(libs.kotlinx.collections.immutable)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
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
