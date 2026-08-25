plugins {
    alias(libs.plugins.convention.kmpLibrary)
    // Metro-owned repositories; their Room-DAO deps resolve from the app graph.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// Production code is commonMain only; no androidMain source set. Platform-driver selection
// stays in core:data:database. See kmp-phase-6-data-layer.md.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))
            implementation(project(":core:data:database"))
            implementation(libs.androidx.paging.common)
            // Needed for the duplicate-name catch on androidx.sqlite.SQLiteException.
            implementation(libs.androidx.sqlite)
        }
    }
}

dependencies {
    // Robolectric under JUnit 5 for androidHostTest; the convention keeps it a module concern.
    "androidHostTestImplementation"(libs.robolectric)
    "androidHostTestImplementation"(libs.robolectric.junit5.extension)
    "androidHostTestImplementation"(libs.androidx.test)
    "androidHostTestImplementation"(libs.mockk.android)
    "androidHostTestImplementation"(libs.mockk.agent)
    "androidHostTestImplementation"(libs.androidx.paging.testing)
    "androidHostTestImplementation"(project(":core:data:database-test"))

    "androidDeviceTestImplementation"(libs.bundles.android.test)
    // InMemoryDatabaseProvider — the on-device AppDatabase under the production (bundled) driver.
    "androidDeviceTestImplementation"(project(":core:data:database-test"))
    // GUARD: supplies @Regression; an un-annotated device test is never selected by any CI job.
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

// The robolectric-junit5 bridge needs JUnit Platform interceptors on, or every test dies with
// "No instrumentation registered". See kmp-phase-6-data-layer.md.
tasks.withType<Test>().configureEach {
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}
