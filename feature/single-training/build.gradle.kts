plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Collider — @DefaultDispatcher + @MainImmediateDispatcher (both CoroutineDispatcher), NO Context.
    // The Screen.Training route arg enters as a @Provides bound instance on the extension factory
    // (shape B), so the graph's root accessor is the Store itself and there is no assisted machinery.
    alias(libs.plugins.metro)
}

// Metro reads javax.inject qualifiers so the two same-typed dispatchers keep their qualifiers:
// (type + qualifier) is the Metro binding key → @Default and @MainImmediate resolve distinctly.
metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:plan-editor"))
    implementation(project(":core:data:database"))
    implementation(project(":core:data:exercise"))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}