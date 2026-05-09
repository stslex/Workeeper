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

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:plan-editor"))

    implementation(project(":core:data:database"))
    implementation(project(":core:data:exercise"))

    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    androidTestImplementation(project(":core:data:database-test"))
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
