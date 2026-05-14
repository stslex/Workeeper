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
    implementation(project(":core:data:backup:api"))
    implementation(project(":core:data:backup:google-drive"))
    implementation(project(":core:data:backup:scheduling"))
    implementation(project(":core:data:backup:worker"))

    api(libs.androidx.work.runtime)
    api(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(project(":feature:exercise"))
    implementation(project(":feature:exercise-chart"))
    implementation(project(":feature:all-trainings"))
    implementation(project(":feature:all-exercises"))
    implementation(project(":feature:single-training"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:archive"))
    implementation(project(":feature:home"))
    implementation(project(":feature:live-workout"))
    implementation(project(":feature:past-session"))
    implementation(project(":feature:image-viewer"))
    implementation(project(":feature:plan-editor"))

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.crashlytics)
    implementation(libs.google.firebase.perf)

    implementation(libs.hilt.navigation.compose)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
