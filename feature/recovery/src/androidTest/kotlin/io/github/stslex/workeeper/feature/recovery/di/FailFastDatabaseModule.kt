// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.di.CoreDatabaseModule
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.data.database.tag.TagDao
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.data.database.training.TrainingDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import io.mockk.every
import io.mockk.mockk
import javax.inject.Singleton

/**
 * Replaces the production [CoreDatabaseModule] with a tripwire [AppDatabase]
 * that throws on any `openHelper` access. Used by [RecoveryActivityDbFreeTest]
 * to verify the DB-free invariant: `RecoveryActivity` and its injected
 * collaborators may hold an `AppDatabase` reference (Room constructs it
 * lazily; just holding the reference is harmless), but no code path inside
 * the activity's lifecycle may call a method that forces
 * `openHelper.{writable,readable}Database` — which would trigger Room
 * migration on the very data this activity exists to recover.
 *
 * If a future contributor wires a Room-touching collaborator into the
 * activity, the first `openHelper` access throws and the test fails
 * immediately. The DAO providers below are also relaxed mocks so the Hilt
 * graph stays resolvable, but the activity should never reach them.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CoreDatabaseModule::class],
)
internal object FailFastDatabaseModule {

    private const val TRIPWIRE_MESSAGE =
        "DB-free invariant violated: RecoveryActivity must not trigger Room init " +
            "(no openHelper access permitted during the activity's lifecycle)"

    @Provides
    @Singleton
    internal fun provideAppDatabase(): AppDatabase {
        val mock = mockk<AppDatabase>(relaxed = true)
        every { mock.openHelper } throws AssertionError(TRIPWIRE_MESSAGE)
        return mock
    }

    @Provides
    @Singleton
    internal fun provideTransition(): DbTransitionRunner = mockk(relaxed = true)

    @Provides
    @Singleton
    internal fun provideTrainingDao(): TrainingDao = mockk(relaxed = true)

    @Provides
    @Singleton
    internal fun provideTrainingExerciseDao(): TrainingExerciseDao = mockk(relaxed = true)

    @Provides
    @Singleton
    internal fun provideExerciseDao(): ExerciseDao = mockk(relaxed = true)

    @Provides
    @Singleton
    internal fun provideSessionDao(): SessionDao = mockk(relaxed = true)

    @Provides
    @Singleton
    internal fun providePerformedExerciseDao(): PerformedExerciseDao = mockk(relaxed = true)

    @Provides
    @Singleton
    internal fun provideSetDao(): SetDao = mockk(relaxed = true)

    @Provides
    @Singleton
    internal fun provideTagDao(): TagDao = mockk(relaxed = true)

    @Provides
    @Singleton
    internal fun provideExerciseTagDao(): ExerciseTagDao = mockk(relaxed = true)

    @Provides
    @Singleton
    internal fun provideTrainingTagDao(): TrainingTagDao = mockk(relaxed = true)
}
