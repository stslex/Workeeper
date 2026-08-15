package io.github.stslex.workeeper

import AppExt.debugImplementation
import AppExt.implementation
import AppExt.implementationBundle
import AppExt.implementationPlatform
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project

/**
 * Configure Compose-specific options
 */
internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension,
) {
    commonExtension.apply {
        buildFeatures.compose = true

        implementationPlatform("androidx-compose-bom")
        debugImplementation("androidx-compose-tooling")
        // App-Scope Collapse Step 6 (Phase 5): `hilt-navigation-compose` removed — it was the sole path
        // by which `com.google.dagger:hilt-android` reached every compose module's classpath, and no code
        // uses `hiltViewModel()` anymore (the Metro `rememberMetroStoreProcessor` path replaced it). The
        // `viewModel<T>()` it provided arrives independently via `androidx.lifecycle:lifecycle-viewmodel-compose`,
        // already in the `lifecycle` bundle above.
        // accompanist (placeholder + systemuicontroller) and appcompat rode here for every
        // compose module with ZERO imports anywhere in app/core/feature — measured: 0 Kotlin
        // references to com.google.accompanist and androidx.appcompat, and the app theme's
        // own comment states it deliberately has no AppCompat ancestry. Dropped with their
        // catalog entries; goldens + full unit gate verify nothing depended on them.
        implementationBundle("compose", "lifecycle")
        implementation("material")
    }
}
