plugins {
    alias(libs.plugins.convention.androidLibrary)
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

android {
    testFixtures.enable = true
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:data:backup:api"))

    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.androidx.room.testing)

    testFixturesImplementation(project(":core:core"))
    testFixturesImplementation(libs.bundles.room)
    // RepositoryTestEnv builds a Room 3 DB and must setDriver(AndroidSQLiteDriver()).
    testFixturesImplementation(libs.androidx.sqlite.framework)
    testFixturesImplementation(libs.androidx.test)
    testFixturesImplementation(libs.coroutines)

    androidTestImplementation(libs.bundles.android.test)
    // runTest for the suspend Room 3 MigrationTestHelper API (createDatabase / runMigrationsAndValidate).
    androidTestImplementation(libs.coroutine.test)
    // Supplies io.github.stslex.workeeper.core.ui.test.annotations.Regression — both
    // connectedAndroidTest jobs in ui_tests.yml select tests via the runner's `annotation` argument,
    // so an un-annotated device test here can never be picked up by any CI job.
    // androidTest-variant edge only; test-utils' main variant reaches back here transitively
    // (test-utils -> ui:navigation -> ui:plan-editor -> data:exercise -> data:database), which is the
    // same accepted shape as core:ui:mvi's androidTestImplementation(project(":core:ui:test-utils")).
    androidTestImplementation(project(":core:ui:test-utils"))
}