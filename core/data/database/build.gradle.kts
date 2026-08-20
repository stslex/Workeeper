plugins {
    // kmpLibrary MUST precede roomLibrary: the Room convention branches on which base
    // convention is already applied (see RoomLibraryConventionPlugin's KDoc).
    alias(libs.plugins.convention.kmpLibrary)
    alias(libs.plugins.convention.roomLibrary)
    alias(libs.plugins.serialization)
    // App-Scope Collapse Step 5 (5a): the DB-cascade bindings (9 DAOs + DbTransitionRunner via a
    // @BindingContainer, and the 3 DB-binding impls via @ContributesBinding) are Metro-owned, deriving
    // from the AppDatabase create() root. Mirrors the C2 exercise-repo module (core:data:exercise).
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// The Room surface (entities, DAOs, converters, migrations, export) is commonMain; only what is
// platform-typed stays in androidMain — buildAppDatabase (Context + AndroidSQLiteDriver) and the
// snapshot/ package (raw android.database.sqlite + java.io.File, deliberately outside Room).
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:core"))
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            // BackupResult/BackupError in DatabaseSnapshotProvider's signatures only.
            implementation(project(":core:data:backup:api"))
        }
    }
}

dependencies {
    // Robolectric under JUnit 5 for androidHostTest — the KMP convention keeps Robolectric a
    // module concern, so the three pieces are declared here and the interceptor property is set
    // below (the core:core / core:data:dataStore shape). 22 of the 26 host-test classes run the
    // production schema on Robolectric's SQLite.
    "androidHostTestImplementation"(libs.robolectric)
    "androidHostTestImplementation"(libs.robolectric.junit5.extension)
    "androidHostTestImplementation"(libs.androidx.test)
    // Host tests stay on AndroidSQLiteDriver: the bundled driver's android variant carries
    // Android-ABI natives only and dies with UnsatisfiedLinkError under Robolectric on a
    // desktop JVM (measured). Robolectric is not an admissible driver oracle anyway — the
    // device suite is where the production driver is exercised.
    "androidHostTestImplementation"(libs.androidx.sqlite.framework)
    // MigrationsRegistryTest introspects the registry against room-testing's Migration surface.
    "androidHostTestImplementation"(libs.androidx.room.testing)

    "androidDeviceTestImplementation"(libs.bundles.android.test)
    // runTest for the suspend Room 3 MigrationTestHelper API (createDatabase / runMigrationsAndValidate).
    "androidDeviceTestImplementation"(libs.coroutine.test)
    // Supplies io.github.stslex.workeeper.core.ui.test.annotations.Regression — both
    // connectedAndroidTest jobs in ui_tests.yml select tests via the runner's `annotation` argument,
    // so an un-annotated device test here can never be picked up by any CI job.
    // deviceTest-variant edge only; test-utils' main variant reaches back here transitively
    // (test-utils -> ui:navigation -> ui:plan-editor -> data:exercise -> data:database), which is the
    // same accepted shape as core:ui:mvi's androidTestImplementation(project(":core:ui:test-utils")).
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

// The robolectric-junit5 bridge sandboxes each test class through a JUnit Platform
// LauncherInterceptor, and interceptors are OFF by default — without this property the extension
// runs but never installs Robolectric's classloader, and every test dies with "No instrumentation
// registered". On classic Android modules the bridge's Gradle plugin (applied by the Android
// convention) sets this; the KMP convention keeps Robolectric a module concern, so the module sets it.
tasks.withType<Test>().configureEach {
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}
