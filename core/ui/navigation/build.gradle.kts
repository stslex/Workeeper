plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(project(":core:core"))

    api(libs.androidx.compose.navigation)
}