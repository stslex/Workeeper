plugins {
    alias(libs.plugins.convention.composeLibrary)
    alias(libs.plugins.convention.navigation)
}

dependencies {
    implementation(project(":core:core"))
}