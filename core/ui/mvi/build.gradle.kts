plugins {
    alias(libs.plugins.convention.composeLibrary)
    alias(libs.plugins.convention.hilt)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:kit"))
}