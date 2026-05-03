plugins {
    alias(libs.plugins.convention.composeLibrary)
}

android {
    defaultConfig {
        testInstrumentationRunner = "io.github.stslex.workeeper.app.HiltTestRunner"
    }
}

dependencies {
    implementation(project(":core:core"))
    androidTestImplementation(project(":core:ui:test-utils"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:exercise"))
    implementation(project(":core:data:dataStore"))

    implementation(project(":feature:exercise"))
    implementation(project(":feature:exercise-chart"))
    implementation(project(":feature:all-trainings"))
    implementation(project(":feature:all-exercises"))
    implementation(project(":feature:single-training"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:home"))
    implementation(project(":feature:live-workout"))
    implementation(project(":feature:past-session"))
    implementation(project(":feature:image-viewer"))

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)

    implementation(libs.hilt.navigation.compose)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
