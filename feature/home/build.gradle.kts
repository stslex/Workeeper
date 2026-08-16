plugins {
    alias(libs.plugins.convention.composeLibrary)
    // PLAIN Store, single @DefaultDispatcher.
    alias(libs.plugins.metro)
    // Goldens for the Home surface. This module had none, and the v3 extraction rewrites the
    // recent-session row and the whole empty region at once — a surface change with no
    // before-picture is a diff nobody can read (§24, "Golden coverage gaps"). The harness is NOT
    // copied: it comes from core:ui:kit's testFixtures, so device config, tolerance and canvas
    // width cannot drift between this module and the three siblings it must stay in step with.
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
    // ObserveWeekReadoutUseCase takes a kotlinx-datetime TimeZone so tests can pin one;
    // core:core keeps the dependency `implementation`, so it does not arrive transitively.
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.androidx.paging.testing)
    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":core:ui:kit")))
    // Compose's semantics-tree surface on the JVM side, for HomeStartCardModeLabelTest: the
    // head's label is present-or-absent, which is a semantics claim a golden cannot make
    // (it would be a picture of an absence) and a handler test cannot reach. `src/androidTest`
    // is dispatch-only and therefore not a gate. Robolectric and the Jupiter
    // RobolectricExtension already arrive from the convention plugin; this is the missing
    // `runComposeUiTest`. Same reasoning as core:ui:kit and feature:settings.
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
