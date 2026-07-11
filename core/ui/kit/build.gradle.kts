plugins {
    alias(libs.plugins.convention.composeLibrary)
    // App-Scope Collapse Step 3 (SB1): Metro plugin so app-scoped @Singleton impls in this module can be
    // contributed to the app-scope AppGraph via @ContributesBinding(AppScope) — visibility-respecting
    // ownership (the internal impls stay internal; app/app never references them). includeJavax keeps any
    // javax.inject qualifier readable, matching the app/app + feature-module Metro config.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))
    // App-Scope Collapse Step 3: the AppScope DI token (for @ContributesBinding(AppScope)) lives in the
    // Android-only core:core-android, not the KMP core:core (which compiles to iOS). Same `di` package.
    implementation(project(":core:core-android"))

    implementation(libs.dev.haze.core)
    implementation(libs.dev.haze.materials)

    implementation(libs.androidx.compose.text.google.fonts)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
