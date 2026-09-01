plugins {
    alias(libs.plugins.convention.application.wear)
}

dependencies {
    implementation(project(":core:wear-protocol"))
    implementation(libs.google.play.services.wearable)
    implementation(libs.coroutines.play.services)

    testImplementation(libs.androidx.wear.tiles.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
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
