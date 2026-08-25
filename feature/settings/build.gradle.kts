plugins {
    alias(libs.plugins.convention.composeLibrary)
    // The largest feature graph — it inherits the most app-scoped bindings.
    alias(libs.plugins.metro)
    // Goldens for the settings surface; the harness comes from core:ui:golden-harness.
    alias(libs.plugins.paparazzi)
}

// includeJavax keeps @DefaultDispatcher and @IODispatcher distinct as binding keys.
metro {
    interop {
        includeJavax()
    }
}

dependencies {
    implementation(project(":core:core"))

    implementation(project(":core:data:dataStore"))
    implementation(project(":core:ui:kit"))
    // HS5: the Home start card's mode sheet — one sheet, two entry points.
    implementation(project(":core:ui:start-mode"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:data:exercise"))
    implementation(project(":core:data:backup:api"))
    implementation(project(":core:data:database"))
    implementation(project(":feature:app-dialogs:api"))

    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.paging.testing)
    testImplementation(project(":core:ui:golden-harness"))
    // `runComposeUiTest` for SettingsStartCardModeSheetTest — the sheet is a window, not a golden.
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
