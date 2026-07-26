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
