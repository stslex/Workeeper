plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(project(":core:core"))
    // TempFileProvider (java.io.File-typed) lives in the Android half of core:core.
    implementation(project(":core:core-android"))

    implementation(project(":core:data:dataStore"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:data:exercise"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":core:data:database"))
    implementation(project(":feature:app-dialogs:api"))

    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.paging.testing)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
