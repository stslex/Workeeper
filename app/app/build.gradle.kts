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

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:database"))
    implementation(project(":core:exercise"))
    implementation(project(":core:dataStore"))

    implementation(project(":feature:exercise"))
    implementation(project(":feature:all-trainings"))
    implementation(project(":feature:charts"))
    implementation(project(":feature:all-exercises"))
    implementation(project(":feature:single-training"))

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)

    implementation(libs.hilt.navigation.compose)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}