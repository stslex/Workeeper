plugins {
    alias(libs.plugins.convention.composeLibrary)
    // KMP C.1 (wave 4): feature/app-dialogs:impl flipped Hilt→Metro. AppFeature (root-mounted,
    // Activity-scoped) PLAIN Store. App-Scope Collapse Step 3 additionally moved the three app-scoped
    // singletons (AppDialogRepository / AppDialogPublisherImpl / AppDialogObserverImpl) onto the app
    // graph; the repo's Context now resolves from the app graph's create(applicationContext) instance.
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
    implementation(project(":core:di"))
    // Android-only core:core-android for AppScope (the app-graph marker) — the app-scoped impls flipped
    // to Metro in App-Scope Collapse Step 3 contribute/scope against it. Not the KMP core:core (iOS).
    implementation(project(":core:core-android"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":feature:app-dialogs:api"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)


    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
