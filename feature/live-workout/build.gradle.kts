plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Largest feature. Route-arg feature (shape B — the arg is a @Provides bound instance on the
    // extension factory, not an @Assisted param), single @DefaultDispatcher.
    alias(libs.plugins.metro)
    // Goldens for the session's set and exercise states. They live here rather than in
    // core:ui:kit because LiveSetRow and LiveExerciseCard are feature components and stay
    // that way — v3 step 5 explicitly defers unifying LiveSetRow with past-session's row.
    // The harness itself is NOT copied: it comes from core:ui:golden-harness, so device
    // config, tolerance and canvas width cannot drift between modules.
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
    testImplementation(project(":core:ui:golden-harness"))
    // Compose's semantics-tree surface on the JVM side, for LiveSetRowSemanticsTest: the
    // announced field name is a semantics property no golden can photograph and no handler
    // test can reach. Same reasoning as core:ui:kit, feature:home and feature:settings.
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
