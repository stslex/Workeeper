plugins {
    alias(libs.plugins.convention.composeLibrary)
    // KMP C.1 (wave 4): feature/app-dialogs:impl flipped Hilt→Metro. AppFeature (root-mounted,
    // Activity-scoped) PLAIN Store; the @ApplicationContext Context lives on the Hilt side (the
    // bridged @Singleton AppDialogRepository) — never enters the Metro graph.
    alias(libs.plugins.metro)
}

// includeJavax kept for batch consistency (no qualified dep here — no dispatcher, no javax qualifier
// in the Metro graph; every Metro feature carries the same interop line).
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
    implementation(project(":feature:app-dialogs:api"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)

    implementation(libs.hilt.navigation.compose)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
