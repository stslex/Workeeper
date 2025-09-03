plugins {
    alias(libs.plugins.convention.application)
    alias(libs.plugins.serialization)
}

dependencies {
    implementation(project(":core:core"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:database"))
    implementation(project(":core:exercise"))

    implementation(project(":feature:home"))

    implementation(project(":dialog:exercise"))
}