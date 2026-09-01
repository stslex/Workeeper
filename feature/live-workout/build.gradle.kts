plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Largest feature. Route-arg feature (shape B), single @DefaultDispatcher.
    alias(libs.plugins.metro)
    // Goldens for the session's set and exercise states; harness from core:ui:golden-harness.
    alias(libs.plugins.paparazzi)
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
    testImplementation(project(":core:data:database-test"))
    testImplementation(project(":core:ui:golden-harness"))
    // `runComposeUiTest` for LiveSetRowSemanticsTest — a semantics property no golden can show.
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
