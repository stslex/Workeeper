plugins {
    alias(libs.plugins.convention.composeLibrary)
}

android {
    defaultConfig {
        testInstrumentationRunner =
            "io.github.stslex.workeeper.core.ui.test.runner.WorkeeperTestRunner"
    }
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:kit"))

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.perf)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}