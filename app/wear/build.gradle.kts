plugins {
    alias(libs.plugins.convention.application.wear)
}

android {
    lint {
        // Android Lint cannot merge its standalone JVM model with this app while app-only issues
        // are enabled: it reports CannotEnableHidden before source analysis. The pure-JVM
        // protocol instead owns an independent, Detekt-backed lintDebug alias in every root gate.
        checkDependencies = false
    }
}

dependencies {
    implementation(project(":core:wear-protocol"))
    implementation(libs.google.play.services.wearable)
    implementation(libs.coroutines.play.services)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.wear.tiles.testing)
    // `runComposeUiTest` under Robolectric hosts the redesign gates (touch targets, kind
    // distinction, disabled labels, overflow) in `testDebugUnitTest`, the per-PR gate — the
    // instrumented workflow is dispatch-only. Robolectric itself and ApplicationProvider come
    // from the shared `test` bundle; the ComponentActivity the test launches comes from the
    // existing `debugImplementation(ui-test-manifest)` below.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric.junit5.extension)
    // Only the repository-owned suite annotations are needed. Pulling the module's phone/KMP UI
    // runtime into the Wear test APK conflicts with the intentionally narrower Wear lock graph.
    androidTestImplementation(project(":core:ui:test-utils")) {
        isTransitive = false
    }
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
    // GUARD: the robolectric-junit5 bridge needs launcher interceptors on, or every test dies
    // with "No instrumentation registered". See feature-specs/kmp-phase-3-core-collapse.md.
    systemProperty("junit.platform.launcher.interceptors.enabled", true)
}

// The root verification contract uses unflavoured lifecycle names. AGP creates those aliases for
// assembleDebug, but not for the remaining tasks once this application adds a flavor dimension.
// Keep both package-compatible variants in every existing root gate.
tasks.register("assembleDebugAndroidTest") {
    dependsOn("assembleDevDebugAndroidTest", "assembleStoreDebugAndroidTest")
}
tasks.register("lintDebug") {
    dependsOn("lintDevDebug", "lintStoreDebug")
}
tasks.register("testDebugUnitTest") {
    dependsOn("testDevDebugUnitTest", "testStoreDebugUnitTest")
}
