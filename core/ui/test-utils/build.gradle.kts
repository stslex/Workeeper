plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    // FakeImageStorage implements core:core's ImageStorage/ImageRef/ImageSaveResult; the real
    // ImageStorageImpl (core:core-android) is never referenced here.
    implementation(projects.core.core)
    implementation(projects.core.ui.kit)
    implementation(projects.core.ui.mvi)
    implementation(projects.core.ui.navigation)

    // TestSingleScreenHost mounts a real NavDisplay so instrumented scaffolding tests can host a
    // feature graph WITHOUT importing androidx.navigation3 themselves — the androidTest
    // navigation-import gate stays clean and exclusion-free.
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Compose Testing
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.junit)
    api(libs.androidx.test.runner)
    api(libs.androidx.compose.activity)

    // Coroutines Testing
    api(libs.coroutine.test)

    // Paging Testing
    api(libs.androidx.paging.testing)

    // Immutable Collections
    implementation(libs.kotlinx.collections.immutable)

    // Compose runtime for @Composable annotations
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.animation)
}
