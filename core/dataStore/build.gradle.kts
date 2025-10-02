plugins {
    alias(libs.plugins.convention.androidLibrary)
}

dependencies {
    implementation(project(":core:core"))

    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences)
}