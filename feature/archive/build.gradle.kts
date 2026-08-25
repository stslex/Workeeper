plugins {
    alias(libs.plugins.convention.composeLibrary)
    // PLAIN Store, single @DefaultDispatcher; the template every other feature graph follows.
    alias(libs.plugins.metro)
    // Goldens for the archive surface; the harness comes from core:ui:golden-harness.
    alias(libs.plugins.paparazzi)
}

// includeJavax keeps the inherited qualified dispatcher bindings from silently merging.
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
    implementation(project(":core:data:exercise"))

    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.paging.testing)
    testImplementation(project(":core:ui:golden-harness"))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
