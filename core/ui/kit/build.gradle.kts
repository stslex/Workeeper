plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(project(":core:core"))

    implementation(libs.androidx.compose.material)
    implementation(libs.dev.haze.core)
    implementation(libs.dev.haze.materials)
}
