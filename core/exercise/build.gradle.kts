plugins {
    alias(libs.plugins.convention.androidLibrary)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:database"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)

    implementation(libs.androidx.paging.runtime)
    testImplementation(libs.androidx.paging.testing)

    implementation(libs.kotlinx.datetime)
}