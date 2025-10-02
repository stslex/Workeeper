plugins {
    alias(libs.plugins.convention.composeLibrary)
    alias(libs.plugins.convention.hilt)
}

dependencies {
    implementation(project(":core:core"))

    api(libs.androidx.compose.navigation)
}