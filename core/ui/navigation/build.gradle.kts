plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:ui:plan-editor"))

    api(libs.androidx.compose.navigation)
}