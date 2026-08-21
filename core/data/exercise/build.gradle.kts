plugins {
    alias(libs.plugins.convention.kmpLibrary)
    // The exercise repositories are Metro-owned via @ContributesBinding(AppScope); their Room-DAO deps
    // resolve from the app graph's DbCascadeBindingContainer (core:data:database).
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// Every production file is commonMain; this module intentionally has no androidMain source set.
// Platform-driver selection stays in core:data:database. See kmp-phase-6-data-layer.md → §10
// "core:data:exercise is the repo's first zero-androidMain KMP module."
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))
            implementation(project(":core:data:database"))
            implementation(libs.androidx.paging.common)
            // The duplicate-name catch names androidx.sqlite.SQLiteException; the database
            // module's room/sqlite deps are `implementation` and do not leak here.
            implementation(libs.androidx.sqlite)
        }
    }
}

dependencies {
    // Robolectric under JUnit 5 for androidHostTest — the KMP convention keeps Robolectric a
    // module concern (the core:core / core:data:dataStore / core:data:database shape). 16 of the
    // 19 host-test classes run the real schema through RepositoryTestEnv.
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
    // Supplies io.github.stslex.workeeper.core.ui.test.annotations.Regression — the ui_tests.yml
    // runner filter; an un-annotated device test can never be selected by any CI job.
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

// The robolectric-junit5 bridge sandboxes each test class through a JUnit Platform
// LauncherInterceptor, and interceptors are OFF by default — without this property the extension
// runs but never installs Robolectric's classloader, and every test dies with "No instrumentation
// registered". The KMP convention keeps Robolectric a module concern, so the module sets it.
tasks.withType<Test>().configureEach {
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}
