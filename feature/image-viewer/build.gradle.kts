plugins {
    alias(libs.plugins.convention.composeLibrary)
    // KMP C.1 (wave 2 checkpoint): feature/image-viewer flipped Hilt→Metro. Non-collider,
    // assisted Store (Screen.ExerciseImage), simplest graph (4 bound instances, no dispatcher).
    alias(libs.plugins.metro)
}

// includeJavax kept for batch consistency (no qualified dep here, but every Metro feature carries
// the same interop line — centralizable in a convention plugin at the iOS phase).
metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))
    // P-BRIDGES: AppGraphContract seam for Hilt-free app-scope reads.
    implementation(project(":core:di"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
