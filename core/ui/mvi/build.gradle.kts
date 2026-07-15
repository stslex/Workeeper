plugins {
    alias(libs.plugins.convention.composeLibrary)
    // App-Scope Collapse Step 3 (SB1): Metro plugin so app-scoped @Singleton impls here (LoggerHolder,
    // StoreDispatchers) can be contributed to the app-scope AppGraph via @ContributesBinding(AppScope).
    // includeJavax: StoreDispatchers ctor-injects @DefaultDispatcher + @MainImmediateDispatcher (javax
    // qualifiers on CoroutineDispatcher colliders) — the qualifiers must survive across the graph.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// App-Scope Collapse Step 6 (Phase 3.4): androidTest de-Hilt'd — no custom Application needed (Stores
// resolve via the Metro path with directly-constructed deps), so the module uses the convention default
// `androidx.test.runner.AndroidJUnitRunner` (the deleted WorkeeperTestRunner booted HiltTestApplication).

dependencies {
    implementation(project(":core:core"))
    // App-Scope Collapse Step 3: the AppScope DI token (for @ContributesBinding(AppScope)) lives in the
    // Android-only core:core-android, not the KMP core:core (which compiles to iOS). Same `di` package.
    implementation(project(":core:core-android"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:kit"))

    // viewModel { } + viewModelFactory/initializer for the Metro-backed Store retention path
    // (rememberMetroStoreProcessor). The Hilt path uses hiltViewModel (transitive) already.
    implementation(libs.bundles.lifecycle)

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.perf)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}