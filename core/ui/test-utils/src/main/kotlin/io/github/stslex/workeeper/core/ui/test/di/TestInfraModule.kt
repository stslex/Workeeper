// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.test.di

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.images.ImageStorageModule
import io.github.stslex.workeeper.core.ui.test.fakes.FakeImageStorage
import javax.inject.Singleton

/**
 * Test-side replacement for production fakes shared across feature integration tests.
 *
 * Per-feature overrides may `@TestInstallIn(replaces = [TestInfraModule::class])` to
 * inject test-specific behaviour; for now every feature inherits this default surface.
 *
 * Today this swaps `ImageStorage`. As more deterministic fakes are added (Clock,
 * SystemFeedback, ARC launchers — see documentation/test-scenarios/exercise.md "Test
 * infrastructure prerequisites"), they'll register here too.
 *
 * Bindings provided by app-level production modules (e.g. `Navigator` from
 * `app/app/.../NavigationModule`) are NOT replaced here, so app-scoped tests that
 * exercise the real graph (like `NavigationLifecycleRegressionTest`) continue to work.
 * Features whose handlers inject those types but run without `app/app` on the classpath
 * supply per-feature androidTest fakes — see
 * `feature/exercise/src/androidTest/.../testutil/FeatureExerciseTestModule.kt` for the
 * canonical example.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [ImageStorageModule::class],
)
abstract class TestInfraModule {

    @Binds
    @Singleton
    abstract fun bindImageStorage(fake: FakeImageStorage): ImageStorage
}
