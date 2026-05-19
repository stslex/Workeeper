plugins {
    alias(libs.plugins.convention.composeLibrary)
}

android {
    defaultConfig {
        testInstrumentationRunner = "io.github.stslex.workeeper.core.ui.test.runner.WorkeeperTestRunner"
    }
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":feature:app-dialogs:api"))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.bundles.room)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(project(":core:ui:test-utils"))
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
