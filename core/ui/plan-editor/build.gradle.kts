plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:database"))
    implementation(project(":core:exercise"))

    implementation(libs.dev.haze.core)
    implementation(libs.dev.haze.materials)

    implementation(libs.androidx.compose.text.google.fonts)
}
