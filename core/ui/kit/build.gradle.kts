plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(project(":core:core"))

    implementation(libs.dev.haze.core)
    implementation(libs.dev.haze.materials)

    implementation(libs.androidx.compose.text.google.fonts)
}
