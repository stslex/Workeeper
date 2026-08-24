plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Collider — two same-typed dispatchers + Context; the route arg is a bound instance (shape B).
    alias(libs.plugins.metro)
    // Goldens for the exercise-detail surface; the harness comes from core:ui:golden-harness.
    alias(libs.plugins.paparazzi)
}

// includeJavax keeps @Default and @MainImmediate distinct as binding keys.
metro {
    interop {
        includeJavax()
    }
}

// androidTest here is screen-render only, so the module uses the convention default runner.

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
    testImplementation(project(":core:ui:golden-harness"))
    // The deferred-delete witness (S7): only a real in-memory Room DB shows the row survive.
    testImplementation(project(":core:data:database-test"))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
