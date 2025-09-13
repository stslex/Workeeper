plugins {
    alias(libs.plugins.convention.androidLibrary)
    alias(libs.plugins.convention.roomLibrary)
}

dependencies {
    implementation(project(":core:core"))

    implementation(libs.kotlinx.serialization.json)
}