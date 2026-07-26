plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Metro plugin so the app-scoped impls in this module (e.g. NumUiUtilsImpl) are contributed to the
    // app-scope AppGraph via @ContributesBinding(AppScope) — visibility-respecting ownership (the
    // internal impls stay internal; app/app never references them). includeJavax keeps any javax.inject
    // qualifier readable, matching the app/app + feature-module Metro config.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

dependencies {
    // Supplies the AppScope DI token (commonMain `di` package) for @ContributesBinding(AppScope).
    implementation(project(":core:core"))

    implementation(libs.dev.haze.core)
    implementation(libs.dev.haze.materials)

    implementation(libs.androidx.compose.text.google.fonts)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
