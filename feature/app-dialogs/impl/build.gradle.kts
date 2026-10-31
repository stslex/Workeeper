plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // Screen-less, mounted at the app root (no route, no nav-host registration) and golden-free.
    alias(libs.plugins.metro)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.app_dialogs.impl.resources"
}

// Metro reads javax.inject qualifiers so the inherited app-scope qualifiers keep their keys.
metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // No public signature names its types: the graph's AppScope annotations reach the app
            // through the app's own core:core edge, and ResourceWrapper only the internal handler.
            implementation(project(":core:core"))
            // The dialog primitives and the theme; no public declaration names a kit type.
            implementation(project(":core:ui:kit"))
            // `api`: AppDialogStore, its handler store and its Store impl extend the MVI contracts.
            api(project(":core:ui:mvi"))
            // `api`: the repository implements AppDialogPublisher and State carries AppDialog.
            api(project(":feature:app-dialogs:api"))
            // `api`: the repository's @Inject constructor takes InstallEpochSource (D4) and a
            // DataStoreProviderFactory.
            api(project(":core:data:backup:api"))
            api(project(":core:data:dataStore"))
            // The repository's DataStore<Preferences>, through the common preferences API only.
            implementation(libs.androidx.datastore.core)
            implementation(libs.androidx.datastore.preferences.core)
            // `api`: currentDialog is a Flow and the observer returns a SharedFlow.
            api(libs.coroutines.core)
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
