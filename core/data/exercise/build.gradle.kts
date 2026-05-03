plugins {
    alias(libs.plugins.convention.androidLibrary)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:data:database"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.room.ktx)
    testImplementation(libs.androidx.paging.testing)
}
