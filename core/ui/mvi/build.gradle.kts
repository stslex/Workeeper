plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Contributes app-scoped impls here to AppGraph; includeJavax keeps the dispatcher qualifiers.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    // AppScope DI token + the dispatcher qualifiers StoreDispatchers contributes against.
    implementation(project(":core:core"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:kit"))

    // viewModel { } + viewModelFactory for the Metro-backed Store retention path.
    implementation(libs.bundles.lifecycle)

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.perf)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}