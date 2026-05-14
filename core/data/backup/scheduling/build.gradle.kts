plugins {
    alias(libs.plugins.convention.androidLibrary)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:data:backup:api"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)
}
