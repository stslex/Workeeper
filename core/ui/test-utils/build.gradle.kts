plugins {
    alias(libs.plugins.convention.composeLibrary)
}

dependencies {
    // FakeImageStorage implements core:core's ImageStorage; the real impl is never referenced here.
    implementation(projects.core.core)
    implementation(projects.core.ui.kit)
    implementation(projects.core.ui.mvi)
    implementation(projects.core.ui.navigation)

    // TestSingleScreenHost mounts a real NavDisplay so scaffolding tests never import navigation3
    // themselves and the androidTest import gate stays exclusion-free.
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.junit)
    api(libs.androidx.test.runner)
    api(libs.androidx.compose.activity)

    api(libs.coroutine.test)

    api(libs.androidx.paging.testing)

    implementation(libs.kotlinx.collections.immutable)

    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.animation)
}
