plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:exercise"))

    implementation(libs.dev.haze.core)
    implementation(libs.dev.haze.materials)

    implementation(libs.androidx.compose.text.google.fonts)
}
