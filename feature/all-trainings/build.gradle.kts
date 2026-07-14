plugins {
    alias(libs.plugins.convention.composeLibrary)
    // KMP C.1 (wave 3): feature/all-trainings flipped Hilt→Metro. Non-collider, PLAIN Store
    // (archive template), single @DefaultDispatcher (no collision).
    alias(libs.plugins.metro)
}

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
    implementation(project(":core:data:exercise"))

    testImplementation(libs.androidx.paging.testing)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}