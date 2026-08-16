plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Collider — @DefaultDispatcher + @MainImmediateDispatcher (both CoroutineDispatcher), NO Context.
    // The Screen.Training route arg enters as a @Provides bound instance on the extension factory
    // (shape B), so the graph's root accessor is the Store itself and there is no assisted machinery.
    alias(libs.plugins.metro)
    // The training editor's only visual gate. What it holds is one-frame-static and therefore
    // exactly what a golden covers (§27): the drawn `.addex` as the add action, ONE drag handle
    // rather than a pair of arrows, and the kit's stroke `✕` on the row. The harness is NOT
    // copied — it comes from core:ui:golden-harness, so device config, tolerance and canvas
    // width cannot drift between modules.
    alias(libs.plugins.paparazzi)
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

    testImplementation(kotlin("test"))
    testImplementation(project(":core:ui:golden-harness"))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
