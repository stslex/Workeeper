plugins {
    alias(libs.plugins.convention.composeLibrary)
    // AppFeature (root-mounted, Activity-scoped) PLAIN Store. The three app-scoped singletons
    // (AppDialogRepository / AppDialogPublisherImpl / AppDialogObserverImpl) live on the app graph;
    // the repo's Context resolves from the app graph's create(applicationContext) instance.
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
    // Supplies the AppScope DI token (commonMain `di` package) the app-scoped impls contribute against.
    implementation(project(":core:core"))
    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:data:backup:api"))
    // DataStoreProviderFactory: AppDialogRepository mints app_dialogs_prefs through the
    // process-lifetime memoizing DataStoreProvider instead of PreferenceDataStoreFactory, so a
    // second AppGraph in one process resolves the same store.
    implementation(project(":core:data:dataStore"))
    implementation(project(":feature:app-dialogs:api"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)


    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Carries the @Smoke / @Regression suite annotations. Without this edge androidx.test
    // cannot load the class named by ui_tests.yml's `-e annotation` filter and SILENTLY drops
    // the filter, running this module's whole suite in both the smoke and the regression run.
    // Enforced by `verifyInstrumentedSuiteClasspath`.
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
