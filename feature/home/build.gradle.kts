plugins {
    alias(libs.plugins.convention.composeLibrary)
    // PLAIN Store, single @DefaultDispatcher.
    alias(libs.plugins.metro)
    // Goldens for the Home surface; the harness comes from core:ui:golden-harness.
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
    // The start card's mode catalog + picker sheet, shared with feature:settings (HS5).
    implementation(project(":core:ui:start-mode"))
    implementation(project(":core:data:exercise"))
    // HS6: the start card's mode is a CommonDataStore preference beside themePreference.
    implementation(project(":core:data:dataStore"))
    // kotlinx-datetime is `implementation` in core:core, so it does not arrive transitively.
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.androidx.paging.testing)
    testImplementation(kotlin("test"))
    testImplementation(project(":core:ui:golden-harness"))
    // `runComposeUiTest` for HomeStartCardModeLabelTest — a semantics claim no golden can make.
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
