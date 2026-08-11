plugins {
    alias(libs.plugins.convention.composeLibrary)
    // Route-arg feature (shape B — the arg is a @Provides bound instance on the extension factory, not
    // an @Assisted param), single @DefaultDispatcher.
    alias(libs.plugins.metro)
    // Goldens for the chart surface (extraction Part 4). The harness is NOT copied: it
    // comes from core:ui:kit's testFixtures, so device config, tolerance and canvas width
    // cannot drift between modules.
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
    implementation(project(":core:data:exercise"))

    testImplementation(kotlin("test"))
    // For PrRuleFixture only — the shared, DB-free description of which set holds a record.
    // ChartFolder's day-winner is one of the five sites held to it; see
    // ChartFolderPrRuleParityTest and core/data/exercise's PrRuleParityTest.
    testImplementation(testFixtures(project(":core:data:database")))
    testImplementation(testFixtures(project(":core:ui:kit")))

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

apply(from = "$rootDir/gradle/golden-gate.gradle.kts")
