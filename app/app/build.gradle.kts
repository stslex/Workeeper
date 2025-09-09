plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(project(":core:core"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:database"))
    implementation(project(":core:exercise"))
    implementation(project(":core:store"))

    implementation(project(":feature:home"))
    implementation(project(":feature:exercise"))


    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)
}