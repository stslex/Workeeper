plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(project(":core:core"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:exercise"))

    implementation(libs.kotlinx.datetime)
}