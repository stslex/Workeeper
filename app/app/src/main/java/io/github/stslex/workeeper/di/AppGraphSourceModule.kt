// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.data.database.tag.TagDao
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.data.database.training.TrainingDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import javax.inject.Singleton

/**
 * The SINGLE source of the app-scope [AppGraph] into Hilt (App-Scope Collapse — Phase D2 decouple).
 * Split out of [AppGraphAdoptBackModule] so it is the ONLY place that reaches for the
 * [BaseApplication]-held graph, and the ONLY unit that behaves differently under test. The adopt-back
 * shims in [AppGraphAdoptBackModule] consume the `AppGraph` binding this module provides and are never
 * replaced — so tests exercise the REAL delegation, not a hand-copied double.
 *
 * WHY A FALLBACK, NOT A CAST: prod `BaseApplication` implements [AppGraphOwner] and holds the process
 * graph. But the Hilt instrumented-test harness swaps in `HiltTestApplication`, which does NOT — and it
 * is `internal`-invisible to the `app:dev` / `app:store` flavor test modules, so they cannot
 * `@TestInstallIn`-replace this module nor implement [AppGraphOwner] themselves. A `context as
 * AppGraphOwner` cast therefore `ClassCastException`s in every flavor `@HiltAndroidTest` that resolves a
 * migrated binding at startup (the defect that shipped latent with the leaf). Instead:
 *  - prod (`BaseApplication is AppGraphOwner`) → return the held graph; and
 *  - test (`HiltTestApplication`, not an owner) → build the REAL [AppGraph] from `applicationContext`.
 * The test branch builds the SAME real graph the prod app holds (`create(applicationContext)`), so there
 * is zero test-double drift and no per-flavor test wiring. Works for `app:app` + `app:dev` + `app:store`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AppGraphSourceModule {

    @Provides
    @Singleton
    @Suppress("LongParameterList")
    fun provideAppGraph(
        application: Application,
        // App-Scope Collapse Step 3 (C2, bridge-scaffold). The db-cascade substrate the graph bridge-reads,
        // injected FROM HILT — so under @TestInstallIn (TestInfraModule swaps FakeImageStorage) the test-branch
        // build passes the FAKE ImageStorage into create(), never a real one. The DAOs / DbTransitionRunner stay
        // Hilt-owned (Step-5-fenced). Fake-awareness guarantee: bridge-READ, never construct. No dagger.Lazy
        // needed — C2-h' dissolved the @IO→appGraph back-edge, so eager resolution here does not cycle.
        exerciseDao: ExerciseDao,
        exerciseTagDao: ExerciseTagDao,
        performedExerciseDao: PerformedExerciseDao,
        sessionDao: SessionDao,
        setDao: SetDao,
        tagDao: TagDao,
        trainingDao: TrainingDao,
        trainingExerciseDao: TrainingExerciseDao,
        trainingTagDao: TrainingTagDao,
        dbTransitionRunner: DbTransitionRunner,
        imageStorage: ImageStorage,
    ): AppGraph = when (application) {
        // Prod: BaseApplication (and its dev/store subclasses) hold the process-lifetime graph.
        is AppGraphOwner -> application.appGraph
        // Test: HiltTestApplication is not an AppGraphOwner. Build the real graph from the app context — the
        // same construction BaseApplication.appGraph performs (via the shared [buildAppGraph]) — so the real
        // adopt-back shims resolve, and the bridged deps (incl. FakeImageStorage) come from Hilt.
        else -> buildAppGraph(
            applicationContext = application.applicationContext,
            exerciseDao = exerciseDao,
            exerciseTagDao = exerciseTagDao,
            performedExerciseDao = performedExerciseDao,
            sessionDao = sessionDao,
            setDao = setDao,
            tagDao = tagDao,
            trainingDao = trainingDao,
            trainingExerciseDao = trainingExerciseDao,
            trainingTagDao = trainingTagDao,
            dbTransitionRunner = dbTransitionRunner,
            imageStorage = imageStorage,
        )
    }
}
