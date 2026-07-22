plugins {
    alias(libs.plugins.convention.composeLibrary)
    // KMP C.1 (wave 1): feature/exercise flipped from Hilt to Metro DI. Collider —
    // @DefaultDispatcher + @MainImmediateDispatcher (both CoroutineDispatcher) + @ApplicationContext.
    // Assisted Store (Screen.Exercise route arg) → the graph exposes the assisted Factory.
    alias(libs.plugins.metro)
}

// Metro reads javax.inject qualifiers so the two same-typed dispatchers keep their qualifiers:
// (type + qualifier) is the Metro binding key → @Default and @MainImmediate resolve distinctly.
metro {
    interop {
        includeJavax()
    }
}

// App-Scope Collapse Step 6 (Phase 3.4): androidTest de-Hilt'd (screen-render tests via BaseComposeTest,
// no Hilt graph / no in-memory DB), so the module uses the convention default AndroidJUnitRunner (the
// deleted WorkeeperTestRunner booted HiltTestApplication).

dependencies {
    implementation(project(":core:core"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:plan-editor"))

    implementation(project(":core:data:database"))
    implementation(project(":core:data:exercise"))

    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
