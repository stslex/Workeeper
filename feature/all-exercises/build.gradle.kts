plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Non-collider, PLAIN Store (archive template), single @DefaultDispatcher (no collision).
    alias(libs.plugins.metro)
    // Goldens for the all-exercises surface. This module had none, and the v3 rebuild rewrites the
    // row, the list, the FAB, the empty state and the tag band at once — a whole-surface change with
    // no before-picture is a diff nobody can read. The harness is NOT copied: it comes from
    // core:ui:golden-harness, so device config, tolerance and canvas width cannot drift between
    // this module and the sibling it must stay in step with.
    alias(libs.plugins.paparazzi)
}

// Metro reads javax.inject qualifiers so the inherited @DefaultDispatcher keeps its qualifier.
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

    testImplementation(libs.androidx.paging.testing)
    testImplementation(kotlin("test"))
    testImplementation(project(":core:ui:golden-harness"))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
