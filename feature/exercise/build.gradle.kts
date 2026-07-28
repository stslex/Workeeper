plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Collider — @DefaultDispatcher + @MainImmediateDispatcher (both CoroutineDispatcher) + Context.
    // The Screen.Exercise route arg enters as a @Provides bound instance on the extension factory
    // (shape B), so the graph's root accessor is the Store itself and there is no assisted machinery.
    alias(libs.plugins.metro)
    // Goldens for the exercise-detail surface (extraction Part 3). The harness is NOT copied:
    // it comes from core:ui:kit's testFixtures, so device config, tolerance and canvas width
    // cannot drift between modules.
    alias(libs.plugins.paparazzi)
}

// Metro reads javax.inject qualifiers so the two same-typed dispatchers keep their qualifiers:
// (type + qualifier) is the Metro binding key → @Default and @MainImmediate resolve distinctly.
metro {
    interop {
        includeJavax()
    }
}

// App-Scope Collapse Step 6 (Phase 3.4): androidTest here is screen-render only (BaseComposeTest, no DI
// graph, no in-memory DB), so the module uses the convention default AndroidJUnitRunner.

dependencies {
    implementation(project(":core:core"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:ui:plan-editor"))

    implementation(project(":core:data:database"))
    implementation(project(":core:data:exercise"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":core:ui:kit")))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
