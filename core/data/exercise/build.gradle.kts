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
    testImplementation(testFixtures(project(":core:data:database")))
}
