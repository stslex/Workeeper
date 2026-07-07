plugins {
    alias(libs.plugins.convention.composeLibrary)
    // KMP C.1 M0: feature/archive is the first feature flipped from Hilt to Metro DI.
    // The convention still force-applies the Hilt plugin (KotlinAndroid.kt) — it now
    // only processes archive's Hilt @EntryPoint bridge; all archive DI is Metro. The
    // other 11 features + app stay on Hilt via the dual-path Store seam.
    alias(libs.plugins.metro)
}

dependencies {
    implementation(project(":core:core"))

    implementation(project(":core:ui:kit"))
    implementation(project(":core:ui:mvi"))
    implementation(project(":core:ui:navigation"))
    implementation(project(":core:data:exercise"))

    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.paging.testing)

    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
