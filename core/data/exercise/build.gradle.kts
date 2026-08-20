plugins {
    alias(libs.plugins.convention.androidLibrary)
    // The exercise repositories are Metro-owned via @ContributesBinding(AppScope); their Room-DAO deps
    // resolve from the app graph's DbCascadeBindingContainer (core:data:database).
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:data:database"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)

    implementation(libs.androidx.paging.runtime)
    // room3 folded the old room-ktx (coroutine/Flow support) into room3-runtime.
    implementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(project(":core:data:database-test"))

    androidTestImplementation(libs.bundles.android.test)
    // InMemoryDatabaseProvider — the on-device AppDatabase under the production (bundled) driver.
    androidTestImplementation(project(":core:data:database-test"))
    // Supplies io.github.stslex.workeeper.core.ui.test.annotations.Regression — the ui_tests.yml
    // runner filter; an un-annotated device test can never be selected by any CI job.
    androidTestImplementation(project(":core:ui:test-utils"))
}
