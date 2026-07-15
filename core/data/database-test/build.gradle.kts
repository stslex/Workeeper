plugins {
    alias(libs.plugins.convention.androidLibrary)
}

dependencies {
    implementation(project(":core:core"))
    api(project(":core:data:database"))

    api(libs.bundles.room)
    api(libs.androidx.test)
}
