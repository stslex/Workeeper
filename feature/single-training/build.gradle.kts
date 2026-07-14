plugins {
    alias(libs.plugins.convention.composeLibrary)
    // KMP C.1 (wave 1): feature/single-training flipped from Hilt to Metro DI. Collider —
    // @DefaultDispatcher + @MainImmediateDispatcher (both CoroutineDispatcher), NO Context.
    // Assisted Store (Screen.Training route arg) → the graph exposes the assisted Factory.
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
    implementation(project(":core:di"))

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