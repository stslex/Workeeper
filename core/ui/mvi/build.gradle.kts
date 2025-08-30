plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:kit"))
    implementation(libs.decompose)
}