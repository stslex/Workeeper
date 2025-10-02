plugins {
    alias(libs.plugins.convention.composeLibrary)
    alias(libs.plugins.convention.hilt)
}

dependencies {
    implementation(project(":core:core"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:exercise"))

    testImplementation(libs.androidx.paging.testing)
    implementation(libs.dev.haze.core)
}