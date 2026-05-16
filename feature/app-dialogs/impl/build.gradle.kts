plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":feature:app-dialogs:api"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)

    implementation(libs.hilt.navigation.compose)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
