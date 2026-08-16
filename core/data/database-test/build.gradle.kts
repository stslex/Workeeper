// Test-scaffolding module: the in-memory AppDatabase provider and the repository fixtures.
//
// The fixtures live in src/main, not a testFixtures source set, and must stay that way: KMP has no
// testFixtures source set, and core:data:database is scheduled to convert. Moving them back would
// break 192 @Test across three modules the day that conversion lands. See
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
    // InMemoryDatabaseProvider builds a Room 3 DB and must setDriver(AndroidSQLiteDriver()).
    api(libs.androidx.sqlite.framework)
    api(libs.androidx.test)
    // RepositoryTestEnv runs suspending seeds and exposes a CoroutineScope to its callers.
    api(libs.coroutines)
}
