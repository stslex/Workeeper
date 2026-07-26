plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Non-collider, simplest graph: no dispatcher dep. The Screen.ExerciseImage route arg enters as a
    // @Provides bound instance on the extension factory (shape B), so there is no assisted machinery.
    alias(libs.plugins.metro)
}

// includeJavax kept for batch consistency (no qualified dep here, but every Metro module carries
// the same interop line — centralizable in a convention plugin at the iOS phase).
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

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
