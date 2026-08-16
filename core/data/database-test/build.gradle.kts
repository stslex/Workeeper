// The DB test-scaffolding module: the in-memory AppDatabase provider AND the repository fixtures
// that used to live in core:data:database's `testFixtures` source set.
//
// They moved here because testFixtures DOES NOT EXIST for a KMP module (measured in the phase-2
// probes), and core:data:database is about to convert. core:data:database was the repo's only
// testFixtures producer, and 192 @Test across 3 modules / 19 files hang off it — so the fixtures had
// to find a real module before the plugin swap, not during it. This module already existed for
// exactly this purpose, so no fourth module was created.
//
// The fixtures KEEP their Kotlin package (io.github.stslex.workeeper.core.data.database.testfixtures)
// even though this module's Gradle namespace is ...database_test. Kotlin package is not Gradle
// namespace — the same move-mechanic phase 4 used to relocate the composition root — so every
// consumer's import line is unchanged and this commit is a build-graph edit, not a source rewrite.
//
// Dependencies are `api`, not `implementation`: consumers construct RepositoryTestEnv and touch the
// Room types it returns, so those types must be on their compile classpath.

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
