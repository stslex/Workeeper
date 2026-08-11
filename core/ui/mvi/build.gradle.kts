plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Metro plugin so the app-scoped impls here (LoggerHolder, StoreDispatchers) are contributed to the
    // app-scope AppGraph via @ContributesBinding(AppScope). includeJavax: StoreDispatchers ctor-injects
    // @DefaultDispatcher + @MainImmediateDispatcher (javax qualifiers on CoroutineDispatcher colliders)
    // — the qualifiers must survive across the graph.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// App-Scope Collapse Step 6 (Phase 3.4): androidTest needs no custom Application (Stores resolve via the
// Metro path with directly-constructed deps), so the module uses the convention default
// `androidx.test.runner.AndroidJUnitRunner`.

dependencies {
    // Supplies the AppScope DI token and the @DefaultDispatcher / @MainImmediateDispatcher qualifiers
    // (commonMain `di` package) that StoreDispatchers contributes and ctor-injects against.
    implementation(project(":core:core"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:kit"))

    // viewModel { } + viewModelFactory/initializer for the Metro-backed Store retention path
    // (rememberMetroStoreProcessor) — the only Store retention path.
    implementation(libs.bundles.lifecycle)

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.perf)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}