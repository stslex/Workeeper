plugins {
    alias(libs.plugins.convention.composeLibrary)
    // The largest feature graph — it inherits the most app-scoped bindings, including two qualified
    // dispatchers and the application Context.
    alias(libs.plugins.metro)
    // Goldens for the settings surface (extraction Part 5). The harness is NOT copied: it
    // comes from core:ui:kit's testFixtures, so device config, tolerance and canvas width
    // cannot drift between modules.
    alias(libs.plugins.paparazzi)
}

// Metro reads javax.inject qualifiers so the inherited app-scoped bindings keep them — settings resolves
// @DefaultDispatcher AND @IODispatcher (both CoroutineDispatcher): (type + qualifier) is the Metro
// binding key, so the two resolve distinctly, never merge.
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
    testImplementation(testFixtures(project(":core:ui:kit")))
    // Compose's semantics-tree surface on the JVM side, for SettingsStartCardModeSheetTest:
    // the mode sheet is a window, so Paparazzi cannot photograph it through SettingsScreen,
    // and `src/androidTest` is dispatch-only and therefore not a gate. Robolectric and the
    // Jupiter RobolectricExtension already come from the convention plugin; this line adds
    // the missing `runComposeUiTest`. Same reasoning as core:ui:kit's copy of it.
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
