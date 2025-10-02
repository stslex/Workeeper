plugins {
    alias(libs.plugins.convention.androidLibrary)
    alias(libs.plugins.convention.hilt)
}

dependencies {
    implementation(project(":core:core"))

    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences)
}