// Test-scaffolding module: the in-memory AppDatabase provider and the repository fixtures.
//
// The fixtures live in src/main, not a testFixtures source set, and must stay that way: KMP has no
// testFixtures source set, and both host- and device-test consumers need this normal module. See
// documentation/feature-specs/kmp-phase-6-data-layer.md -> §3.1.
//
// Dependencies are `api`, not `implementation`: consumers construct RepositoryTestEnv and touch the
// Room types it returns, so those types must reach their compile classpath.

plugins {
    alias(libs.plugins.convention.androidLibrary)
}

dependencies {
    api(project(":core:core"))
    api(project(":core:data:database"))

    api(libs.bundles.room)
    // The two fixtures deliberately run DIFFERENT drivers. RepositoryTestEnv (Robolectric
    // repository unit tests) pins AndroidSQLiteDriver: the bundled driver's android variant
    // ships Android-ABI natives only and dies with UnsatisfiedLinkError on a desktop JVM
    // (measured). InMemoryDatabaseProvider (on-device androidTest via MetroTestRule) runs
    // BundledSQLiteDriver — the production driver since the flip.
    api(libs.androidx.sqlite.framework)
    api(libs.androidx.sqlite.bundled)
    api(libs.androidx.test)
    // RepositoryTestEnv runs suspending seeds and exposes a CoroutineScope to its callers.
    api(libs.coroutines)
}
