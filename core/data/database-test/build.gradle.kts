plugins {
    alias(libs.plugins.convention.androidLibrary)
}

dependencies {
    implementation(project(":core:core"))
    api(project(":core:data:database"))

    api(libs.bundles.room)
    implementation(libs.hilt.test)
    api(libs.androidx.test)
}
