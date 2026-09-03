plugins {
    alias(libs.plugins.convention.composeLibrary)
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:data:backup:api"))
    // AppDialogRepository mints app_dialogs_prefs through the memoizing DataStoreProvider.
    implementation(project(":core:data:dataStore"))
    implementation(project(":feature:app-dialogs:api"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)


    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Carries the @Smoke / @Regression annotations; without it androidx.test drops the filter.
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
