plugins {
    alias(libs.plugins.convention.application.dev)
}

android {
    defaultConfig {
        testInstrumentationRunner = "io.github.stslex.workeeper.dev.HiltTestRunner"
    }
}

dependencies {
    implementation(project(":app:app"))
    androidTestImplementation(project(":core:ui:test-utils"))

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}