plugins {
    alias(libs.plugins.convention.androidLibrary)
    // App-Scope Collapse Step 3 (C2): the 9 exercise repositories flipped Hilt→Metro
    // (@ContributesBinding(AppScope)). Coexists with the module's remaining Hilt (none — the 3 @Binds
    // modules were deleted; the repos' Room-DAO deps are bridge-read into the app graph).
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))
    // Android-only core:core-android for AppScope (the app-graph marker the repo impls contribute against).
    implementation(project(":core:core-android"))
    implementation(project(":core:data:database"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.room.ktx)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(testFixtures(project(":core:data:database")))
}
