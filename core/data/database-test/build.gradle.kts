// Test-scaffolding module: the in-memory AppDatabase provider and the repository fixtures.
// GUARD: fixtures stay in src/main — KMP has no testFixtures set (kmp-phase-6-data-layer.md §3.1).

plugins {
    alias(libs.plugins.convention.androidLibrary)
}

dependencies {
    api(project(":core:core"))
    api(project(":core:data:database"))

    api(libs.bundles.room)
    // The two fixtures deliberately run different drivers; see documentation/testing.md.
    api(libs.androidx.sqlite.framework)
    api(libs.androidx.sqlite.bundled)
    api(libs.androidx.test)
    // RepositoryTestEnv runs suspending seeds and exposes a CoroutineScope to its callers.
    api(libs.coroutines)
}
