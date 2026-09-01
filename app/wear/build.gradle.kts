plugins {
    alias(libs.plugins.convention.application.wear)
}

android {
    lint {
        // Android Lint cannot merge an application model with the required pure-JVM protocol
        // model while app-only issues are enabled: it reports CannotEnableHidden before source
        // analysis. The protocol owns an independent lintDebug alias in every root gate.
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
