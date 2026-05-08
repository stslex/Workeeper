// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.testutil

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.test.fakes.FakeNavigator
import javax.inject.Singleton

/**
 * Per-feature test bindings for `feature/exercise` androidTest.
 *
 * `Navigator` is bound by `app/app/.../NavigationModule` in production, which isn't on
 * the feature-test classpath. We supply `FakeNavigator` here so handlers that inject
 * `Navigator` (e.g. `NavigationHandler`) resolve at the test singleton scope. The same
 * stub lives in `core/ui/test-utils` so other feature androidTest modules can reuse it
 * with their own one-line `@Binds`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureExerciseTestModule {

    @Binds
    @Singleton
    abstract fun bindNavigator(fake: FakeNavigator): Navigator
}
