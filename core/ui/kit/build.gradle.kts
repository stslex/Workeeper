plugins {
    alias(libs.plugins.convention.composeLibrary)
    alias(libs.plugins.convention.hilt)
}

dependencies {
    implementation(project(":core:core"))

    implementation(libs.androidx.compose.material)
    implementation(libs.dev.haze.core)
    implementation(libs.dev.haze.materials)
}