plugins {
    alias(libs.plugins.convention.androidLibrary)
    alias(libs.plugins.convention.roomLibrary)
    alias(libs.plugins.serialization)
}

dependencies {
    implementation(project(":core:core"))

    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.androidx.room.testing)
}