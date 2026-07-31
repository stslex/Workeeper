plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Non-collider, PLAIN Store (archive template), single @DefaultDispatcher (no collision).
    alias(libs.plugins.metro)
    // Goldens for the all-trainings surface. This module had none: the v3 rebuild is a whole-surface
    // change against a drawn contract, and a rebuild with no before-picture is a diff nobody can read.
    // The harness is NOT copied — it comes from core:ui:kit's testFixtures, so device config,
    // tolerance and canvas width cannot drift between modules.
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
    implementation(project(":core:data:exercise"))

    testImplementation(libs.androidx.paging.testing)
    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":core:ui:kit")))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
