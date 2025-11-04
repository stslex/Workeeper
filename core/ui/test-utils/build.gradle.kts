plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    implementation(projects.core.ui.kit)
    implementation(projects.core.ui.mvi)

    // Compose Testing
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.junit)
    api(libs.androidx.test.runner)

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
