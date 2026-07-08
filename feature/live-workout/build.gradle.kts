plugins {
    alias(libs.plugins.convention.composeLibrary)
    // KMP C.1 (wave 3): feature/live-workout flipped Hilt→Metro. ASSISTED, largest feature,
    // 13 bound instances, single @DefaultDispatcher.
    alias(libs.plugins.metro)
}

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

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
