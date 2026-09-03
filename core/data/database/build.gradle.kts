plugins {
    // GUARD: kmpLibrary MUST precede roomLibrary — the Room convention branches on the base
    // convention already applied to pick its configuration names.
    alias(libs.plugins.convention.kmpLibrary)
    alias(libs.plugins.convention.roomLibrary)
    alias(libs.plugins.serialization)
    // The DB-cascade bindings (DAOs + DbTransitionRunner) are Metro-owned, deriving from the
    // AppDatabase create() root.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// The Room surface (entities, DAOs, converters, migrations, export) is commonMain; only
// platform-typed code stays in androidMain — buildAppDatabase and the snapshot/ package.
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
    // module concern, so the pieces are declared here and the interceptor property is set below.
    "androidHostTestImplementation"(libs.robolectric)
    "androidHostTestImplementation"(libs.robolectric.junit5.extension)
    "androidHostTestImplementation"(libs.androidx.test)
    // GUARD: host tests stay on AndroidSQLiteDriver — the bundled driver's android variant is
    // ABI-native only and dies with UnsatisfiedLinkError under Robolectric on a desktop JVM.
    "androidHostTestImplementation"(libs.androidx.sqlite.framework)
    // MigrationsRegistryTest introspects the registry against room-testing's Migration surface.
    "androidHostTestImplementation"(libs.androidx.room.testing)

    "androidDeviceTestImplementation"(libs.bundles.android.test)
    // runTest for the suspend Room 3 MigrationTestHelper API (createDatabase / runMigrationsAndValidate).
    "androidDeviceTestImplementation"(libs.coroutine.test)
    // Supplies the @Regression annotation: both connectedAndroidTest jobs select tests by the
    // runner's `annotation` argument, so an un-annotated device test here is never picked up.
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}

// The robolectric-junit5 bridge sandboxes each test class through a JUnit Platform
// LauncherInterceptor, and interceptors are OFF by default; the KMP convention leaves this to
// the module.
tasks.withType<Test>().configureEach {
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}
